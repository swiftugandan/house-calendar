package dev.housecalendar.app.model

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.schedule.Agenda
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Clashes
import dev.housecalendar.schedule.DateWindow
import dev.housecalendar.schedule.DueReminder
import dev.housecalendar.schedule.OccurrenceRef
import dev.housecalendar.schedule.ReminderPlan
import dev.housecalendar.schedule.ReminderScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Event detail (DESIGN.md "Screens": time, place and taking win; FR-18 who added and changed it). */
data class EventDetail(
    val key: OccurrenceRef,
    val title: String,
    val icon: IconKey,
    val day: LocalDate,
    val lastDay: LocalDate,
    val allDay: Boolean,
    val start: LocalTime?,
    val end: LocalTime?,
    val repeat: Repeat,
    val termTimeOnly: Boolean,
    val repeating: Boolean,
    val repeatUnrecognised: Boolean,
    val people: List<PersonMark>,
    val everyone: Boolean,
    val taking: TakingMark,
    val place: String,
    val notes: String,
    val reminders: List<Reminder>,
    val addedBy: DeviceLabel,
    val changedBy: DeviceLabel,
    val clashes: List<ClashMark>,
    val moved: Boolean,
)

object EventDetailModel {
    fun build(state: CalendarState, ref: OccurrenceRef, self: DeviceId): EventDetail? {
        val entry = Agenda.occurrence(state, ref) ?: return null
        val record = state.events[ref.event] ?: return null
        val marks = Marks(state)
        val timed = entry.slot as? Slot.Timed
        val sameDays = Agenda.resolve(state, DateWindow(entry.day, entry.lastDay))
        return EventDetail(
            key = ref, title = entry.title, icon = entry.icon, day = entry.day, lastDay = entry.lastDay, allDay = timed == null,
            start = timed?.start, end = timed?.end, repeat = record.repeat?.value ?: Repeat.Never,
            termTimeOnly = record.termTimeOnly?.value ?: false, repeating = entry.repeating, repeatUnrecognised = entry.repeatUnrecognised,
            people = marks.people(entry.people), everyone = entry.everyone, taking = marks.taking(entry.taking), place = entry.place,
            notes = entry.notes, reminders = entry.reminders, addedBy = marks.device(record.created.device, self),
            changedBy = marks.device(record.changed.device, self), clashes = marks.clashes(entry, Clashes.find(sameDays, state.zone)),
            moved = entry.moved,
        )
    }
}

/** One reminder to post now; [late] when it is posted more than a few minutes after it was due (SPEC FR-38). */
data class Posting(val due: DueReminder, val late: Boolean)

/** What the reminder receiver does: what to post, when to wake next, and how far it has now fired. */
data class ReminderDecision(val post: List<Posting>, val nextAlarm: Instant?, val fired: Instant)

/**
 * The reminder scheduler's decisions, kept pure so they are tested on the JVM (SAD §3.8 "Reminders"):
 * recomputed from the log at fire time, so a sync that moved or cancelled an event is always respected.
 */
object ReminderDecisions {
    /** SPEC FR-38: later than this, a reminder no longer changes what someone does. */
    val LATE_WINDOW: Duration = Duration.ofMinutes(60)

    /** Posted more than this after its time, a reminder is marked late. */
    val LATE_MARK: Duration = Duration.ofMinutes(5)

    /** How far ahead the next alarm is looked for; reminders are at most a week ahead (SPEC FR-36). */
    val HORIZON: Duration = Duration.ofDays(14)

    fun decide(state: CalendarState, owner: MemberId?, scope: ReminderScope, lastFired: Instant?, now: Instant): ReminderDecision {
        val earliest = now.minus(LATE_WINDOW)
        val after = if (lastFired == null || lastFired < earliest) earliest else lastFired
        val post = if (after < now) {
            ReminderPlan.due(state, owner, scope, after, now).map { Posting(it, Duration.between(it.at, now) > LATE_MARK) }
        } else {
            emptyList()
        }
        val next = ReminderPlan.due(state, owner, scope, now, now.plus(HORIZON)).firstOrNull()?.at
        return ReminderDecision(post, next, maxOf(now, lastFired ?: now))
    }
}
