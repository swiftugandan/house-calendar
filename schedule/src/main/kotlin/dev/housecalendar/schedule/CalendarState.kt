package dev.housecalendar.schedule

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.HolidayId
import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.Owner
import dev.housecalendar.contracts.Uuids
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.SortedMap
import java.util.concurrent.ConcurrentHashMap

/** What the log holds that this version cannot use: shown in diagnostics, never on the calendar (SPEC FR-46). */
public data class Diagnostics(val unknownByType: Map<String, Int>, val newestSchema: Int, val malformed: Int) {
    public companion object {
        public val NONE: Diagnostics = Diagnostics(emptyMap(), Op.CURRENT_SCHEMA, 0)
    }
}

/** A member as the interface lists them, in the order they were added. */
public data class MemberView(val id: MemberId, val name: String, val colorIndex: Int, val kind: MemberKind, val archived: Boolean)

/** An entry of Recently deleted (SPEC FR-17). */
public data class DeletedEvent(val event: EventId, val title: String, val by: DeviceId, val stamp: Stamp)

/**
 * The whole calendar as last-writer-wins registers, keyed so that every merge is a max (SAD ADR-12):
 * `Projection.fold(A ∪ B) == Projection.fold(A).plus(B)`. Immutable; the derived indexes below are caches
 * that take no part in equality.
 */
public data class CalendarState(
    val family: FamilyRecord,
    val members: Map<MemberId, MemberRecord>,
    val devices: Map<DeviceId, DeviceRecord>,
    val events: Map<EventId, EventRecord>,
    val changes: Map<EventId, SortedMap<LocalDate, ChangeRecord>>,
    val edits: Map<EventId, Map<LocalDate, EditRecord>>,
    val holidays: Map<HolidayId, HolidayRecord>,
    val removedDevices: Set<DeviceId>,
    val diagnostics: Diagnostics,
) {
    /** The family timezone, or UTC when there is none this device can use (SPEC FR-25a). */
    val zone: ZoneId get() = family.zone?.value ?: UTC

    val weekStart: DayOfWeek get() = family.weekStart?.value ?: DayOfWeek.MONDAY

    val familyName: String get() = family.name?.value.orEmpty()

    /** Applies newly stored ops. Callers pass only ops not applied before (SAD ADR-12). */
    public fun plus(ops: List<Op>): CalendarState = Projection.apply(this, ops)

    public fun withoutDiagnostics(): CalendarState = copy(diagnostics = Diagnostics.NONE)

    public fun ownerOf(device: DeviceId): MemberId? = (devices[device]?.owner?.value as? Owner.Member)?.member

    public fun roleOf(device: DeviceId): DeviceRole? = devices[device]?.role?.value

    public fun deviceName(device: DeviceId): String = devices[device]?.name?.value.orEmpty()

    /** Every named member, archived or not, in the order they were added. */
    public fun allMembers(): List<MemberView> = memberViews

    /** Members shown in pickers and the display's columns (SPEC FR-7). */
    public fun activeMembers(): List<MemberView> = memberViews.filterNot { it.archived }

    public fun member(id: MemberId): MemberView? = memberViews.firstOrNull { it.id == id }

    public fun recentlyDeleted(): List<DeletedEvent> = events.mapNotNull { (id, record) ->
        val deleted = record.deleted?.takeIf { it.value } ?: return@mapNotNull null
        DeletedEvent(id, record.title?.value.orEmpty(), deleted.stamp.device, deleted.stamp)
    }.sortedByDescending { it.stamp }

    /** Holidays that are in force, ordered by first day (SPEC FR-28). */
    public fun activeHolidays(): List<Holiday> = holidayList

    public fun isHoliday(day: LocalDate): Boolean = holidayIndex.covers(day)

    /** The resolvable view of an event, or null when it is deleted or incomplete (SPEC FR-25a). */
    internal fun series(id: EventId): Series? =
        when (val lookup = seriesCache.getOrPut(id) { Series.of(this, id)?.let(SeriesLookup::Found) ?: SeriesLookup.Missing }) {
            is SeriesLookup.Found -> lookup.series
            SeriesLookup.Missing -> null
        }

    internal fun liveSeries(): List<Series> = liveSeriesList

    /** Orders members as [allMembers] lists them; ids of unknown members come last, by id. */
    internal val memberOrder: Comparator<MemberId> = Comparator { a, b ->
        val first = members[a]?.created
        val second = members[b]?.created
        when {
            first != null && second != null -> first.compareTo(second).takeIf { it != 0 } ?: Uuids.compare(a.uuid, b.uuid)
            first != null -> -1
            second != null -> 1
            else -> Uuids.compare(a.uuid, b.uuid)
        }
    }

    private val memberViews: List<MemberView> by lazy {
        members.entries.filter { it.value.name != null }.sortedWith(compareBy(memberOrder) { it.key })
            .mapIndexed { index, (id, record) ->
                MemberView(
                    id, record.name?.value.orEmpty(),
                    Math.floorMod(record.colorIndex?.value ?: index, Limits.MEMBER_COLOURS),
                    record.kind?.value ?: MemberKind.Adult,
                    record.archived?.value == true,
                )
            }
    }

    private val holidayList: List<Holiday> by lazy {
        holidays.mapNotNull { (id, record) -> Holiday.of(id, record) }
            .sortedWith(compareBy<Holiday> { it.first }.thenComparator { a, b -> Uuids.compare(a.id.uuid, b.id.uuid) })
    }

    private val holidayIndex: HolidayIndex by lazy { HolidayIndex(holidayList) }

    private val seriesCache = ConcurrentHashMap<EventId, SeriesLookup>()

    private val liveSeriesList: List<Series> by lazy { events.keys.mapNotNull(::series) }

    public companion object {
        public val UTC: ZoneId = ZoneId.of("UTC")

        public val EMPTY: CalendarState = CalendarState(
            FamilyRecord(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptySet(), Diagnostics.NONE,
        )
    }
}

/** A holiday in force: named, with an inclusive range no longer than the spec allows (SPEC FR-28). */
public data class Holiday(val id: HolidayId, val name: String, val first: LocalDate, val last: LocalDate) {
    internal companion object {
        fun of(id: HolidayId, record: HolidayRecord): Holiday? {
            if (record.deleted?.value == true) return null
            val first = record.first?.value ?: return null
            val last = record.last?.value ?: return null
            if (last < first || last > first.plusDays(Limits.MAX_HOLIDAY_DAYS_AFTER_FIRST.toLong())) return null
            return Holiday(id, record.name?.value.orEmpty(), first, last)
        }
    }
}

/** Merged holiday ranges for an O(log n) "is this day a holiday?". */
private class HolidayIndex(holidays: List<Holiday>) {
    private val ranges = java.util.TreeMap<LocalDate, LocalDate>().apply {
        holidays.forEach { holiday ->
            val previous = floorEntry(holiday.first)
            if (previous != null && previous.value >= holiday.first.minusDays(1)) {
                if (holiday.last > previous.value) put(previous.key, holiday.last)
            } else {
                put(holiday.first, holiday.last)
            }
        }
    }

    fun covers(day: LocalDate): Boolean = ranges.floorEntry(day)?.let { day <= it.value } == true
}
