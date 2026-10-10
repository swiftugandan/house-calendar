package dev.housecalendar.app

import dev.housecalendar.app.calendar.CalendarRepository
import dev.housecalendar.app.calendar.Clock
import dev.housecalendar.app.sync.LinkFactory
import dev.housecalendar.app.sync.SyncController
import dev.housecalendar.app.sync.SyncState
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.data.SyncHistoryStore
import dev.housecalendar.sync.FamilyKey
import dev.housecalendar.sync.InMemoryOpLog
import dev.housecalendar.sync.LinkPeer
import dev.housecalendar.sync.LoopbackTransport
import dev.housecalendar.sync.PeerLink
import dev.housecalendar.sync.SimDevice
import dev.housecalendar.sync.SyncOutcome
import dev.housecalendar.sync.TestOps
import dev.housecalendar.sync.Transport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.Collections
import java.util.UUID

/** SPEC FR-44: who dials whom, the manual sync, and the background worker's one pass. */
class SyncControllerTest {
    @get:Rule val temp = TemporaryFolder()

    private val key = FamilyKey.generate()
    private val clock = Clock { Instant.parse("2026-10-10T12:00:00Z") }
    private val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> uncaught += e })

    private fun controller(self: dev.housecalendar.contracts.DeviceId, role: DeviceRole, log: InMemoryOpLog = InMemoryOpLog()) = SyncController(
        scope, log, CalendarRepository(log, self, clock, Dispatchers.Default), SyncHistoryStore(temp.root), self, { role }, clock,
    ) {}

    @Test
    fun `finishing a manual sync stops the link without crashing the app`() = runBlocking {
        val self = TestOps.device(2)
        val other = SimDevice(TestOps.device(1), key)
        val link = ScriptedLink(LinkPeer("other", other.id, "Other phone", DeviceRole.Phone))
        val controller = controller(self, DeviceRole.Phone)

        controller.startManual(LinkFactory { _, _, _ -> link }, TestOps.FAMILY, key, "This phone")
        val (mine, theirs) = LoopbackTransport.pair()
        val peerSide = async(Dispatchers.Default) { other.session(theirs).run() }
        link.incoming.send(mine)

        val finished = withTimeout(TIMEOUT_MS) { controller.state.first { it is SyncState.Finished } } as SyncState.Finished
        assertTrue(finished.outcome is SyncOutcome.Completed)
        assertTrue(peerSide.await() is SyncOutcome.Completed)
        withTimeout(TIMEOUT_MS) { scope.coroutineContext.job.children.forEach { it.join() } }
        assertEquals(emptyList<Throwable>(), uncaught.toList())
    }

    @Test
    fun `a phone dials the kitchen display even when the display's id sorts lower`() = runBlocking {
        val display = SimDevice(TestOps.device(1), key)
        display.factory.record(listOf(EventUpsert(EventId(UUID.randomUUID()), title = "Swimming")))
        val log = InMemoryOpLog()
        val link = ScriptedLink(LinkPeer("kitchen", display.id, "Kitchen", DeviceRole.Display), answer = display)
        val controller = controller(TestOps.device(2), DeviceRole.Phone, log)

        controller.startAutomatic(LinkFactory { _, _, _ -> link }, TestOps.FAMILY, key, "Sam's phone")
        withTimeout(TIMEOUT_MS) { log.changes.first() }
        assertEquals(display.log.all().toSet(), log.all().toSet())
        controller.stop()
        assertEquals(1, link.dialled)
    }

    @Test
    fun `a display never dials a phone, it waits for the phone`() = runBlocking {
        val phone = SimDevice(TestOps.device(2), key)
        val link = ScriptedLink(LinkPeer("phone", phone.id, "Sam's phone", DeviceRole.Phone), answer = phone)
        val controller = controller(TestOps.device(1), DeviceRole.Display)

        controller.startAutomatic(LinkFactory { _, _, _ -> link }, TestOps.FAMILY, key, "Kitchen")
        val (mine, theirs) = LoopbackTransport.pair()
        val phoneSide = async(Dispatchers.Default) { phone.session(theirs).run() }
        link.incoming.send(mine)
        assertTrue(withTimeout(TIMEOUT_MS) { phoneSide.await() } is SyncOutcome.Completed)
        controller.stop()
        assertEquals(0, link.dialled)
    }

    @Test
    fun `the background pass syncs once with every device it should dial, then stops the link`() = runBlocking {
        val display = SimDevice(TestOps.device(1), key)
        display.factory.record(listOf(EventUpsert(EventId(UUID.randomUUID()), title = "Swimming")))
        val log = InMemoryOpLog()
        val link = ScriptedLink(LinkPeer("kitchen", display.id, "Kitchen", DeviceRole.Display), answer = display)
        val controller = controller(TestOps.device(2), DeviceRole.Phone, log)

        val outcomes = withTimeout(TIMEOUT_MS) { controller.runOnce(LinkFactory { _, _, _ -> link }, TestOps.FAMILY, key, "Sam's phone", discoverMs = 500) }
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single() is SyncOutcome.Completed)
        assertEquals(display.log.all().toSet(), log.all().toSet())
        assertTrue(link.stopped)
    }

    /**
     * A link with one visible peer. Dialling it runs [answer]'s side of a session over a loopback pair;
     * stopping closes the incoming queue, as LanLink does (PeerLink: null after stop).
     */
    private inner class ScriptedLink(private val peer: LinkPeer, private val answer: SimDevice? = null) : PeerLink {
        val incoming = Channel<Transport>(Channel.UNLIMITED)
        var dialled = 0
        var stopped = false
        private val sessions = mutableListOf<Deferred<SyncOutcome>>()

        override fun peers(): Flow<List<LinkPeer>> = flow {
            emit(listOf(peer))
            awaitCancellation()
        }

        override suspend fun connect(peer: LinkPeer): Transport {
            val other = answer ?: throw java.io.IOException("nobody answers")
            dialled += 1
            val (mine, theirs) = LoopbackTransport.pair()
            sessions += scope.async { other.session(theirs).run() }
            return mine
        }

        override suspend fun awaitIncoming(): Transport? = incoming.receiveCatching().getOrNull()

        override fun stop() {
            stopped = true
            incoming.close()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
