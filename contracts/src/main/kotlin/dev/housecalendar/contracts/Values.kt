package dev.housecalendar.contracts

import kotlinx.serialization.Serializable

/** Milliseconds since the Unix epoch, UTC. */
@JvmInline @Serializable
public value class InstantMs(public val value: Long) : Comparable<InstantMs> {
    override fun compareTo(other: InstantMs): Int = value.compareTo(other.value)
    public fun toInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(value)

    public companion object {
        public fun of(instant: java.time.Instant): InstantMs = InstantMs(instant.toEpochMilli())
    }
}

/** Lamport clock value [SPEC R7]. */
@JvmInline @Serializable
public value class Lamport(public val value: Long) : Comparable<Lamport> {
    override fun compareTo(other: Lamport): Int = value.compareTo(other.value)
    public fun next(): Lamport = Lamport(Math.addExact(value, 1L))

    public companion object {
        public val ZERO: Lamport = Lamport(0)
    }
}

/** Per-device contiguous sequence number, starting at 1. */
@JvmInline @Serializable
public value class Seq(public val value: Long) : Comparable<Seq> {
    override fun compareTo(other: Seq): Int = value.compareTo(other.value)
    public fun next(): Seq = Seq(Math.addExact(value, 1L))

    public companion object {
        public val NONE: Seq = Seq(0)
    }
}
