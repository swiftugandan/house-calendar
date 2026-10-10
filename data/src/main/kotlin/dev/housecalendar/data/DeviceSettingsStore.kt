package dev.housecalendar.data

import android.content.Context
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.InstantMs
import dev.housecalendar.contracts.LocalTimeSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime

/** Whose events this phone reminds about (SPEC FR-37). */
@Serializable
public enum class ReminderChoice { MINE, EVERYONE, NONE }

/**
 * What a joining device chose before it could record anything: it has the family key but none of the
 * family's ops until its first sync, and records its `device.upsert` then (SPEC FR-5).
 */
@Serializable
public data class PendingJoin(val name: String, val role: DeviceRole)

/** The view a display returns to when idle (SPEC FR-31). */
@Serializable
public enum class DisplayHome { WEEK, TODAY, MONTH }

/**
 * Settings that describe this device only (SAD §3.5). They are never synced and never backed up: another
 * device of the family, or a restored copy, makes its own choices.
 */
@Serializable
public data class DeviceSettings(
    val reminders: ReminderChoice = ReminderChoice.MINE,
    @Serializable(with = LocalTimeSerializer::class) val dayStarts: LocalTime = DEFAULT_DAY_STARTS,
    @Serializable(with = LocalTimeSerializer::class) val dayEnds: LocalTime = DEFAULT_DAY_ENDS,
    val displayHome: DisplayHome = DisplayHome.WEEK,
    /** The display's size check (DESIGN.md "Display type"): 100 is the measured physical size. */
    val sizePercent: Int = DEFAULT_SIZE_PERCENT,
    /** The latest reminder instant this phone has posted (SPEC FR-38). */
    val lastReminderFired: InstantMs? = null,
    /** Screen pinning is offered once (SAD §3.8). */
    val pinningOffered: Boolean = false,
    /** Set while this device has joined by pairing code but not yet received the family (SAD §3.8 "Pairing"). */
    val pendingJoin: PendingJoin? = null,
) {
    public companion object {
        public val DEFAULT_DAY_STARTS: LocalTime = LocalTime.of(6, 30)
        public val DEFAULT_DAY_ENDS: LocalTime = LocalTime.of(22, 0)
        public const val DEFAULT_SIZE_PERCENT: Int = 100
    }
}

/**
 * A small JSON file in `noBackupFilesDir`, written atomically. An unreadable file gives the defaults rather
 * than failing the app; the next update replaces it.
 */
public class DeviceSettingsStore(private val directory: File) {
    public constructor(context: Context) : this(context.noBackupFilesDir)

    private val file get() = File(directory, FILE_NAME)
    private val lock = Mutex()
    private val state = MutableStateFlow<DeviceSettings?>(null)

    public suspend fun current(): DeviceSettings = lock.withLock { loaded() }

    /** The settings now and after every update. */
    public fun settings(): Flow<DeviceSettings> = flow {
        current()
        emitAll(state.filterNotNull())
    }

    public suspend fun update(transform: (DeviceSettings) -> DeviceSettings): DeviceSettings = lock.withLock {
        val updated = transform(loaded())
        withContext(Dispatchers.IO) { AtomicFiles.write(file, json.encodeToString(DeviceSettings.serializer(), updated).toByteArray()) }
        state.value = updated
        updated
    }

    private suspend fun loaded(): DeviceSettings = state.value ?: read().also { state.value = it }

    private suspend fun read(): DeviceSettings = withContext(Dispatchers.IO) {
        val source = file
        if (!source.exists()) return@withContext DeviceSettings()
        try {
            json.decodeFromString(DeviceSettings.serializer(), source.readText())
        } catch (e: SerializationException) {
            DeviceSettings() // Local preferences only: an unreadable file starts again from the defaults.
        } catch (e: IllegalArgumentException) {
            DeviceSettings()
        }
    }

    public companion object {
        public const val FILE_NAME: String = "device-settings.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
