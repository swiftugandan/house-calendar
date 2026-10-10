package dev.housecalendar.contracts

import kotlinx.serialization.Serializable
import java.util.UUID

/** Every identifier is a UUID with its own type, so an id can never be passed where another kind is expected. */
public sealed interface Id : Comparable<Id> {
    public val uuid: UUID
    override fun compareTo(other: Id): Int = Uuids.compare(uuid, other.uuid)
}

@JvmInline @Serializable
public value class FamilyId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class DeviceId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class OpId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}
