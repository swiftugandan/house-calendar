package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import java.time.LocalDate

/** The fields in force for an original date: the event, overlaid by its change-froms (SPEC FR-23). */
internal data class Fields(
    val title: String,
    val icon: IconKey,
    val slot: Slot,
    val repeat: Repeat,
    val anchor: LocalDate,
    val termTimeOnly: Boolean,
    val who: Who,
    val taking: Taking,
    val place: String,
    val notes: String,
    val reminders: List<Reminder>,
    val ended: Boolean,
) {
    fun overlay(change: ChangeRecord, from: LocalDate): Fields = copy(
        title = change.title?.value ?: title,
        icon = change.icon?.value ?: icon,
        slot = change.slot?.value ?: slot,
        repeat = change.repeat?.value ?: repeat,
        anchor = if (change.repeat != null) maxOf(from, anchor) else anchor,
        termTimeOnly = change.termTimeOnly?.value ?: termTimeOnly,
        who = change.who?.value ?: who,
        taking = change.taking?.value ?: taking,
        place = change.place?.value ?: place,
        notes = change.notes?.value ?: notes,
        reminders = change.reminders?.value ?: reminders,
        ended = change.ended?.value ?: ended,
    )
}

/** Fields from [Fields.from] onward, up to the next segment. */
internal data class Segment(val from: LocalDate, val fields: Fields)

/**
 * One event resolved for occurrence tests (SPEC FR-23 to FR-25): its segments in ascending order, its
 * occurrence edits, and the bounds outside which it cannot have an occurrence (SAD §3.2's interval index).
 */
internal class Series private constructor(
    val id: EventId,
    val record: EventRecord,
    val start: LocalDate,
    private val segments: List<Segment>,
    val edits: Map<LocalDate, EditRecord>,
    private val isHoliday: (LocalDate) -> Boolean,
) {
    /** Whether any segment has a rule that actually repeats; decides whether edits ask for a scope. */
    val repeats: Boolean = segments.any { RepeatRules.repeats(it.fields.repeat) }

    /** The rule of some segment cannot be read by this version (SPEC FR-46). */
    val repeatUnrecognised: Boolean = segments.any { it.fields.repeat is Repeat.Unrecognised }

    /** No original date is earlier than this. */
    val earliest: LocalDate get() = start

    /** No original date is later than this; [LocalDate.MAX] when some segment repeats without end. */
    val latest: LocalDate = segments.indices.maxOf { index ->
        val fields = segments[index].fields
        val next = segments.getOrNull(index + 1)?.from
        when {
            fields.ended -> start
            !RepeatRules.repeats(fields.repeat) -> start
            else -> RepeatRules.until(fields.repeat) ?: next?.minusDays(1) ?: LocalDate.MAX
        }
    }

    /** The day of the earliest occurrence, or null if there is none within [FIRST_SEARCH_DAYS]. */
    val firstOccurrence: LocalDate? by lazy {
        generateSequence(start) { it.plusDays(1) }
            .take(FIRST_SEARCH_DAYS)
            .takeWhile { it <= latest }
            .firstOrNull(::occurs)
    }

    fun fieldsAt(date: LocalDate): Fields {
        var low = 0
        var high = segments.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (segments[middle].from <= date) low = middle else high = middle - 1
        }
        return segments[low].fields
    }

    /** SPEC FR-24. Occurrence edits do not change the answer. */
    fun occurs(date: LocalDate): Boolean {
        if (date < start) return false
        val fields = fieldsAt(date)
        if (fields.ended) return false
        if (!RepeatRules.repeats(fields.repeat)) return date == start
        if (!RepeatRules.matches(fields.repeat, fields.anchor, date)) return false
        return !(fields.termTimeOnly && isHoliday(date))
    }

    companion object {
        /** Ten years: further than any rule needs to find its first date (29 February takes at most eight). */
        private const val FIRST_SEARCH_DAYS = 3_653

        fun of(state: CalendarState, id: EventId): Series? {
            val record = state.events[id] ?: return null
            if (record.deleted?.value == true) return null
            val title = record.title?.value ?: return null
            val start = record.start?.value ?: return null
            val slot = record.slot?.value ?: return null
            val base = Fields(
                title = title,
                icon = record.icon?.value ?: IconKey.NONE,
                slot = slot,
                repeat = record.repeat?.value ?: Repeat.Never,
                anchor = start,
                termTimeOnly = record.termTimeOnly?.value ?: false,
                who = record.who?.value ?: Who.Everyone,
                taking = record.taking?.value ?: Taking.NotNeeded,
                place = record.place?.value.orEmpty(),
                notes = record.notes?.value.orEmpty(),
                reminders = record.reminders?.value.orEmpty(),
                ended = false,
            )
            val segments = mutableListOf(Segment(start, base))
            state.changes[id].orEmpty().forEach { (from, change) ->
                val effective = maxOf(from, start)
                val previous = segments.last()
                val fields = previous.fields.overlay(change, from)
                if (effective == previous.from) segments[segments.size - 1] = Segment(effective, fields) else segments += Segment(effective, fields)
            }
            return Series(id, record, start, segments, state.edits[id].orEmpty(), state::isHoliday)
        }

    }
}

/** A cached answer to "what is this event's series?", including "it has none" (deleted or incomplete). */
internal sealed interface SeriesLookup {
    data class Found(val series: Series) : SeriesLookup

    data object Missing : SeriesLookup
}
