package dev.housecalendar.app.model

import dev.housecalendar.app.ui.icons.IconSuggester
import dev.housecalendar.contracts.CalendarIds
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.HolidayId
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.Payload
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import dev.housecalendar.intake.IncomingDraft
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Recording
import dev.housecalendar.schedule.RepeatRules
import java.time.LocalDateTime

/**
 * Drafts from other apps (SPEC FR-49 to FR-55) into the app: one opens the add sheet; several go through the
 * import review, which records events for everyone (or holidays), with deterministic ids from UIDs (FR-54).
 */
object IntakeModel {
    fun toDraft(incoming: IncomingDraft, now: LocalDateTime): Draft {
        val day = incoming.day ?: now.toLocalDate()
        var draft = DraftModel.withTitle(DraftModel.blank(day, now), incoming.title)
        when (val slot = incoming.slot) {
            is Slot.AllDay -> draft = DraftModel.withEndDay(DraftModel.withAllDay(draft, true), day.plusDays(slot.days - 1L))
            is Slot.Timed -> draft = DraftModel.withTimes(draft, slot.start, slot.end).let { timed ->
                if (slot.endDayOffset > 0) timed.copy(endDay = day.plusDays(slot.endDayOffset.toLong())) else timed
            }
            is Slot.Unrecognised, null -> Unit
        }
        if (RepeatRules.repeats(incoming.repeat)) {
            draft = DraftModel.withRule(draft, incoming.repeat)
            incoming.count?.let { draft = draft.copy(ends = Ends.After(it)) }
        }
        return draft.copy(place = incoming.place, notes = incoming.notes)
    }

    /** The review's "Add N events": everyone's, no taking needed, no reminders; EXDATEs cancelled (FR-52). */
    fun eventPayloads(items: List<IncomingDraft>, state: CalendarState, newId: () -> EventId): List<Payload> = items.flatMap { item ->
        val day = item.day ?: return@flatMap emptyList()
        val slot = item.slot ?: return@flatMap emptyList()
        val id = item.uid?.let(CalendarIds::externalEvent) ?: newId()
        val rule = item.count?.let { count -> RepeatRules.withUntil(item.repeat, Recording.untilForCount(state, day, item.repeat, false, count)) } ?: item.repeat
        val event = EventUpsert(
            id, title = item.title, icon = IconSuggester.suggest(item.title), start = day, slot = slot, repeat = rule, termTimeOnly = false,
            who = Who.Everyone, taking = Taking.NotNeeded, place = item.place, notes = item.notes, reminders = emptyList(), deleted = false,
        )
        listOf(event) + item.exdates.map { EventEdit(id, occurrence = it, cancelled = true) }
    }

    fun canBeHolidays(items: List<IncomingDraft>): Boolean = items.isNotEmpty() && items.all { it.allDay && it.day != null }

    /** "Add as holidays" (SPEC FR-50): how a school's term-dates file becomes the family's holidays. */
    fun holidayPayloads(items: List<IncomingDraft>, newId: () -> HolidayId): List<HolidayUpsert> = items.mapNotNull { item ->
        val first = item.day ?: return@mapNotNull null
        val last = item.lastDay ?: first
        HolidayUpsert(item.uid?.let(CalendarIds::externalHoliday) ?: newId(), name = item.title, first = first, last = last, deleted = false)
    }

    /** SPEC FR-54: confirming records an update of the same event, not a second one. */
    fun alreadyOnCalendar(incoming: IncomingDraft, state: CalendarState): Boolean =
        incoming.uid?.let { CalendarIds.externalEvent(it) in state.events || CalendarIds.externalHoliday(it) in state.holidays } ?: false

}
