package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Uuids
import dev.housecalendar.contracts.Who
import java.time.LocalDate

/** An inclusive range of days. */
public data class DateWindow(val from: LocalDate, val to: LocalDate)

/** Identifies one occurrence: its event and its original date (SPEC glossary). */
public data class OccurrenceRef(val event: EventId, val occurrence: LocalDate)

/**
 * One fully resolved occurrence (SPEC FR-26). [people] has "everyone" resolved against the members who are
 * not archived; [slot] is always placeable; [taking] is never unrecognised.
 */
public data class AgendaEntry(
    val event: EventId,
    val occurrence: LocalDate,
    val day: LocalDate,
    val lastDay: LocalDate,
    val slot: Slot,
    val title: String,
    val icon: IconKey,
    val people: List<MemberId>,
    val everyone: Boolean,
    val taking: Taking,
    val place: String,
    val notes: String,
    val reminders: List<Reminder>,
    val repeating: Boolean,
    val repeatUnrecognised: Boolean,
    val first: Boolean,
    val moved: Boolean,
    val edited: Boolean,
) {
    val key: OccurrenceRef get() = OccurrenceRef(event, occurrence)

    val allDay: Boolean get() = slot is Slot.AllDay

    /** The member [taking] names, if any. */
    val taker: MemberId? get() = (taking as? Taking.Person)?.member

    public fun involves(member: MemberId): Boolean = member in people
}

/** `(state, window) → agenda`, a pure function of its inputs (SPEC FR-26, NFR-DET-1). */
public object Agenda {
    public fun resolve(state: CalendarState, window: DateWindow): List<AgendaEntry> {
        if (window.to < window.from) return emptyList()
        val lookBack = window.from.minusDays(Limits.MAX_SPAN_DAYS.toLong())
        val everyone = state.activeMembers().map { it.id }
        return state.liveSeries()
            .filter { (it.earliest <= window.to && it.latest >= lookBack) || it.edits.isNotEmpty() }
            .flatMap { series -> entriesOf(state, series, window, lookBack, everyone) }
            .sortedWith(ORDER)
    }

    /** One occurrence, wherever it was moved to, or null if it is not an occurrence or was cancelled. */
    public fun occurrence(state: CalendarState, ref: OccurrenceRef): AgendaEntry? {
        val series = state.series(ref.event) ?: return null
        return entry(state, series, ref.occurrence, state.activeMembers().map { it.id })
    }

    private fun entriesOf(state: CalendarState, series: Series, window: DateWindow, lookBack: LocalDate, everyone: List<MemberId>): List<AgendaEntry> {
        val first = maxOf(series.earliest, lookBack)
        val last = minOf(series.latest, window.to)
        val candidates = sortedSetOf<LocalDate>()
        var day = first
        while (day <= last) {
            candidates += day
            day = day.plusDays(1)
        }
        series.edits.forEach { (original, edit) -> if (edit.movedTo != null) candidates += original }
        return candidates.mapNotNull { original ->
            entry(state, series, original, everyone)?.takeIf { it.day <= window.to && it.lastDay >= window.from }
        }
    }

    private fun entry(state: CalendarState, series: Series, original: LocalDate, everyone: List<MemberId>): AgendaEntry? {
        if (!series.occurs(original)) return null
        val fields = series.fieldsAt(original)
        val edit = series.edits[original]
        if (edit?.cancelled?.value == true) return null
        val slot = edit?.slot?.value ?: fields.slot
        if (!Slots.isValid(slot)) return null
        val day = edit?.movedTo?.value ?: original
        val who = edit?.who?.value ?: fields.who
        val people = when (who) {
            is Who.Members -> who.members.sortedWith(state.memberOrder)
            Who.Everyone, is Who.Unrecognised -> everyone
        }
        return AgendaEntry(
            event = series.id,
            occurrence = original,
            day = day,
            lastDay = Slots.lastDay(slot, day),
            slot = slot,
            title = edit?.title?.value ?: fields.title,
            icon = edit?.icon?.value ?: fields.icon,
            people = people,
            everyone = who !is Who.Members,
            taking = (edit?.taking?.value ?: fields.taking).let { if (it is Taking.Unrecognised) Taking.NotNeeded else it },
            place = edit?.place?.value ?: fields.place,
            notes = edit?.notes?.value ?: fields.notes,
            reminders = fields.reminders,
            repeating = series.repeats,
            repeatUnrecognised = series.repeatUnrecognised,
            first = original == series.firstOccurrence,
            moved = day != original,
            edited = edit != null,
        )
    }

    /** SPEC FR-26: day, all-day first, start, end, title by code point, event id, original date. */
    public val ORDER: Comparator<AgendaEntry> = Comparator { a, b ->
        compareValuesBy(a, b, { it.day }, { if (it.allDay) 0 else 1 }, { startOf(it.slot) }, { endOf(it.slot) }).takeIf { it != 0 }
            ?: compareCodePoints(a.title, b.title).takeIf { it != 0 }
            ?: Uuids.compare(a.event.uuid, b.event.uuid).takeIf { it != 0 }
            ?: a.occurrence.compareTo(b.occurrence)
    }

    private fun startOf(slot: Slot): Int = (slot as? Slot.Timed)?.start?.toSecondOfDay() ?: 0

    private fun endOf(slot: Slot): Long = when (slot) {
        is Slot.Timed -> slot.endDayOffset * SECONDS_PER_DAY + slot.end.toSecondOfDay()
        is Slot.AllDay -> slot.days * SECONDS_PER_DAY
        is Slot.Unrecognised -> 0L
    }

    private fun compareCodePoints(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val x = a.codePointAt(i)
            val y = b.codePointAt(j)
            if (x != y) return x.compareTo(y)
            i += Character.charCount(x)
            j += Character.charCount(y)
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private const val SECONDS_PER_DAY: Long = 86_400
}
