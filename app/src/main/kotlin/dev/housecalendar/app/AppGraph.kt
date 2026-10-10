package dev.housecalendar.app

import android.content.Context
import dev.housecalendar.app.calendar.CalendarActions
import dev.housecalendar.app.calendar.CalendarRepository
import dev.housecalendar.app.calendar.CalendarSnapshot
import dev.housecalendar.app.calendar.Clock
import dev.housecalendar.app.calendar.NewMember
import dev.housecalendar.app.calendar.RecordResult
import dev.housecalendar.app.model.Incoming
import dev.housecalendar.app.reminders.ReminderScheduler
import dev.housecalendar.app.sync.BackgroundSync
import dev.housecalendar.app.sync.LinkFactory
import dev.housecalendar.app.sync.NearbyPeerLink
import dev.housecalendar.app.sync.SyncController
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Payload
import dev.housecalendar.contracts.Uuids
import dev.housecalendar.data.DeviceIdentityStore
import dev.housecalendar.data.DeviceSettings
import dev.housecalendar.data.DeviceSettingsStore
import dev.housecalendar.data.FamilyKeyVault
import dev.housecalendar.data.PendingJoin
import dev.housecalendar.data.SqliteOpLog
import dev.housecalendar.data.SyncHistoryEntry
import dev.housecalendar.data.SyncHistoryStore
import dev.housecalendar.lan.LanLink
import dev.housecalendar.nearby.NearbyLink
import dev.housecalendar.sync.FamilyKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** The family key held by this device, once loaded from the vault. */
sealed interface KeyState {
    data object Loading : KeyState

    data object None : KeyState

    data class Held(val family: FamilyId, val key: FamilyKey) : KeyState
}

/**
 * The whole object graph, built once per process (SAD ADR-3: constructor injection, no framework).
 */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.SYSTEM
    val deviceId = DeviceIdentityStore(appContext).deviceId()
    val opLog = SqliteOpLog(appContext)
    private val vault = FamilyKeyVault(appContext)
    val settingsStore = DeviceSettingsStore(appContext)
    val repository = CalendarRepository(opLog, deviceId, clock, Dispatchers.Default).also { it.start(scope) }

    private val historyStore = SyncHistoryStore(appContext)
    private val _history = MutableStateFlow<List<SyncHistoryEntry>>(emptyList())
    val history: StateFlow<List<SyncHistoryEntry>> = _history.asStateFlow()

    private val _key = MutableStateFlow<KeyState>(KeyState.Loading)
    val key: StateFlow<KeyState> = _key.asStateFlow()

    val settings: StateFlow<DeviceSettings?> = settingsStore.settings().stateIn(scope, SharingStarted.Eagerly, null)

    /** "Now", to the minute: the now line, "ended", and the display's day rollover (SAD §3.8 "Clock"). */
    private val _minute = MutableStateFlow(clock.now())
    val minute: StateFlow<Instant> = _minute.asStateFlow()

    val sync = SyncController(scope, opLog, repository, historyStore, deviceId, ::role, clock) { refreshHistory() }

    /** Same Wi-Fi: the default, used automatically (SAD ADR-9). */
    val localNetwork = LinkFactory { family, name, role -> LanLink(appContext, family, deviceId, name, role) }

    /** Bluetooth and Wi-Fi Direct through Nearby Connections, for when the devices don't share a network. */
    val bluetooth = LinkFactory { family, name, role -> NearbyPeerLink(NearbyLink(appContext, family, deviceId, name, role)) }

    private val foreground = MutableStateFlow(false)

    private val _incoming = MutableStateFlow<Incoming?>(null)

    /** What another app handed over, until the phone's screens have shown it (SPEC FR-49 to FR-55). */
    val incoming: StateFlow<Incoming?> = _incoming.asStateFlow()

    fun offerIncoming(value: Incoming?) {
        if (value != null) _incoming.value = value
    }

    fun takeIncoming() {
        _incoming.value = null
    }

    init {
        scope.launch {
            _key.value = withContext(Dispatchers.IO) { vault.load() }?.let { (family, key) -> KeyState.Held(family, key) } ?: KeyState.None
            refreshHistory()
        }
        scope.launch { tickMinutes() }
        scope.launch { followRole() }
        scope.launch { finishPendingJoin() }
        scope.launch { ReminderScheduler.follow(this@AppGraph) }
    }

    /** This device's role: as the family recorded it, else as chosen while joining, else a phone. */
    fun role(): DeviceRole {
        val state = (repository.snapshot.value as? CalendarSnapshot.Ready)?.state
        return state?.devices?.get(deviceId)?.role?.value ?: settings.value?.pendingJoin?.role ?: DeviceRole.Phone
    }

    fun ownerMember(): MemberId? = (repository.snapshot.value as? CalendarSnapshot.Ready)?.state?.ownerOf(deviceId)

    fun deviceName(): String =
        repository.deviceName(deviceId).ifBlank { null } ?: settings.value?.pendingJoin?.name ?: appContext.getString(R.string.role_phone_short)

    /** Called by the activity: on a phone, automatic sync runs only while the app is on screen. */
    fun setForeground(visible: Boolean) {
        foreground.value = visible
    }

    /** Back to automatic sync after a manual sync on the Sync screen. */
    fun resumeAutomatic() {
        val held = key.value as? KeyState.Held ?: return
        if (foreground.value || role() == DeviceRole.Display) startAutomatic(held)
    }

    suspend fun record(payloads: List<Payload>): RecordResult = repository.record(payloads)

    /**
     * SPEC FR-1, FR-4: a new family id and key; the key goes to the vault before any op is written. Runs in
     * the application scope: storing the key changes the screen, which would cancel a screen's scope midway.
     */
    fun createFamily(name: String, zone: ZoneId, weekStart: DayOfWeek, members: List<NewMember>, deviceName: String, role: DeviceRole, owner: MemberId?) {
        scope.launch {
            val family = FamilyId(Uuids.random())
            val payloads = CalendarActions.createFamily(name, zone, weekStart, members, deviceId, deviceName, role, owner)
            store(family, FamilyKey.generate())
            repository.createFamily(family, payloads)
        }
    }

    /** SPEC FR-4, FR-5: a key from a pairing code, with this device's role and name kept until the first sync. */
    fun join(family: FamilyId, key: FamilyKey, pending: PendingJoin) {
        scope.launch {
            settingsStore.update { it.copy(pendingJoin = pending) }
            store(family, key)
        }
    }

    /** Saves a family key (new family, joining, or after removing a lost device, SPEC FR-6). */
    suspend fun store(family: FamilyId, key: FamilyKey) {
        withContext(Dispatchers.IO) { vault.save(family, key) }
        _key.value = KeyState.Held(family, key)
    }

    suspend fun refreshHistory() {
        _history.value = historyStore.entries()
    }

    private fun startAutomatic(held: KeyState.Held) = sync.startAutomatic(localNetwork, held.family, held.key, deviceName())

    private suspend fun tickMinutes() {
        while (true) {
            val now = clock.now()
            _minute.value = now
            delay(MILLIS_PER_MINUTE - now.toEpochMilli() % MILLIS_PER_MINUTE)
        }
    }

    /**
     * Automatic sync runs while a phone is on screen and always on a display (SPEC FR-44); background
     * work is scheduled for phones only. Restarts when the name or role changes, so others see the new one.
     */
    private suspend fun followRole() {
        val identity = repository.snapshot.map { role() to deviceName() }.distinctUntilChanged()
        combine(foreground, key, identity) { visible, held, (role, _) -> Triple(visible, held as? KeyState.Held, role) }
            .distinctUntilChanged()
            .collect { (visible, held, role) ->
                BackgroundSync.update(appContext, enabled = held != null && role != DeviceRole.Display)
                if (held != null && (visible || role == DeviceRole.Display)) startAutomatic(held) else sync.stop()
            }
    }

    /** SPEC FR-5: once the family arrives, a joining device records the name and role it chose. */
    private suspend fun finishPendingJoin() {
        combine(repository.snapshot, settings) { snapshot, local -> (snapshot as? CalendarSnapshot.Ready) to local?.pendingJoin }
            .collect { (ready, pending) ->
                if (ready == null || pending == null) return@collect
                record(listOf(CalendarActions.nameDevice(deviceId, pending.name, pending.role, owner = null)))
                settingsStore.update { it.copy(pendingJoin = null) }
            }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
