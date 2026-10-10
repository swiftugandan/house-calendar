package dev.housecalendar.app.model

import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Slot
import dev.housecalendar.schedule.Agenda
import dev.housecalendar.schedule.AgendaEntry
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Clashes
import dev.housecalendar.schedule.DateWindow
import dev.housecalendar.schedule.OccurrenceRef
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** One occurrence as a row of the agenda or the week (DESIGN.md "Agenda row", "Entry line"). */
data class EntryRow(
    val key: OccurrenceRef,
    val allDay: Boolean,
    val start: LocalTime?,
    val end: LocalTime?,
    val endsOnLaterDay: Boolean,
    /** A multi-day occurrence on a day after its first. */
    val continued: Boolean,
    val title: String,
    val icon: IconKey,
    val people: List<PersonMark>,
    val everyone: Boolean,
    val taking: TakingMark,
    val place: String,
    val repeating: Boolean,
    val ended: Boolean,
    val clashes: List<ClashMark>,
)

data class AgendaDay(
    val date: LocalDate,
    val word: DayWord,
    val holidays: List<String>,
    val rows: List<EntryRow>,
    /** Today only: the row index the now line is drawn above (rows.size draws it below the last). */
    val nowLineBefore: Int?,
)

/**
 * The phone's agenda (SPEC FR-29, FR-30, FR-33, FR-34): days from [from], with days that have nothing on
 * collapsed except today; multi-day occurrences on every day they cover; clashes on both occurrences.
 */
object AgendaModel {
    fun build(state: CalendarState, from: LocalDate, days: Int, now: Instant, filter: MemberId? = null): List<AgendaDay> {
        val to = from.plusDays(days - 1L)
        val today = Marks.today(state, now)
        val marks = Marks(state)
        val entries = Agenda.resolve(state, DateWindow(from, to))
        val clashes = Clashes.find(entries, state.zone)
        val shown = if (filter == null) entries else entries.filter { it.involves(filter) || it.taker == filter }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.mapNotNull { day ->
            val onDay = shown.filter { day in it.day..it.lastDay }.sortedWith(rowOrder(day))
            if (onDay.isEmpty() && day != today) return@mapNotNull null
            val rows = onDay.map { row(it, day, state, now, marks, clashes) }
            val nowTime = now.atZone(state.zone).toLocalTime()
            AgendaDay(day, Marks.word(day, today), holidaysOn(state, day), rows, if (day == today) nowLine(rows, nowTime) else null)
        }.toList()
    }

    internal fun row(entry: AgendaEntry, day: LocalDate, state: CalendarState, now: Instant, marks: Marks, clashes: List<dev.housecalendar.schedule.Clash>): EntryRow {
        val timed = entry.slot as? Slot.Timed
        val continued = entry.day < day
        return EntryRow(
            key = entry.key,
            allDay = timed == null || continued,
            start = timed?.start?.takeUnless { continued },
            end = timed?.end?.takeUnless { continued },
            endsOnLaterDay = (timed?.endDayOffset ?: 0) > 0,
            continued = continued,
            title = entry.title,
            icon = entry.icon,
            people = marks.people(entry.people),
            everyone = entry.everyone,
            taking = marks.taking(entry.taking),
            place = entry.place,
            repeating = entry.repeating,
            ended = Marks.ended(entry, state, now),
            clashes = marks.clashes(entry, clashes),
        )
    }

    internal fun holidaysOn(state: CalendarState, day: LocalDate): List<String> =
        state.activeHolidays().filter { day in it.first..it.last }.map { it.name }

    /** Rows that read as all day on [day] (all-day ones, and ones continuing from an earlier day) come first. */
    internal fun rowOrder(day: LocalDate): Comparator<AgendaEntry> =
        compareBy<AgendaEntry> { !(it.allDay || it.day < day) }.then(Agenda.ORDER)

    /** DESIGN.md: the line sits between the last timed row that has started and the next one. */
    private fun nowLine(rows: List<EntryRow>, now: LocalTime): Int? {
        if (rows.none { !it.allDay }) return null
        val index = rows.indexOfFirst { row -> !row.allDay && (row.start ?: LocalTime.MIN) > now }
        return if (index < 0) rows.size else index
    }
}
