package dev.housecalendar.app.sync

import dev.housecalendar.app.calendar.CalendarRepository
import dev.housecalendar.app.calendar.Clock
import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.InstantMs
import dev.housecalendar.data.SyncHistoryEntry
import dev.housecalendar.data.SyncHistoryStore
import dev.housecalendar.nearby.NearbyException
import dev.housecalendar.sync.Dialing
import dev.housecalendar.sync.FamilyKey
import dev.housecalendar.sync.LinkPeer
import dev.housecalendar.sync.OpLog
import dev.housecalendar.sync.PeerLink
import dev.housecalendar.sync.SyncOutcome
import dev.housecalendar.sync.SyncSession
import dev.housecalendar.sync.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Why a manual sync could not finish; the words are in string resources. */
enum class SyncFailure { LINK_LOST, LINK_UNAVAILABLE }

sealed interface SyncState {
    data object Idle : SyncState

    data class Searching(val peers: List<LinkPeer>) : SyncState

    data class Syncing(val peerName: String) : SyncState

    data class Finished(val peerName: String, val outcome: SyncOutcome, val received: Int) : SyncState

    data class Failed(val failure: SyncFailure) : SyncState
}

/** How a link is opened for a family; the app supplies the local-network and Bluetooth ones. */
fun interface LinkFactory {
    fun open(family: FamilyId, deviceName: String, role: DeviceRole): PeerLink
}

/**
 * Runs sync sessions over a [PeerLink] (SAD §4.3, ADR-9). Who dials is decided by [Dialing]: a phone dials
 * the display, a display never dials a phone, and between equals the lower id dials (SPEC FR-44).
 *
 * - [startAutomatic]: the foreground on phones, and always on a display. Syncs when a device appears, 3 s
 *   after anything is recorded, and every 60 s while one is visible.
 * - [startManual]: the Sync screen; one sync with the first device found, over whichever link is given.
 * - [runOnce]: the background worker's single pass.
 */
class SyncController(
    private val scope: CoroutineScope,
    private val log: OpLog,
    private val repository: CalendarRepository,
    private val history: SyncHistoryStore,
    private val self: DeviceId,
    private val role: () -> DeviceRole,
    private val clock: Clock,
    private val onHistoryChanged: suspend () -> Unit,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val sessionLock = Mutex()
    private var link: PeerLink? = null
    private var jobs: List<Job> = emptyList()

    fun startManual(factory: LinkFactory, family: FamilyId, key: FamilyKey, deviceName: String) {
        stop()
        val opened = factory.open(family, deviceName, role())
        link = opened
        _state.value = SyncState.Searching(emptyList())
        jobs = listOf(
            scope.launch {
                try {
                    opened.peers().collect { peers ->
                        if (_state.value !is SyncState.Searching) return@collect
                        _state.value = SyncState.Searching(peers)
                        val peer = peers.firstOrNull(::dials) ?: return@collect
                        dial(opened, peer, family, key, manual = true)
                    }
                } catch (e: IOException) {
                    _state.value = SyncState.Failed(failureOf(e))
                }
            },
            scope.launch { acceptLoop(opened, family, key, manual = true) },
        )
    }

    @OptIn(FlowPreview::class)
    fun startAutomatic(factory: LinkFactory, family: FamilyId, key: FamilyKey, deviceName: String) {
        stop()
        val opened = factory.open(family, deviceName, role())
        link = opened
        val visible = MutableStateFlow<List<LinkPeer>>(emptyList())
        val nudges = Channel<Unit>(Channel.CONFLATED)
        jobs = listOf(
            scope.launch {
                try {
                    opened.peers().collect { peers ->
                        if (peers.map { it.device } != visible.value.map { it.device }) nudges.trySend(Unit)
                        visible.value = peers
                    }
                } catch (e: IOException) {
                    // The local network is unavailable; the Sync screen still offers Bluetooth.
                }
            },
            scope.launch { log.changes.debounce(AFTER_RECORD_MS).collect { nudges.trySend(Unit) } },
            scope.launch {
                while (true) {
                    withTimeoutOrNull(POLL_MS) { nudges.receive() }
                    visible.value.filter(::dials).forEach { peer -> dial(opened, peer, family, key, manual = false) }
                }
            },
            scope.launch { acceptLoop(opened, family, key, manual = false) },
        )
    }

    /**
     * The background worker's pass (SPEC FR-44): look for up to [discoverMs], or until the display is found,
     * then sync once with every device this one should dial, accepting anything that dials in meanwhile, and
     * stop the link. Discovery is a separate phase, so its time limit never cuts a session short.
     */
    suspend fun runOnce(factory: LinkFactory, family: FamilyId, key: FamilyKey, deviceName: String, discoverMs: Long): List<SyncOutcome> {
        val opened = factory.open(family, deviceName, role())
        val outcomes = mutableListOf<SyncOutcome>()
        val accepting = scope.launch {
            while (true) {
                val transport = opened.awaitIncoming() ?: break
                outcomes += run(transport, family, key, manual = false)
            }
        }
        try {
            val found = linkedMapOf<DeviceId, LinkPeer>()
            withTimeoutOrNull(discoverMs) {
                opened.peers().first { peers ->
                    peers.filter(::dials).forEach { found[it.device] = it }
                    found.values.any { it.role == DeviceRole.Display }
                }
            }
            found.values.forEach { peer ->
                try {
                    outcomes += run(opened.connect(peer), family, key, manual = false)
                } catch (e: IOException) {
                    // Not reachable this time; the next period tries again (SPEC FR-44).
                }
            }
        } catch (e: IOException) {
            // No local network for discovery; nothing to do until the next period.
        } finally {
            opened.stop()
            accepting.join()
        }
        return outcomes
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        link?.stop()
        link = null
        if (_state.value !is SyncState.Finished) _state.value = SyncState.Idle
    }

    fun reset() {
        _state.value = SyncState.Idle
    }

    private fun dials(peer: LinkPeer): Boolean = Dialing.shouldDial(self, role(), peer.device, peer.role)

    private suspend fun dial(opened: PeerLink, peer: LinkPeer, family: FamilyId, key: FamilyKey, manual: Boolean) {
        try {
            if (manual) _state.value = SyncState.Syncing(peer.name)
            run(opened.connect(peer), family, key, manual, peer.name)
        } catch (e: IOException) {
            if (manual) _state.value = SyncState.Failed(failureOf(e))
        }
    }

    private suspend fun acceptLoop(opened: PeerLink, family: FamilyId, key: FamilyKey, manual: Boolean) {
        while (true) {
            val transport = opened.awaitIncoming() ?: return
            scope.launch { run(transport, family, key, manual) }
        }
    }

    private suspend fun run(transport: Transport, family: FamilyId, key: FamilyKey, manual: Boolean, peerName: String = ""): SyncOutcome = sessionLock.withLock {
        val before = log.vector()
        val started = InstantMs.of(clock.now())
        val outcome = SyncSession(family, key, self, log, transport, clock = { InstantMs.of(clock.now()) }).run()
        val peer = (outcome as? SyncOutcome.Completed)?.peer
        history.add(SyncHistoryEntry.of(outcome, peer, started, InstantMs.of(clock.now())))
        onHistoryChanged()
        repository.refreshNow()
        if (manual) {
            val received = log.opsAfter(before).size
            val name = peer?.let { repository.deviceName(it) }?.ifBlank { null } ?: peerName
            _state.value = SyncState.Finished(name, outcome, received)
            stop()
        } else {
            // Give the other device a moment to finish its side before the next automatic pass.
            delay(SETTLE_MS)
        }
        outcome
    }

    private fun failureOf(e: IOException): SyncFailure = if (e is NearbyException) SyncFailure.LINK_UNAVAILABLE else SyncFailure.LINK_LOST

    private companion object {
        const val AFTER_RECORD_MS = 3_000L
        const val POLL_MS = 60_000L
        const val SETTLE_MS = 1_000L
    }
}
