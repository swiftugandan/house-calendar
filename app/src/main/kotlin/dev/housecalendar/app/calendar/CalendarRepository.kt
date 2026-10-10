package dev.housecalendar.app.calendar

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.InstantMs
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.Payload
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.sync.OpFactory
import dev.housecalendar.sync.OpLog
import dev.housecalendar.sync.VersionVector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

/** The only wall-clock reader in the app (SPEC NFR-DET-1: everything below it takes "now" as a parameter). */
fun interface Clock {
    fun now(): Instant

    companion object {
        val SYSTEM: Clock = Clock { Instant.now() }
    }
}

/** What the app knows at a moment: still loading, no family on this device yet, or the family's calendar. */
sealed interface CalendarSnapshot {
    data object Loading : CalendarSnapshot

    data object NoFamily : CalendarSnapshot

    data class Ready(val familyId: FamilyId, val state: CalendarState) : CalendarSnapshot
}

sealed interface RecordResult {
    data class Recorded(val ops: List<Op>) : RecordResult

    data object NoFamily : RecordResult
}

/**
 * The app's single owner of calendar state (SAD §3.8). It applies only the ops that arrived since the last
 * refresh, which equals refolding the whole log (SAD ADR-12), and records new ops through [OpFactory].
 */
class CalendarRepository(
    private val log: OpLog,
    val self: DeviceId,
    private val clock: Clock,
    private val compute: CoroutineDispatcher,
) {
    private val lock = Mutex()
    private var vector = VersionVector.EMPTY
    private var state = CalendarState.EMPTY
    private var familyId: FamilyId? = null
    private var factory: OpFactory? = null

    private val _snapshot = MutableStateFlow<CalendarSnapshot>(CalendarSnapshot.Loading)
    val snapshot: StateFlow<CalendarSnapshot> = _snapshot.asStateFlow()

    /** Starts following the log (local records and syncs alike); call once from the application scope. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            refreshNow()
            log.changes.collect { refreshNow() }
        }
    }

    /** Applies whatever is new in the log and returns the result. */
    suspend fun refreshNow(): CalendarSnapshot = withContext(compute) {
        lock.withLock {
            val fresh = log.opsAfter(vector)
            if (fresh.isNotEmpty()) {
                state = state.plus(fresh)
                fresh.forEach { vector = vector.withOp(it) }
                if (familyId == null) familyId = fresh.first().familyId
            }
            val family = familyId
            _snapshot.value = if (family == null) CalendarSnapshot.NoFamily else CalendarSnapshot.Ready(family, state)
            _snapshot.value
        }
    }

    /** A device's name as the family last set it, or blank. */
    fun deviceName(device: DeviceId): String =
        (snapshot.value as? CalendarSnapshot.Ready)?.state?.devices?.get(device)?.name?.value.orEmpty()

    /** Records payloads as new ops from this device, in one atomic append. */
    suspend fun record(payloads: List<Payload>): RecordResult {
        val opFactory = factoryOrNull() ?: return RecordResult.NoFamily
        val ops = opFactory.record(payloads)
        refreshNow()
        return RecordResult.Recorded(ops)
    }

    /** Starts a new family on this device (SPEC FR-1). */
    suspend fun createFamily(family: FamilyId, payloads: List<Payload>): List<Op> {
        val opFactory = lock.withLock { OpFactory(family, self, log, ::nowMs).also { factory = it } }
        val ops = opFactory.record(payloads)
        refreshNow()
        return ops
    }

    private suspend fun factoryOrNull(): OpFactory? = lock.withLock {
        factory ?: familyId?.let { family -> OpFactory(family, self, log, ::nowMs).also { factory = it } }
    }

    private fun nowMs(): InstantMs = InstantMs.of(clock.now())
}
