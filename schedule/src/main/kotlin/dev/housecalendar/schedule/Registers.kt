package dev.housecalendar.schedule

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.Lamport
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.Seq
import dev.housecalendar.contracts.Uuids

/**
 * Where and when a write happened: the op's `(lamport, device)` order (SPEC FR-40), with the device's own
 * sequence number as a final tie-break so even a hostile peer that reuses a Lamport value cannot make the
 * merge depend on arrival order.
 */
public data class Stamp(val lamport: Lamport, val device: DeviceId, val seq: Seq) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int {
        val byLamport = lamport.compareTo(other.lamport)
        if (byLamport != 0) return byLamport
        val byDevice = Uuids.compare(device.uuid, other.device.uuid)
        return if (byDevice != 0) byDevice else seq.compareTo(other.seq)
    }

    public companion object {
        public fun of(op: Op): Stamp = Stamp(op.lamport, op.originDevice, op.originSeq)
    }
}

/** One last-writer-wins register (SPEC FR-42): the value of the greatest [Stamp] written. */
public data class Reg<out T : Any>(val value: T, val stamp: Stamp)

/**
 * Merges a write into a register. A null [value] means "not written by this op". Because the result is the
 * write with the greatest stamp, merging is commutative, associative and idempotent (SAD ADR-12).
 */
internal fun <T : Any> Reg<T>?.write(value: T?, stamp: Stamp): Reg<T>? = when {
    value == null -> this
    this == null || stamp > this.stamp -> Reg(value, stamp)
    else -> this
}

internal fun minOf(a: Stamp, b: Stamp): Stamp = if (a <= b) a else b

internal fun maxOf(a: Stamp, b: Stamp): Stamp = if (a >= b) a else b
