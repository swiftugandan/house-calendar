package dev.housecalendar.app.model

import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.schedule.Agenda
import dev.housecalendar.schedule.AgendaEntry
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.DateWindow
import dev.housecalendar.schedule.NeedsSomeone
import dev.housecalendar.schedule.OccurrenceRef
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class OrganiserLayout { COLUMNS, LIST }

/** Why an entry is in a person's column: they are in it, or they are taking someone (DESIGN.md "Organiser grid"). */
enum class CellRole { INVOLVED, TAKING }

data class CellEntry(
    val key: OccurrenceRef,
    val allDay: Boolean,
    val start: LocalTime?,
    val title: String,
    val icon: IconKey,
    /** The other people involved, as small discs after the title. */
    val others: List<PersonMark>,
    val role: CellRole,
    val taking: TakingMark,
    val place: String,
    val repeating: Boolean,
    val ended: Boolean,
    val now: Boolean,
)

data class OrganiserDay(
    val date: LocalDate,
    val word: DayWord,
    val holidays: List<String>,
    /** "Everyone" occurrences, drawn once across every column. */
    val everyone: List<CellEntry>,
    /** One list per column, in [Organiser.columns] order. Empty in the list layout. */
    val cells: List<List<CellEntry>>,
    /** Every occurrence of the day, for the list layout. */
    val list: List<CellEntry>,
)

data class Organiser(val layout: OrganiserLayout, val columns: List<PersonMark>, val days: List<OrganiserDay>)

/** The display's Week view: the family organiser (SPEC FR-31, FR-32). */
object OrganiserModel {
    private const val MAX_COLUMNS = 6
    private const val DAYS_WIDE = 7
    private const val DAYS_NARROW = 5
    private const val WIDE_COLUMNS = 4

    /** DESIGN.md fit check: up to 4 columns fit 7 days at two entries per cell; 5 or 6 columns fit 5. */
    fun daysShown(columns: Int): Int = if (columns in (WIDE_COLUMNS + 1)..MAX_COLUMNS) DAYS_NARROW else DAYS_WIDE

    fun build(state: CalendarState, now: Instant, firstDay: LocalDate? = null): Organiser {
        val today = Marks.today(state, now)
        val from = firstDay ?: today
        val marks = Marks(state)
        val members = state.activeMembers().map(Marks::mark)
        val layout = if (members.size <= MAX_COLUMNS) OrganiserLayout.COLUMNS else OrganiserLayout.LIST
        val columns = if (layout == OrganiserLayout.COLUMNS) members else emptyList()
        val count = if (layout == OrganiserLayout.COLUMNS) daysShown(columns.size) else DAYS_WIDE
        val entries = Agenda.resolve(state, DateWindow(from, from.plusDays(count - 1L)))
        val days = (0 until count).map { offset ->
            val day = from.plusDays(offset.toLong())
            val onDay = entries.filter { day in it.day..it.lastDay }.sortedWith(AgendaModel.rowOrder(day))
            fun cell(entry: AgendaEntry, role: CellRole, column: PersonMark?): CellEntry = CellEntry(
                key = entry.key,
                allDay = entry.allDay || entry.day < day,
                start = Marks.startOn(entry, day),
                title = entry.title,
                icon = entry.icon,
                others = marks.people(entry.people.filter { it != column?.id }).takeUnless { entry.everyone }.orEmpty(),
                role = role,
                taking = marks.taking(entry.taking),
                place = entry.place,
                repeating = entry.repeating,
                ended = Marks.ended(entry, state, now),
                now = Marks.happening(entry, state, now),
            )
            OrganiserDay(
                date = day,
                word = Marks.word(day, today),
                holidays = AgendaModel.holidaysOn(state, day),
                everyone = if (layout == OrganiserLayout.COLUMNS) onDay.filter { it.everyone }.map { cell(it, CellRole.INVOLVED, null) } else emptyList(),
                cells = columns.map { column ->
                    onDay.filter { !it.everyone || layout == OrganiserLayout.LIST }.mapNotNull { entry ->
                        when {
                            entry.everyone -> null
                            entry.involves(column.id) -> cell(entry, CellRole.INVOLVED, column)
                            entry.taker == column.id -> cell(entry, CellRole.TAKING, column)
                            else -> null
                        }
                    }
                },
                list = if (layout == OrganiserLayout.LIST) onDay.map { cell(it, CellRole.INVOLVED, null) } else emptyList(),
            )
        }
        return Organiser(layout, columns, days)
    }
}

/** One occurrence on the today board (DESIGN.md "Today board"). */
data class BoardEntry(
    val key: OccurrenceRef,
    val date: LocalDate,
    val allDay: Boolean,
    val start: LocalTime?,
    val end: LocalTime?,
    val title: String,
    val icon: IconKey,
    val taking: TakingMark,
    val place: String,
)

/** A member's now and next; [person] is null for the "everyone" row. */
data class BoardRow(val person: PersonMark?, val now: BoardEntry?, val next: BoardEntry?)

/** The display's Today view: now and next for each member (SPEC FR-31). */
object TodayBoardModel {
    /** How far ahead "next" looks when nothing more is on today. */
    private const val NEXT_DAYS = 7L

    fun build(state: CalendarState, now: Instant): List<BoardRow> {
        val today = Marks.today(state, now)
        val marks = Marks(state)
        val entries = Agenda.resolve(state, DateWindow(today, today.plusDays(NEXT_DAYS)))
        fun board(entry: AgendaEntry): BoardEntry {
            val timed = entry.slot as? Slot.Timed
            return BoardEntry(entry.key, entry.day, timed == null, timed?.start, timed?.end, entry.title, entry.icon, marks.taking(entry.taking), entry.place)
        }
        fun rowFor(mine: List<AgendaEntry>): Pair<BoardEntry?, BoardEntry?> {
            val current = mine.firstOrNull { Marks.happening(it, state, now) }
                ?: mine.firstOrNull { it.allDay && today in it.day..it.lastDay }
            val upcoming = mine.firstOrNull { entry ->
                entry != current && !Marks.ended(entry, state, now) && !Marks.happening(entry, state, now) && !(entry.allDay && entry.day <= today)
            }
            return current?.let(::board) to upcoming?.let(::board)
        }
        val everyone = entries.filter { it.everyone }
        val (everyoneNow, everyoneNext) = rowFor(everyone.filter { it.day == today })
        val members = state.activeMembers().map(Marks::mark).map { person ->
            val (current, next) = rowFor(entries.filter { !it.everyone && (it.involves(person.id) || it.taker == person.id) })
            BoardRow(person, current, next)
        }
        return (if (everyoneNow != null || everyoneNext != null) listOf(BoardRow(null, everyoneNow, everyoneNext)) else emptyList()) + members
    }
}

data class MonthCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val today: Boolean,
    val holiday: Boolean,
    val people: List<PersonMark>,
    val everyone: Boolean,
    val titles: List<String>,
    val more: Int,
)

data class MonthGrid(val month: YearMonth, val weekdays: List<DayOfWeek>, val weeks: List<List<MonthCell>>)

/** The month view (SPEC FR-29): whole weeks from the family's week start, a mark per person, three titles. */
object MonthModel {
    private const val TITLES = 3
    private const val DAYS_PER_WEEK = 7

    fun build(state: CalendarState, month: YearMonth, today: LocalDate): MonthGrid {
        val weekStart = state.weekStart
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
        val marks = Marks(state)
        val entries = Agenda.resolve(state, DateWindow(first, last))
        val cells = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { day ->
            val onDay = entries.filter { day in it.day..it.lastDay }.sortedWith(AgendaModel.rowOrder(day))
            val people = onDay.filterNot { it.everyone }.flatMap { it.people }.distinct().sortedWith(state.memberOrder)
            MonthCell(
                date = day,
                inMonth = YearMonth.from(day) == month,
                today = day == today,
                holiday = state.isHoliday(day),
                people = marks.people(people),
                everyone = onDay.any { it.everyone },
                titles = onDay.take(TITLES).map { it.title },
                more = (onDay.size - TITLES).coerceAtLeast(0),
            )
        }.toList()
        return MonthGrid(month, (0 until DAYS_PER_WEEK).map { weekStart.plus(it.toLong()) }, cells.chunked(DAYS_PER_WEEK))
    }
}

/** One unresolved lift (SPEC FR-35). */
data class NeedsRow(val key: OccurrenceRef, val date: LocalDate, val start: LocalTime?, val title: String, val icon: IconKey, val people: List<PersonMark>)

object NeedsSomeoneModel {
    fun build(state: CalendarState, now: Instant): List<NeedsRow> {
        val marks = Marks(state)
        return NeedsSomeone.list(state, now).map { entry ->
            NeedsRow(entry.key, entry.day, (entry.slot as? Slot.Timed)?.start, entry.title, entry.icon, marks.people(entry.people))
        }
    }

    /** The people offered first for taking (SPEC FR-11): the adults, in order. */
    fun adults(state: CalendarState): List<PersonMark> =
        state.activeMembers().filter { it.kind != MemberKind.Child }.map(Marks::mark)

    /** Recording "I'll take" for one occurrence is an edit with a person; scopes come from the planner. */
    fun taking(person: PersonMark): Taking = Taking.Person(person.id)
}
