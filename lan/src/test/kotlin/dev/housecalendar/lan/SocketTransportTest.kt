package dev.housecalendar.lan

import dev.housecalendar.sync.FamilyKey
import dev.housecalendar.sync.SimDevice
import dev.housecalendar.sync.SyncOutcome
import dev.housecalendar.sync.TestOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class SocketTransportTest {
    private fun connectedPair(): Pair<SocketTransport, SocketTransport> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val client = Socket()
            client.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), CONNECT_TIMEOUT_MS)
            val accepted = server.accept()
            return SocketTransport(client) to SocketTransport(accepted)
        }
    }

    @Test(timeout = HANG_LIMIT_MS)
    fun `a receive that times out returns promptly and closes the connection, even though socket reads block`() = runBlocking {
        val (a, _) = connectedPair()
        val started = System.nanoTime()
        val frame = kotlinx.coroutines.withTimeoutOrNull(RECEIVE_TIMEOUT_MS) { a.receive() }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(frame)
        assertTrue("receive took $elapsedMs ms after a $RECEIVE_TIMEOUT_MS ms timeout", elapsedMs < RECEIVE_TIMEOUT_MS + PROMPT_MS)
        assertNull(a.receive())
    }

    @Test
    fun `frames of any size up to the limit arrive whole and in order`() = runBlocking {
        val (a, b) = connectedPair()
        val small = byteArrayOf(1, 2, 3)
        val large = ByteArray(300 * 1024) { (it % 251).toByte() }
        withContext(Dispatchers.IO) {
            a.send(small)
            a.send(large)
        }
        assertArrayEquals(small, b.receive())
        assertArrayEquals(large, b.receive())
        a.close()
        assertNull(b.receive())
    }

    @Test
    fun `a peer announcing an oversized frame is cut off rather than buffered`() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val raw = Socket(InetAddress.getLoopbackAddress(), server.localPort)
            val transport = SocketTransport(server.accept())
            withContext(Dispatchers.IO) {
                raw.getOutputStream().write(byteArrayOf(0x7F, 0, 0, 0))
                raw.getOutputStream().flush()
            }
            assertNull(transport.receive())
            raw.close()
        }
    }

    @Test
    fun `a full sync session converges over real sockets`() = runBlocking {
        val key = FamilyKey.generate()
        val a = SimDevice(TestOps.device(1), key)
        val b = SimDevice(TestOps.device(2), key)
        a.log.append(TestOps.ops(a.id, 1L..300L))
        b.log.append(TestOps.ops(b.id, 1L..40L))
        val (ta, tb) = connectedPair()
        val outcomes = SimDevice.sync(a, b, ta to tb)
        assertTrue(outcomes.first is SyncOutcome.Completed)
        assertTrue(outcomes.second is SyncOutcome.Completed)
        assertEquals(a.log.all().toSet(), b.log.all().toSet())
        assertEquals(340, a.log.all().size)
    }

    @Test
    fun `only addresses on the home network are accepted`() {
        assertTrue(LanAddresses.isLocal(InetAddress.getByName("192.168.1.20")))
        assertTrue(LanAddresses.isLocal(InetAddress.getByName("10.0.0.7")))
        assertTrue(LanAddresses.isLocal(InetAddress.getByName("172.20.3.4")))
        assertTrue(LanAddresses.isLocal(InetAddress.getByName("fe80::1")))
        assertTrue(LanAddresses.isLocal(InetAddress.getByName("fd12:3456::1")))
        assertFalse(LanAddresses.isLocal(InetAddress.getByName("8.8.8.8")))
        assertFalse(LanAddresses.isLocal(InetAddress.getByName("2001:4860:4860::8888")))
    }

    @Test
    fun `the advertised family tag hides the family id but matches across phones`() {
        val tag = LanAddresses.familyTag(TestOps.FAMILY)
        assertEquals(tag, LanAddresses.familyTag(TestOps.FAMILY))
        assertFalse(tag.contains(TestOps.FAMILY.toString().take(8)))
        assertEquals(LanAddresses.FAMILY_TAG_CHARS, tag.length)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2_000
        const val RECEIVE_TIMEOUT_MS = 300L
        const val PROMPT_MS = 2_000L
        const val HANG_LIMIT_MS = 10_000L
    }
}
