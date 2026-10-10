package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.Payload
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The scope question's answers (SPEC FR-15, DESIGN.md "Scope question"). */
public enum class Scope { ONLY, FOLLOWING, ALL }

/** What a person changed. Null means unchanged. [day] moves the occurrence. */
public data class Changes(
    val title: String? = null,
    val icon: IconKey? = null,
    val day: LocalDate? = null,
    val slot: Slot? = null,
    val who: Who? = null,
    val taking: Taking? = null,
    val place: String? = null,
    val notes: String? = null,
    val repeat: Repeat? = null,
    val termTimeOnly: Boolean? = null,
    val reminders: List<Reminder>? = null,
) {
    internal val touchesSeries: Boolean get() = repeat != null || termTimeOnly != null || reminders != null
}

public sealed interface Plan {
    /** Record these payloads, in order, in one append. */
    public data class Record(val payloads: List<Payload>) : Plan

    public data class Refused(val reason: PlanRefusal) : Plan
}

public enum class PlanRefusal {
    UNKNOWN_EVENT,
    NOT_AN_OCCURRENCE,

    /** "Only this one" cannot change the repeat rule, term time or reminders (CONTRACTS.md field table). */
    SERIES_FIELD_IN_SINGLE_EDIT,

    /** "This and following" can only move the day of a weekly rule (SPEC FR-15). */
    MOVE_NEEDS_WEEKLY_RULE,

    /** A move across a week boundary would put the occurrence in a week a fortnightly rule skips. */
    MOVE_ACROSS_SKIPPED_WEEK,
}

/**
 * Turns an edit or a deletion of one occurrence, with its scope, into exactly the payloads SPEC FR-15 and
 * FR-16 name. Every plan is a set of register writes keyed by data the person can see, so plans recorded on
 * two devices while apart always merge (SAD ADR-11).
 */
public object EditPlanner {
    public fun change(state: CalendarState, ref: OccurrenceRef, scope: Scope, changes: Changes): Plan {
        val series = state.series(ref.event) ?: return Plan.Refused(PlanRefusal.UNKNOWN_EVENT)
        if (!series.occurs(ref.occurrence)) return Plan.Refused(PlanRefusal.NOT_AN_OCCURRENCE)
        val effective = effectiveScope(series, ref, scope)
        return when {
            !series.repeats -> Plan.Record(listOf(wholeEvent(series, ref.occurrence, changes)))
            effective == Scope.ONLY -> onlyThisOne(ref, changes)
            effective == Scope.FOLLOWING -> thisAndFollowing(series, ref, changes)
            else -> Plan.Record(listOf(wholeEvent(series, ref.occurrence, changes)) + propagated(state, series, ref.occurrence, changes))
        }
    }

    public fun delete(state: CalendarState, ref: OccurrenceRef, scope: Scope): Plan {
        val series = state.series(ref.event) ?: return Plan.Refused(PlanRefusal.UNKNOWN_EVENT)
        if (!series.occurs(ref.occurrence)) return Plan.Refused(PlanRefusal.NOT_AN_OCCURRENCE)
        val payload = when (if (series.repeats) effectiveScope(series, ref, scope) else Scope.ALL) {
            Scope.ONLY -> EventEdit(ref.event, occurrence = ref.occurrence, cancelled = true)
            Scope.FOLLOWING -> EventChange(ref.event, from = ref.occurrence, ended = true)
            Scope.ALL -> EventUpsert(ref.event, deleted = true)
        }
        return Plan.Record(listOf(payload))
    }

    /** SPEC FR-17: bring a deleted event back as it was, with any changes made since. */
    public fun restore(event: EventId): EventUpsert = EventUpsert(event, deleted = false)

    /** "This and following" from the first occurrence is "all" (SPEC FR-15). */
    private fun effectiveScope(series: Series, ref: OccurrenceRef, scope: Scope): Scope =
        if (scope == Scope.FOLLOWING && ref.occurrence == series.firstOccurrence) Scope.ALL else scope

    private fun onlyThisOne(ref: OccurrenceRef, changes: Changes): Plan {
        if (changes.touchesSeries) return Plan.Refused(PlanRefusal.SERIES_FIELD_IN_SINGLE_EDIT)
        return Plan.Record(
            listOf(
                EventEdit(
                    ref.event, occurrence = ref.occurrence, movedTo = changes.day?.takeIf { it != ref.occurrence },
                    slot = changes.slot, title = changes.title, icon = changes.icon, who = changes.who, taking = changes.taking,
                    place = changes.place, notes = changes.notes,
                ),
            ),
        )
    }

    private fun thisAndFollowing(series: Series, ref: OccurrenceRef, changes: Changes): Plan {
        val newDay = changes.day?.takeIf { it != ref.occurrence }
        var from = ref.occurrence
        var repeat = changes.repeat
        if (newDay != null && repeat == null) {
            val rule = series.fieldsAt(ref.occurrence).repeat as? Repeat.Weekly ?: return Plan.Refused(PlanRefusal.MOVE_NEEDS_WEEKLY_RULE)
            val shifted = rule.copy(days = rule.days.map { it.plus(daysBetween(ref.occurrence, newDay)) }.toSet())
            from = minOf(ref.occurrence, newDay)
            if (!RepeatRules.matches(shifted, from, newDay)) return Plan.Refused(PlanRefusal.MOVE_ACROSS_SKIPPED_WEEK)
            repeat = shifted
        }
        return Plan.Record(
            listOf(
                EventChange(
                    ref.event, from = from, title = changes.title, icon = changes.icon, slot = changes.slot, repeat = repeat,
                    termTimeOnly = changes.termTimeOnly, who = changes.who, taking = changes.taking, place = changes.place,
                    notes = changes.notes, reminders = changes.reminders,
                ),
            ),
        )
    }

    /** The event's own fields; a new day moves the start and, for a rule tied to the day, the rule too. */
    private fun wholeEvent(series: Series, occurrence: LocalDate, changes: Changes): EventUpsert {
        val shift = changes.day?.let { daysBetween(occurrence, it) }?.takeIf { it != 0L }
        val start = shift?.let { series.start.plusDays(it) }
        val current = series.record.repeat?.value
        val repeat = changes.repeat ?: if (shift != null && start != null && current != null) {
            shiftRule(current, shift, start).takeIf { it != current }
        } else {
            null
        }
        return EventUpsert(
            series.id, title = changes.title, icon = changes.icon, start = start, slot = changes.slot, repeat = repeat,
            termTimeOnly = changes.termTimeOnly, who = changes.who, taking = changes.taking, place = changes.place,
            notes = changes.notes, reminders = changes.reminders,
        )
    }

    /** "All" also rewrites each change-from that sets a changed field, so the change shows everywhere (SPEC FR-15). */
    private fun propagated(state: CalendarState, series: Series, occurrence: LocalDate, changes: Changes): List<EventChange> {
        val shift = changes.day?.let { daysBetween(occurrence, it) }?.takeIf { it != 0L }
        return state.changes[series.id].orEmpty().mapNotNull { (from, record) ->
            val repeat = changes.repeat?.takeIf { record.repeat != null }
                ?: shift?.let { days -> record.repeat?.value?.let { rule -> shiftRule(rule, days, from.plusDays(days)).takeIf { it != rule } } }
            val change = EventChange(
                series.id, from = from,
                title = changes.title?.takeIf { record.title != null },
                icon = changes.icon?.takeIf { record.icon != null },
                slot = changes.slot?.takeIf { record.slot != null },
                repeat = repeat,
                termTimeOnly = changes.termTimeOnly?.takeIf { record.termTimeOnly != null },
                who = changes.who?.takeIf { record.who != null },
                taking = changes.taking?.takeIf { record.taking != null },
                place = changes.place?.takeIf { record.place != null },
                notes = changes.notes?.takeIf { record.notes != null },
                reminders = changes.reminders?.takeIf { record.reminders != null },
            )
            change.takeIf { it != EventChange(series.id, from = from) }
        }
    }

    /** Moves a rule's day-dependent parts along with its anchor. Rules anchored only by date need nothing. */
    private fun shiftRule(rule: Repeat, days: Long, newAnchor: LocalDate): Repeat = when (rule) {
        is Repeat.Weekly -> rule.copy(days = rule.days.map { it.plus(days) }.toSet())
        is Repeat.MonthlyOnDay -> rule.copy(dayOfMonth = newAnchor.dayOfMonth)
        is Repeat.MonthlyOnWeekday -> rule.copy(
            weekday = newAnchor.dayOfWeek,
            ordinal = if (rule.ordinal < 0 && RepeatRules.isLastOfMonth(newAnchor)) rule.ordinal else minOf(RepeatRules.ordinalOf(newAnchor), MAX_ORDINAL),
        )
        Repeat.Never, is Repeat.Daily, is Repeat.Yearly, is Repeat.Unrecognised -> rule
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    private const val MAX_ORDINAL: Int = 4
}
