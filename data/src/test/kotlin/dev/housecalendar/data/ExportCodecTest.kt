package dev.housecalendar.data

import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.Lamport
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.OpCodec
import dev.housecalendar.contracts.OpId
import dev.housecalendar.contracts.Seq
import dev.housecalendar.contracts.Uuids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ExportCodecTest {
    private val family = FamilyId(UUID.randomUUID())
    private val device = DeviceId(UUID.randomUUID())
    private val ops: List<Op> = (1L..40L).map { seq ->
        val (type, body) = OpCodec.encodePayload(MemberUpsert(MemberId(UUID.randomUUID()), name = "child $seq"))
        Op(OpId(Uuids.v7(seq)), family, device, Seq(seq), Lamport(seq), Op.CURRENT_SCHEMA, type, body)
    }
    private val fast = 1_000

    @Test
    fun `an export decrypts back to the same ops with the right passphrase`() {
        val bytes = ExportCodec.encrypt(ops, "correct horse".toCharArray(), iterations = fast)
        assertEquals(ExportResult.Ok(ops), ExportCodec.decrypt(bytes, "correct horse".toCharArray()))
    }

    @Test
    fun `the default work factor round-trips too`() {
        val bytes = ExportCodec.encrypt(ops.take(2), "pw".toCharArray())
        assertEquals(ExportResult.Ok(ops.take(2)), ExportCodec.decrypt(bytes, "pw".toCharArray()))
    }

    @Test
    fun `a wrong passphrase is reported as such`() {
        val bytes = ExportCodec.encrypt(ops, "right".toCharArray(), iterations = fast)
        assertEquals(ExportResult.WrongPassphrase, ExportCodec.decrypt(bytes, "wrong".toCharArray()))
    }

    @Test
    fun `an altered file cannot be read`() {
        val bytes = ExportCodec.encrypt(ops, "pw".toCharArray(), iterations = fast)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertEquals(ExportResult.WrongPassphrase, ExportCodec.decrypt(bytes, "pw".toCharArray()))
    }

    @Test
    fun `something that is not an export is corrupt, never an exception`() {
        assertTrue(ExportCodec.decrypt(byteArrayOf(1, 2, 3), "pw".toCharArray()) is ExportResult.Corrupt)
        assertTrue(ExportCodec.decrypt("HCX1".toByteArray(), "pw".toCharArray()) is ExportResult.Corrupt)
    }

    @Test
    fun `two exports of the same ops differ because salt and iv are random`() {
        val first = ExportCodec.encrypt(ops, "pw".toCharArray(), iterations = fast)
        val second = ExportCodec.encrypt(ops, "pw".toCharArray(), iterations = fast)
        assertTrue(!first.contentEquals(second))
    }
}
