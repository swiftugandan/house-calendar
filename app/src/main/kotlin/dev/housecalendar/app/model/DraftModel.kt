package dev.housecalendar.app.model

import dev.housecalendar.app.ui.icons.IconSuggester
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import dev.housecalendar.schedule.Agenda
import dev.housecalendar.schedule.AgendaEntry
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Changes
import dev.housecalendar.schedule.Clashes
import dev.housecalendar.schedule.DateWindow
import dev.housecalendar.schedule.EventDraft
import dev.housecalendar.schedule.OccurrenceRef
import dev.housecalendar.schedule.Recording
import dev.housecalendar.schedule.RepeatRules
import dev.housecalendar.schedule.Slots
import dev.housecalendar.schedule.Suggestion
import dev.housecalendar.schedule.Verdict
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The add sheet's repeat choices (DESIGN.md "Add sheet"). [CUSTOM] keeps a rule the sheet cannot express. */
enum class RepeatKind { NEVER, DAILY, WEEKLY, FORTNIGHTLY, MONTHLY_ON_DAY, MONTHLY_ON_WEEKDAY, MONTHLY_ON_LAST_WEEKDAY, YEARLY, CUSTOM }

/** "Ends": never, on a date, or after N times (SPEC FR-22). */
sealed interface Ends {
    data object Never : Ends

    data class On(val date: LocalDate) : Ends

    data class After(val count: Int) : Ends
}

/** Everything the add sheet holds while a person fills it in. */
data class Draft(
    val title: String,
    val icon: IconKey,
    /** The person chose the icon; titles no longer change it (SPEC FR-14). */
    val iconChosen: Boolean,
    val day: LocalDate,
    val allDay: Boolean,
    val start: LocalTime,
    val end: LocalTime,
    val endDay: LocalDate,
    val repeatKind: RepeatKind,
    val weekdays: Set<DayOfWeek>,
    val custom: Repeat?,
    val ends: Ends,
    val termTimeOnly: Boolean,
    val who: Who,
    val taking: Taking,
    val place: String,
    val notes: String,
    val reminders: List<Reminder>,
)

/** What the confirm button says will happen (DESIGN.md: "Add Swimming · Tuesdays from 13 Oct"). */
data class Outcome(val title: String, val day: LocalDate, val repeatKind: RepeatKind, val weekdays: Set<DayOfWeek>, val start: LocalTime?)

/** A clash the draft would create (SPEC FR-34, shown above the button). */
data class DraftClash(val person: MemberId, val other: AgendaEntry)

/** The add sheet's logic, separate from its composables so every rule is tested on the JVM. */
object DraftModel {
    /** SPEC FR-36: new timed events default to a reminder 30 minutes before. */
    val DEFAULT_TIMED_REMINDERS: List<Reminder> = listOf(Reminder.MinutesBefore(30))
    private const val DEFAULT_MINUTES = 60L
    private val LAST_MINUTE: LocalTime = LocalTime.of(23, 59)

    /** SPEC FR-12: timed, from the next whole hour for an hour, on the day being viewed. */
    fun blank(viewedDay: LocalDate, now: LocalDateTime): Draft {
        val nextHour = now.toLocalTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val crossesMidnight = nextHour <= now.toLocalTime()
        val start = if (crossesMidnight) LocalTime.of(LAST_MINUTE.hour, 0) else nextHour
        val end = if (crossesMidnight || start.plusMinutes(DEFAULT_MINUTES) <= start) LAST_MINUTE else start.plusMinutes(DEFAULT_MINUTES)
        return Draft(
            title = "", icon = IconKey.NONE, iconChosen = false, day = viewedDay, allDay = false, start = start, end = end,
            endDay = viewedDay, repeatKind = RepeatKind.NEVER, weekdays = emptySet(), custom = null, ends = Ends.Never,
            termTimeOnly = false, who = Who.Members(emptySet()), taking = Taking.NotNeeded, place = "", notes = "",
            reminders = DEFAULT_TIMED_REMINDERS,
        )
    }

    fun withTitle(draft: Draft, title: String): Draft =
        draft.copy(title = title, icon = if (draft.iconChosen) draft.icon else IconSuggester.suggest(title))

    fun withIcon(draft: Draft, icon: IconKey): Draft = draft.copy(icon = icon, iconChosen = true)

    /** Moves the day; a multi-day end moves with it, and weekly weekdays follow when there is just one. */
    fun withDay(draft: Draft, day: LocalDate): Draft {
        val shift = ChronoUnit.DAYS.between(draft.day, day)
        val weekdays = if (draft.weekdays == setOf(draft.day.dayOfWeek)) setOf(day.dayOfWeek) else draft.weekdays
        return draft.copy(day = day, endDay = draft.endDay.plusDays(shift), weekdays = weekdays)
    }

    fun withEndDay(draft: Draft, endDay: LocalDate): Draft = draft.copy(endDay = maxOf(endDay, draft.day))

    /** A time range; an end at or before the start ends on the following day. */
    fun withTimes(draft: Draft, start: LocalTime, end: LocalTime): Draft {
        val endDay = if (end <= start) draft.day.plusDays(1) else if (draft.endDay > draft.day && !draft.allDay) draft.endDay else draft.day
        return draft.copy(start = start, end = end, endDay = endDay)
    }

    fun withAllDay(draft: Draft, allDay: Boolean): Draft = draft.copy(
        allDay = allDay,
        endDay = if (allDay) draft.day else draft.endDay,
        reminders = when {
            allDay && draft.reminders == DEFAULT_TIMED_REMINDERS -> emptyList()
            !allDay && draft.reminders.isEmpty() -> DEFAULT_TIMED_REMINDERS
            else -> draft.reminders
        },
    )

    fun withRepeat(draft: Draft, kind: RepeatKind): Draft = draft.copy(
        repeatKind = kind,
        weekdays = if (kind == RepeatKind.WEEKLY || kind == RepeatKind.FORTNIGHTLY) draft.weekdays.ifEmpty { setOf(draft.day.dayOfWeek) } else draft.weekdays,
    )

    /** SPEC FR-13: a suggestion fills in what an earlier event had, but not the day being added. */
    fun applySuggestion(draft: Draft, suggestion: Suggestion): Draft {
        val slotted = when (val slot = suggestion.slot) {
            is Slot.AllDay -> withEndDay(withAllDay(draft, true), draft.day.plusDays(slot.days - 1L))
            is Slot.Timed -> withTimes(withAllDay(draft, false), slot.start, slot.end)
            is Slot.Unrecognised -> draft
        }
        return slotted.copy(
            title = suggestion.title, icon = suggestion.icon, iconChosen = !suggestion.icon.isNone, who = suggestion.who,
            taking = suggestion.taking, place = suggestion.place,
        )
    }

    fun slotOf(draft: Draft): Slot {
        val days = ChronoUnit.DAYS.between(draft.day, draft.endDay).toInt()
        return if (draft.allDay) Slot.AllDay(days + 1) else Slot.Timed(draft.start, draft.end, days)
    }

    /** The rule without its end (SPEC FR-20); the end comes from [Ends] in [toEventDraft]. */
    fun repeatOf(draft: Draft): Repeat = when (draft.repeatKind) {
        RepeatKind.NEVER -> Repeat.Never
        RepeatKind.DAILY -> Repeat.Daily(1)
        RepeatKind.WEEKLY -> Repeat.Weekly(1, draft.weekdays.ifEmpty { setOf(draft.day.dayOfWeek) })
        RepeatKind.FORTNIGHTLY -> Repeat.Weekly(2, draft.weekdays.ifEmpty { setOf(draft.day.dayOfWeek) })
        RepeatKind.MONTHLY_ON_DAY -> Repeat.MonthlyOnDay(1, draft.day.dayOfMonth)
        RepeatKind.MONTHLY_ON_WEEKDAY -> Repeat.MonthlyOnWeekday(1, minOf(RepeatRules.ordinalOf(draft.day), MAX_ORDINAL), draft.day.dayOfWeek)
        RepeatKind.MONTHLY_ON_LAST_WEEKDAY -> Repeat.MonthlyOnWeekday(1, LAST, draft.day.dayOfWeek)
        RepeatKind.YEARLY -> Repeat.Yearly(1)
        RepeatKind.CUSTOM -> draft.custom?.let(RepeatRules::withoutUntil) ?: Repeat.Never
    }

    /** The checked event (SPEC FR-19), with "after N times" turned into an until date (FR-22). */
    fun toEventDraft(draft: Draft, state: CalendarState): EventDraft {
        val rule = repeatOf(draft)
        val until = when (val ends = draft.ends) {
            Ends.Never -> null
            is Ends.On -> ends.date
            is Ends.After -> Recording.untilForCount(state, draft.day, rule, draft.termTimeOnly, ends.count)
        }
        return EventDraft(
            title = draft.title.trim(), icon = draft.icon, start = draft.day, slot = slotOf(draft),
            repeat = if (RepeatRules.repeats(rule)) withUntil(rule, until) else rule,
            termTimeOnly = draft.termTimeOnly && RepeatRules.repeats(rule), who = draft.who, taking = draft.taking,
            place = draft.place.trim(), notes = draft.notes.trim(), reminders = draft.reminders,
        )
    }

    fun canAdd(draft: Draft, state: CalendarState): Boolean = Recording.check(state, toEventDraft(draft, state)) == Verdict.Ok

    fun outcome(draft: Draft): Outcome = Outcome(
        draft.title.trim(), draft.day, draft.repeatKind,
        if (draft.repeatKind == RepeatKind.WEEKLY || draft.repeatKind == RepeatKind.FORTNIGHTLY) draft.weekdays else emptySet(),
        if (draft.allDay) null else draft.start,
    )

    /** SPEC FR-34: the clashes the draft's first occurrence would be part of. */
    fun clashes(draft: Draft, state: CalendarState): List<DraftClash> {
        val slot = slotOf(draft)
        if (!Slots.isValid(slot)) return emptyList()
        val people = when (val who = draft.who) {
            is Who.Members -> who.members.sortedWith(state.memberOrder)
            Who.Everyone, is Who.Unrecognised -> state.activeMembers().map { it.id }
        }
        val candidate = AgendaEntry(
            event = EventId(UUID(0, 0)), occurrence = draft.day, day = draft.day, lastDay = Slots.lastDay(slot, draft.day), slot = slot,
            title = draft.title, icon = draft.icon, people = people, everyone = draft.who !is Who.Members, taking = draft.taking,
            place = draft.place, notes = draft.notes, reminders = emptyList(), repeating = false, repeatUnrecognised = false,
            first = true, moved = false, edited = false,
        )
        val others = Agenda.resolve(state, DateWindow(draft.day, candidate.lastDay))
        return Clashes.with(candidate, others, state.zone).map { clash ->
            DraftClash(clash.person, if (clash.first.key == candidate.key) clash.second else clash.first)
        }
    }

    /** The sheet filled from an occurrence, for editing (SPEC FR-15). */
    fun fromOccurrence(state: CalendarState, ref: OccurrenceRef): Draft? {
        val entry = Agenda.occurrence(state, ref) ?: return null
        val record = state.events[ref.event] ?: return null
        val rule = record.repeat?.value ?: Repeat.Never
        val timed = entry.slot as? Slot.Timed
        val endDay = when (val slot = entry.slot) {
            is Slot.AllDay -> entry.day.plusDays(slot.days - 1L)
            is Slot.Timed -> entry.day.plusDays(slot.endDayOffset.toLong())
            is Slot.Unrecognised -> entry.day
        }
        return Draft(
            title = entry.title, icon = entry.icon, iconChosen = true, day = entry.day, allDay = timed == null,
            start = timed?.start ?: LocalTime.of(DEFAULT_HOUR, 0), end = timed?.end ?: LocalTime.of(DEFAULT_HOUR + 1, 0), endDay = endDay,
            repeatKind = kindOf(rule, entry.day), weekdays = (RepeatRules.withoutUntil(rule) as? Repeat.Weekly)?.days.orEmpty(),
            custom = rule, ends = RepeatRules.until(rule)?.let(Ends::On) ?: Ends.Never,
            termTimeOnly = record.termTimeOnly?.value ?: false, who = if (entry.everyone) Who.Everyone else Who.Members(entry.people.toSet()),
            taking = entry.taking, place = entry.place, notes = entry.notes, reminders = entry.reminders,
        )
    }

    /** What changed between the sheet as opened and as confirmed, for the edit planner. */
    fun changes(before: Draft, after: Draft): Changes {
        val repeatBefore = toRepeatWithEnd(before)
        val repeatAfter = toRepeatWithEnd(after)
        return Changes(
            title = after.title.trim().takeIf { it != before.title.trim() },
            icon = after.icon.takeIf { it != before.icon },
            day = after.day.takeIf { it != before.day },
            slot = slotOf(after).takeIf { it != slotOf(before) },
            who = after.who.takeIf { it != before.who },
            taking = after.taking.takeIf { it != before.taking },
            place = after.place.trim().takeIf { it != before.place.trim() },
            notes = after.notes.trim().takeIf { it != before.notes.trim() },
            repeat = repeatAfter.takeIf { it != repeatBefore && !(after.day != before.day && sameRuleShape(before, after)) },
            termTimeOnly = after.termTimeOnly.takeIf { it != before.termTimeOnly },
            reminders = after.reminders.takeIf { it != before.reminders },
        )
    }

    /**
     * A day change alone shifts the rule in the planner (SPEC FR-15), so the sheet's automatic weekday follow
     * is not a rule change: same kind, same end, and the weekdays moved by the same number of days.
     */
    private fun sameRuleShape(before: Draft, after: Draft): Boolean {
        val shift = ChronoUnit.DAYS.between(before.day, after.day)
        return before.repeatKind == after.repeatKind && before.ends == after.ends &&
            after.weekdays == before.weekdays.map { it.plus(shift) }.toSet()
    }

    private fun toRepeatWithEnd(draft: Draft): Repeat {
        val rule = repeatOf(draft)
        return withUntil(rule, (draft.ends as? Ends.On)?.date)
    }

    private fun kindOf(rule: Repeat, day: LocalDate): RepeatKind = when (rule) {
        Repeat.Never -> RepeatKind.NEVER
        is Repeat.Daily -> if (rule.interval == 1) RepeatKind.DAILY else RepeatKind.CUSTOM
        is Repeat.Weekly -> when (rule.interval) {
            1 -> RepeatKind.WEEKLY
            2 -> RepeatKind.FORTNIGHTLY
            else -> RepeatKind.CUSTOM
        }
        is Repeat.MonthlyOnDay -> if (rule.interval == 1 && rule.dayOfMonth == day.dayOfMonth) RepeatKind.MONTHLY_ON_DAY else RepeatKind.CUSTOM
        is Repeat.MonthlyOnWeekday -> when {
            rule.interval != 1 -> RepeatKind.CUSTOM
            rule.ordinal == LAST -> RepeatKind.MONTHLY_ON_LAST_WEEKDAY
            else -> RepeatKind.MONTHLY_ON_WEEKDAY
        }
        is Repeat.Yearly -> if (rule.interval == 1) RepeatKind.YEARLY else RepeatKind.CUSTOM
        is Repeat.Unrecognised -> RepeatKind.CUSTOM
    }

    private fun withUntil(rule: Repeat, until: LocalDate?): Repeat = when (rule) {
        Repeat.Never, is Repeat.Unrecognised -> rule
        is Repeat.Daily -> rule.copy(until = until)
        is Repeat.Weekly -> rule.copy(until = until)
        is Repeat.MonthlyOnDay -> rule.copy(until = until)
        is Repeat.MonthlyOnWeekday -> rule.copy(until = until)
        is Repeat.Yearly -> rule.copy(until = until)
    }

    private const val LAST = -1
    private const val MAX_ORDINAL = 4
    private const val DEFAULT_HOUR = 9
}
