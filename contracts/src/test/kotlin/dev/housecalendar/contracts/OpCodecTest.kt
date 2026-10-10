package dev.housecalendar.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The envelope, copied from House Points v0.2.3 (SAD ADR-5). Payload round-trips live in [PayloadsTest]. */
class OpCodecTest {
    private val family = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
    private val device = DeviceId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))

    private fun opOf(type: String, body: String, seq: Long = 1, schema: Int = Op.CURRENT_SCHEMA): Op =
        Op(OpId(Uuids.v7(seq)), family, device, Seq(seq), Lamport(seq), schema, type, body)

    @Test
    fun `every op survives the envelope byte format`() {
        listOf("a.b" to "{}", "task.upsert" to """{"x":"ünïcode ✓"}""", "x" to "").forEachIndexed { index, (type, body) ->
            val op = opOf(type, body, seq = index + 1L)
            assertEquals(DecodeResult.Decoded(op), OpCodec.decode(OpCodec.encode(op)))
        }
    }

    @Test
    fun `an unknown type is kept verbatim and re-encodes to the same body`() {
        val op = opOf("task.upsert", """{"x":1,"y":[2]}""", schema = 2)
        val payload = OpCodec.decodePayload(op)
        assertEquals(UnknownPayload("task.upsert", """{"x":1,"y":[2]}"""), payload)
        assertEquals("task.upsert" to """{"x":1,"y":[2]}""", OpCodec.encodePayload(payload))
    }

    @Test
    fun `truncated or padded envelopes are malformed, never exceptions`() {
        val bytes = OpCodec.encode(opOf("a.b", "{}"))
        assertTrue(OpCodec.decode(bytes.copyOf(bytes.size - 3)) is DecodeResult.Malformed)
        assertTrue(OpCodec.decode(bytes + byteArrayOf(0)) is DecodeResult.Malformed)
        assertTrue(OpCodec.decode(byteArrayOf()) is DecodeResult.Malformed)
    }

    @Test
    fun `an op over 64 KiB is malformed`() {
        val bytes = OpCodec.encode(opOf("a.b", "x".repeat(OpCodec.MAX_OP_BYTES)))
        assertTrue(OpCodec.decode(bytes) is DecodeResult.Malformed)
    }
}
