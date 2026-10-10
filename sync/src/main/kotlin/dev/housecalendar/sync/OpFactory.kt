package dev.housecalendar.sync

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.InstantMs
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.OpCodec
import dev.housecalendar.contracts.OpId
import dev.housecalendar.contracts.Payload
import dev.housecalendar.contracts.Uuids
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Creates this device's ops: the next sequence numbers, Lamport values above anything the log holds
 * [SPEC R7], and time-ordered v7 ids. Calls are serialized so two concurrent records never share a
 * sequence number; the ops of one call are appended atomically.
 */
public class OpFactory(
    private val familyId: FamilyId,
    private val self: DeviceId,
    private val log: OpLog,
    private val clock: () -> InstantMs,
) {
    private val mutex = Mutex()

    public suspend fun record(payloads: List<Payload>): List<Op> = mutex.withLock {
        var seq = log.vector()[self]
        var lamport = log.maxLamport()
        val now = clock().value
        val ops = payloads.map { payload ->
            seq = seq.next()
            lamport = lamport.next()
            val (type, body) = OpCodec.encodePayload(payload)
            Op(OpId(Uuids.v7(now)), familyId, self, seq, lamport, Op.CURRENT_SCHEMA, type, body)
        }
        log.append(ops)
        ops
    }
}
