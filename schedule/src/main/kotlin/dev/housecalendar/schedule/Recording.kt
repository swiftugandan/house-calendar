package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import java.time.LocalDate

/** Everything the add sheet collects for a new event (SPEC FR-8, FR-12). */
public data class EventDraft(
    val title: String,
    val icon: IconKey,
    val start: LocalDate,
    val slot: Slot,
    val repeat: Repeat,
    val termTimeOnly: Boolean,
    val who: Who,
    val taking: Taking,
    val place: String,
    val notes: String,
    val reminders: List<Reminder>,
)

public enum class Problem {
    BLANK_TITLE,
    TITLE_TOO_LONG,
    PLACE_TOO_LONG,
    NOTES_TOO_LONG,
    BAD_SLOT,
    BAD_REPEAT,
    UNTIL_BEFORE_START,
    START_NOT_IN_RULE,
    NO_PEOPLE,
    UNKNOWN_MEMBER,
    BAD_REMINDERS,
}

public sealed interface Verdict {
    public data object Ok : Verdict

    public data class Problems(val problems: Set<Problem>) : Verdict
}

/** SPEC FR-19: checked on the recording device only, never when merging (FR-43). */
public object Recording {
    /** Twenty years: how far "after N times" looks before saying the rule never gets there. */
    private const val COUNT_SEARCH_DAYS: Long = 7_305

    public fun check(state: CalendarState, draft: EventDraft): Verdict {
        val problems = buildSet {
            addAll(textProblems(draft))
            if (!Slots.isValid(draft.slot)) add(Problem.BAD_SLOT)
            addAll(repeatProblems(draft))
            addAll(peopleProblems(state, draft.who, draft.taking))
            if (!remindersValid(draft.reminders)) add(Problem.BAD_REMINDERS)
        }
        return if (problems.isEmpty()) Verdict.Ok else Verdict.Problems(problems)
    }

    /** The one payload that records a checked draft, with every field written. */
    public fun newEvent(id: EventId, draft: EventDraft): EventUpsert = EventUpsert(
        id, title = draft.title.trim(), icon = draft.icon, start = draft.start, slot = draft.slot, repeat = draft.repeat,
        termTimeOnly = draft.termTimeOnly, who = draft.who, taking = draft.taking, place = draft.place.trim(),
        notes = draft.notes.trim(), reminders = draft.reminders, deleted = false,
    )

    /**
     * SPEC FR-22: the original date of the [count]th occurrence as this device sees it now, including the
     * term-time skip. Null when the rule never gets there.
     */
    public fun untilForCount(state: CalendarState, start: LocalDate, repeat: Repeat, termTimeOnly: Boolean, count: Int): LocalDate? {
        if (count < 1) return null
        if (!RepeatRules.repeats(repeat)) return if (count == 1) start else null
        val rule = RepeatRules.withoutUntil(repeat)
        var seen = 0
        var day = start
        val stop = start.plusDays(COUNT_SEARCH_DAYS)
        while (day <= stop) {
            if (RepeatRules.matches(rule, start, day) && !(termTimeOnly && state.isHoliday(day))) {
                seen += 1
                if (seen == count) return day
            }
            day = day.plusDays(1)
        }
        return null
    }

    private fun textProblems(draft: EventDraft): Set<Problem> = buildSet {
        val title = draft.title.trim()
        if (title.isEmpty()) add(Problem.BLANK_TITLE)
        if (title.codePointCount(0, title.length) > Limits.TITLE_CHARS) add(Problem.TITLE_TOO_LONG)
        if (draft.place.trim().length > Limits.PLACE_CHARS) add(Problem.PLACE_TOO_LONG)
        if (draft.notes.trim().length > Limits.NOTES_CHARS) add(Problem.NOTES_TOO_LONG)
    }

    private fun repeatProblems(draft: EventDraft): Set<Problem> = buildSet {
        if (!RepeatRules.isWellFormed(draft.repeat)) {
            add(Problem.BAD_REPEAT)
            return@buildSet
        }
        val until = RepeatRules.until(draft.repeat)
        if (until != null && until < draft.start) add(Problem.UNTIL_BEFORE_START)
        if (RepeatRules.repeats(draft.repeat) && !RepeatRules.matches(RepeatRules.withoutUntil(draft.repeat), draft.start, draft.start)) {
            add(Problem.START_NOT_IN_RULE)
        }
    }

    internal fun peopleProblems(state: CalendarState, who: Who, taking: Taking): Set<Problem> = buildSet {
        val named = when (who) {
            is Who.Members -> who.members.also { if (it.isEmpty()) add(Problem.NO_PEOPLE) }
            Who.Everyone, is Who.Unrecognised -> emptySet()
        } + listOfNotNull((taking as? Taking.Person)?.member)
        if (named.any { it !in state.members }) add(Problem.UNKNOWN_MEMBER)
    }

    private fun remindersValid(reminders: List<Reminder>): Boolean = reminders.size <= Limits.MAX_REMINDERS && reminders.all { reminder ->
        when (reminder) {
            is Reminder.MinutesBefore -> reminder.minutes in 0..Limits.MAX_MINUTES_BEFORE
            is Reminder.DaysBeforeAt -> reminder.days in 0..Limits.MAX_DAYS_BEFORE
            is Reminder.Unrecognised -> false
        }
    }
}
