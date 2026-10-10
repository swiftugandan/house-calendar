package dev.housecalendar.contracts

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * What an [Op] says happened. Each concrete payload has a stable [PayloadType] string; the JSON body is
 * stored and forwarded byte-for-byte, so a device that does not understand a type still keeps it (SPEC FR-46).
 *
 * Upserts are per-field last-writer-wins registers (SPEC FR-42): a non-null field is a write of that field;
 * a null field is "not written by this op" and is omitted from the JSON. Text fields other than titles and
 * names are cleared by writing the empty string, never by writing null.
 */
public sealed interface Payload

/** Payload type identifiers. Never rename one: they are stored in every log. */
public object PayloadType {
    public const val FAMILY_CREATED: String = "family.created"
    public const val FAMILY_SETTINGS: String = "family.settings"
    public const val DEVICE_UPSERT: String = "device.upsert"
    public const val DEVICE_REMOVED: String = "device.removed"
    public const val MEMBER_UPSERT: String = "member.upsert"
    public const val EVENT_UPSERT: String = "event.upsert"
    public const val EVENT_CHANGE: String = "event.change"
    public const val EVENT_EDIT: String = "event.edit"
    public const val HOLIDAY_UPSERT: String = "holiday.upsert"
}

/** One per family. Its fields count as the first writes of the family settings (SPEC FR-1, FR-2). */
@Serializable
public data class FamilyCreated(
    val name: String,
    @Serializable(with = ZoneIdSerializer::class) val zone: ZoneId,
    @Serializable(with = DayOfWeekSerializer::class) val weekStart: DayOfWeek,
) : Payload

/** SPEC FR-2: name, timezone and week start can change at any time. */
@Serializable
public data class FamilySettings(
    val name: String? = null,
    @Serializable(with = ZoneIdSerializer::class) val zone: ZoneId? = null,
    @Serializable(with = DayOfWeekSerializer::class) val weekStart: DayOfWeek? = null,
) : Payload

/** A device's display name, role and owner (SPEC FR-5). */
@Serializable
public data class DeviceUpsert(
    val deviceId: DeviceId,
    val name: String? = null,
    val role: DeviceRole? = null,
    val owner: Owner? = null,
) : Payload

/** SPEC FR-6. The device's earlier ops stay in the log. */
@Serializable
public data class DeviceRemoved(val deviceId: DeviceId) : Payload

/** SPEC FR-7. [colorIndex] is 0-based into the member colours. */
@Serializable
public data class MemberUpsert(
    val memberId: MemberId,
    val name: String? = null,
    val colorIndex: Int? = null,
    val kind: MemberKind? = null,
    val archived: Boolean? = null,
) : Payload

/** The event itself, and edits with scope "all" (SPEC FR-8, FR-15). */
@Serializable
public data class EventUpsert(
    val eventId: EventId,
    val title: String? = null,
    val icon: IconKey? = null,
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate? = null,
    val slot: Slot? = null,
    val repeat: Repeat? = null,
    val termTimeOnly: Boolean? = null,
    val who: Who? = null,
    val taking: Taking? = null,
    val place: String? = null,
    val notes: String? = null,
    val reminders: List<Reminder>? = null,
    val deleted: Boolean? = null,
) : Payload

/**
 * "This and following" (SPEC FR-15, FR-16, FR-23): overlays the occurrences of [eventId] whose original date
 * is on or after [from]. A [repeat] written here is anchored at [from]. It has no start, deleted, moved day
 * or cancelled field; those belong to the event and to single occurrences.
 */
@Serializable
public data class EventChange(
    val eventId: EventId,
    @Serializable(with = LocalDateSerializer::class) val from: LocalDate,
    val title: String? = null,
    val icon: IconKey? = null,
    val slot: Slot? = null,
    val repeat: Repeat? = null,
    val termTimeOnly: Boolean? = null,
    val who: Who? = null,
    val taking: Taking? = null,
    val place: String? = null,
    val notes: String? = null,
    val reminders: List<Reminder>? = null,
    val ended: Boolean? = null,
) : Payload

/**
 * "Only this one" (SPEC FR-15, FR-16): overlays the single occurrence of [eventId] whose original date is
 * [occurrence]. [movedTo] places it on another day. It cannot change the repeat rule, term time, or the
 * reminders, which belong to the series.
 */
@Serializable
public data class EventEdit(
    val eventId: EventId,
    @Serializable(with = LocalDateSerializer::class) val occurrence: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val movedTo: LocalDate? = null,
    val slot: Slot? = null,
    val title: String? = null,
    val icon: IconKey? = null,
    val who: Who? = null,
    val taking: Taking? = null,
    val place: String? = null,
    val notes: String? = null,
    val cancelled: Boolean? = null,
) : Payload

/** A named range of days, inclusive (SPEC FR-28). */
@Serializable
public data class HolidayUpsert(
    val holidayId: HolidayId,
    val name: String? = null,
    @Serializable(with = LocalDateSerializer::class) val first: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val last: LocalDate? = null,
    val deleted: Boolean? = null,
) : Payload

/** A payload of a type (or schema version) this version does not know. Kept and forwarded unchanged. */
public data class UnknownPayload(val type: String, val json: String) : Payload
