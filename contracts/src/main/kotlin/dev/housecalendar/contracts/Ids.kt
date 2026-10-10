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

@JvmInline @Serializable
public value class MemberId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class EventId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class HolidayId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

/**
 * How the calendar's ids are made. Imported items get a deterministic id from their iCalendar UID (SPEC
 * FR-54), so the same file imported twice, or on two devices, gives one record. Everything else gets a
 * time-ordered random id.
 */
public object CalendarIds {
    /** Fixed namespace for every House Calendar v5 id; never change it. Distinct from House Points' (SPEC NG-5). */
    public val NAMESPACE: UUID = UUID.fromString("0d1ccd49-f501-4b47-9bb0-3e4dfc92f9bc")

    public fun externalEvent(uid: String): EventId = EventId(Uuids.v5(NAMESPACE, "ext:$uid"))

    public fun externalHoliday(uid: String): HolidayId = HolidayId(Uuids.v5(NAMESPACE, "holiday:$uid"))

    public fun randomEvent(nowMillis: Long): EventId = EventId(Uuids.v7(nowMillis))

    public fun randomHoliday(nowMillis: Long): HolidayId = HolidayId(Uuids.v7(nowMillis))

    public fun randomMember(nowMillis: Long): MemberId = MemberId(Uuids.v7(nowMillis))
}
