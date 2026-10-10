package dev.housecalendar.sync

import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.InstantMs
import dev.housecalendar.contracts.Lamport
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.OpCodec
import dev.housecalendar.contracts.Seq
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OpFactoryTest {
    private val self = TestOps.device(1)
    private val child = MemberId(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"))

    @Test
    fun `ops get contiguous sequence numbers and increasing lamports, and are stored`() = runTest {
        val log = InMemoryOpLog()
        val factory = OpFactory(TestOps.FAMILY, self, log, clock = { InstantMs(1_000) })
        val first = factory.record(listOf(MemberUpsert(child, name = "Ada"), MemberUpsert(child, name = "Bea")))
        val second = factory.record(listOf(MemberUpsert(child, name = "Tom")))
        val all = first + second
        assertEquals(listOf(Seq(1), Seq(2), Seq(3)), all.map { it.originSeq })
        assertEquals(listOf(Lamport(1), Lamport(2), Lamport(3)), all.map { it.lamport })
        assertTrue(all.all { it.familyId == TestOps.FAMILY && it.originDevice == self && it.schemaVersion == Op.CURRENT_SCHEMA })
        assertEquals(MemberUpsert(child, name = "Tom"), OpCodec.decodePayload(second.single()))
        assertEquals(all.toSet(), log.all().toSet())
    }

    @Test
    fun `a new op's lamport is above every op seen from other devices`() = runTest {
        val log = InMemoryOpLog()
        log.append(listOf(TestOps.op(TestOps.device(2), seq = 1, lamport = 41)))
        val factory = OpFactory(TestOps.FAMILY, self, log, clock = { InstantMs(1_000) })
        assertEquals(Lamport(42), factory.record(listOf(MemberUpsert(child, name = "Ada"))).single().lamport)
    }

    @Test
    fun `concurrent records never reuse a sequence number`() = runTest {
        val log = InMemoryOpLog()
        val factory = OpFactory(TestOps.FAMILY, self, log, clock = { InstantMs(1_000) })
        (1..20).map { n -> async { factory.record(listOf(MemberUpsert(child, name = "n$n"))) } }.awaitAll()
        assertEquals(Seq(20), log.vector()[self])
        assertEquals(20, log.all().map { it.originSeq }.toSet().size)
    }
}
