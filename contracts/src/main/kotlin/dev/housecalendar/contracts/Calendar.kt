package dev.housecalendar.contracts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The limits the spec puts on recorded values. Recording checks them (SPEC FR-19) and intake truncates to
 * them (FR-56); reading never rejects a value for breaking one, because merging never rejects (FR-43).
 */
public object Limits {
    public const val TITLE_CHARS: Int = 80
    public const val PLACE_CHARS: Int = 120
    public const val NOTES_CHARS: Int = 2_000
    public const val NAME_CHARS: Int = 40
    public const val MAX_REMINDERS: Int = 3
    public const val MAX_MINUTES_BEFORE: Int = 10_080
    public const val MAX_DAYS_BEFORE: Int = 7
    public const val MAX_SPAN_DAYS: Int = 31
    public const val MAX_HOLIDAY_DAYS_AFTER_FIRST: Int = 366
    public const val MAX_MEMBERS: Int = 12
    public const val MEMBER_COLOURS: Int = 8
}

/** Key into the app's built-in icon set; an open set so newer versions can add icons. Empty means "no icon". */
@JvmInline @Serializable
public value class IconKey(public val key: String) {
    public val isNone: Boolean get() = key.isEmpty()

    public companion object {
        public val NONE: IconKey = IconKey("")
    }
}

/** What an occurrence takes up on its day (SPEC FR-9). */
@Serializable(with = SlotSerializer::class)
public sealed interface Slot {
    /** Covers its day and the following `days − 1` days. */
    @Serializable
    public data class AllDay(val days: Int) : Slot

    /** From [start] on its day to [end] on day `+ endDayOffset`. */
    @Serializable
    public data class Timed(
        @Serializable(with = LocalTimeSerializer::class) val start: LocalTime,
        @Serializable(with = LocalTimeSerializer::class) val end: LocalTime,
        val endDayOffset: Int,
    ) : Slot

    /** Written by a newer version; this version cannot place it. */
    public data class Unrecognised(val json: JsonObject) : Slot
}

/** A subset of the RFC 5545 RRULE (SPEC FR-20). [interval] ≥ 1; [until] is an inclusive original date. */
@Serializable(with = RepeatSerializer::class)
public sealed interface Repeat {
    @Serializable
    public data object Never : Repeat

    @Serializable
    public data class Daily(
        val interval: Int,
        @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
    ) : Repeat

    @Serializable
    public data class Weekly(
        val interval: Int,
        val days: Set<@Serializable(with = DayOfWeekSerializer::class) DayOfWeek>,
        @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
    ) : Repeat

    @Serializable
    public data class MonthlyOnDay(
        val interval: Int,
        val dayOfMonth: Int,
        @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
    ) : Repeat

    /** The [ordinal]th [weekday] of the month: 1 to 4, or −1 for the last. */
    @Serializable
    public data class MonthlyOnWeekday(
        val interval: Int,
        val ordinal: Int,
        @Serializable(with = DayOfWeekSerializer::class) val weekday: DayOfWeek,
        @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
    ) : Repeat

    @Serializable
    public data class Yearly(
        val interval: Int,
        @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
    ) : Repeat

    /** Written by a newer version; this version shows only the first occurrence (SPEC FR-46). */
    public data class Unrecognised(val json: JsonObject) : Repeat
}

/** Who an event involves (SPEC FR-10). [Everyone] is resolved when the agenda is drawn. */
@Serializable(with = WhoSerializer::class)
public sealed interface Who {
    @Serializable
    public data object Everyone : Who

    @Serializable
    public data class Members(val members: Set<MemberId>) : Who

    public data class Unrecognised(val json: JsonObject) : Who
}

/** Who takes, collects or accompanies the people involved (SPEC FR-11). */
@Serializable(with = TakingSerializer::class)
public sealed interface Taking {
    @Serializable
    public data object NotNeeded : Taking

    @Serializable
    public data object NeedsSomeone : Taking

    @Serializable
    public data class Person(val member: MemberId) : Taking

    public data class Unrecognised(val json: JsonObject) : Taking
}

/** One reminder of an event (SPEC FR-36). */
@Serializable(with = ReminderSerializer::class)
public sealed interface Reminder {
    /** Before the start of a timed occurrence. */
    @Serializable
    public data class MinutesBefore(val minutes: Int) : Reminder

    /** [days] before the occurrence's first day, at a family time. */
    @Serializable
    public data class DaysBeforeAt(
        val days: Int,
        @Serializable(with = LocalTimeSerializer::class) val at: LocalTime,
    ) : Reminder

    public data class Unrecognised(val json: JsonObject) : Reminder
}

/** Whose device this is (SPEC FR-5). A display has no owner. */
@Serializable(with = OwnerSerializer::class)
public sealed interface Owner {
    @Serializable
    public data object NoOne : Owner

    @Serializable
    public data class Member(val member: MemberId) : Owner

    public data class Unrecognised(val json: JsonObject) : Owner
}

/** A device's role (SPEC FR-5). Written as `PHONE` or `DISPLAY`. */
@Serializable(with = DeviceRoleSerializer::class)
public sealed interface DeviceRole {
    public data object Phone : DeviceRole

    public data object Display : DeviceRole

    /** A role a newer version added (for example a child's phone); treated as a phone that never dials. */
    public data class Unrecognised(val raw: String) : DeviceRole
}

/** Written as `ADULT` or `CHILD`. Adults are offered first for taking (SPEC FR-11). */
@Serializable(with = MemberKindSerializer::class)
public sealed interface MemberKind {
    public data object Adult : MemberKind

    public data object Child : MemberKind

    public data class Unrecognised(val raw: String) : MemberKind
}

public object SlotSerializer : TaggedUnionSerializer<Slot>("dev.housecalendar.Slot") {
    override val variants: List<Variant<out Slot>> = listOf(
        Variant("allDay", Slot.AllDay::class, Slot.AllDay.serializer()),
        Variant("timed", Slot.Timed::class, Slot.Timed.serializer()),
    )
    override fun unrecognised(json: JsonObject): Slot = Slot.Unrecognised(json)
    override fun rawOf(value: Slot): JsonObject? = (value as? Slot.Unrecognised)?.json
}

public object RepeatSerializer : TaggedUnionSerializer<Repeat>("dev.housecalendar.Repeat") {
    override val variants: List<Variant<out Repeat>> = listOf(
        Variant("never", Repeat.Never::class, Repeat.Never.serializer()),
        Variant("daily", Repeat.Daily::class, Repeat.Daily.serializer()),
        Variant("weekly", Repeat.Weekly::class, Repeat.Weekly.serializer()),
        Variant("monthlyOnDay", Repeat.MonthlyOnDay::class, Repeat.MonthlyOnDay.serializer()),
        Variant("monthlyOnWeekday", Repeat.MonthlyOnWeekday::class, Repeat.MonthlyOnWeekday.serializer()),
        Variant("yearly", Repeat.Yearly::class, Repeat.Yearly.serializer()),
    )
    override fun unrecognised(json: JsonObject): Repeat = Repeat.Unrecognised(json)
    override fun rawOf(value: Repeat): JsonObject? = (value as? Repeat.Unrecognised)?.json
}

public object WhoSerializer : TaggedUnionSerializer<Who>("dev.housecalendar.Who") {
    override val variants: List<Variant<out Who>> = listOf(
        Variant("everyone", Who.Everyone::class, Who.Everyone.serializer()),
        Variant("members", Who.Members::class, Who.Members.serializer()),
    )
    override fun unrecognised(json: JsonObject): Who = Who.Unrecognised(json)
    override fun rawOf(value: Who): JsonObject? = (value as? Who.Unrecognised)?.json
}

public object TakingSerializer : TaggedUnionSerializer<Taking>("dev.housecalendar.Taking") {
    override val variants: List<Variant<out Taking>> = listOf(
        Variant("notNeeded", Taking.NotNeeded::class, Taking.NotNeeded.serializer()),
        Variant("needsSomeone", Taking.NeedsSomeone::class, Taking.NeedsSomeone.serializer()),
        Variant("person", Taking.Person::class, Taking.Person.serializer()),
    )
    override fun unrecognised(json: JsonObject): Taking = Taking.Unrecognised(json)
    override fun rawOf(value: Taking): JsonObject? = (value as? Taking.Unrecognised)?.json
}

public object ReminderSerializer : TaggedUnionSerializer<Reminder>("dev.housecalendar.Reminder") {
    override val variants: List<Variant<out Reminder>> = listOf(
        Variant("minutesBefore", Reminder.MinutesBefore::class, Reminder.MinutesBefore.serializer()),
        Variant("daysBeforeAt", Reminder.DaysBeforeAt::class, Reminder.DaysBeforeAt.serializer()),
    )
    override fun unrecognised(json: JsonObject): Reminder = Reminder.Unrecognised(json)
    override fun rawOf(value: Reminder): JsonObject? = (value as? Reminder.Unrecognised)?.json
}

public object OwnerSerializer : TaggedUnionSerializer<Owner>("dev.housecalendar.Owner") {
    override val variants: List<Variant<out Owner>> = listOf(
        Variant("none", Owner.NoOne::class, Owner.NoOne.serializer()),
        Variant("member", Owner.Member::class, Owner.Member.serializer()),
    )
    override fun unrecognised(json: JsonObject): Owner = Owner.Unrecognised(json)
    override fun rawOf(value: Owner): JsonObject? = (value as? Owner.Unrecognised)?.json
}

public object DeviceRoleSerializer : OpenSetSerializer<DeviceRole>("dev.housecalendar.DeviceRole") {
    override val known: Map<String, DeviceRole> = mapOf("PHONE" to DeviceRole.Phone, "DISPLAY" to DeviceRole.Display)
    override fun unrecognised(raw: String): DeviceRole = DeviceRole.Unrecognised(raw)
    override fun rawOf(value: DeviceRole): String? = (value as? DeviceRole.Unrecognised)?.raw
}

public object MemberKindSerializer : OpenSetSerializer<MemberKind>("dev.housecalendar.MemberKind") {
    override val known: Map<String, MemberKind> = mapOf("ADULT" to MemberKind.Adult, "CHILD" to MemberKind.Child)
    override fun unrecognised(raw: String): MemberKind = MemberKind.Unrecognised(raw)
    override fun rawOf(value: MemberKind): String? = (value as? MemberKind.Unrecognised)?.raw
}
