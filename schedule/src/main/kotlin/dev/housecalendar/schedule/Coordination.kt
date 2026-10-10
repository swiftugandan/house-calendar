package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** A timed occurrence's instants, and the wall-clock times they show as (SPEC FR-27). */
public data class TimedSpan(val start: Instant, val end: Instant, val localStart: LocalDateTime, val localEnd: LocalDateTime)

public object Instants {
    /**
     * RFC 5545 §3.3.5 through `ZonedDateTime.of`: a repeated local time means its first occurrence; a
     * missing one is read with the offset before the gap. Null for all-day entries.
     */
    public fun of(entry: AgendaEntry, zone: ZoneId): TimedSpan? {
        val slot = entry.slot as? Slot.Timed ?: return null
        val start = ZonedDateTime.of(entry.day.atTime(slot.start), zone)
        val end = ZonedDateTime.of(entry.day.plusDays(slot.endDayOffset.toLong()).atTime(slot.end), zone)
        return TimedSpan(start.toInstant(), end.toInstant(), start.toLocalDateTime(), end.toLocalDateTime())
    }

    /** The instant a family-time day and time stand for. */
    public fun at(dateTime: LocalDateTime, zone: ZoneId): Instant = ZonedDateTime.of(dateTime, zone).toInstant()
}

/** Two timed occurrences that overlap and share [person], as involved or taking (SPEC FR-34). */
public data class Clash(val person: MemberId, val first: AgendaEntry, val second: AgendaEntry)

public object Clashes {
    /** Every clash among [entries], in agenda order of the pair, then member order. */
    public fun find(entries: List<AgendaEntry>, zone: ZoneId): List<Clash> {
        val timed = entries.sortedWith(Agenda.ORDER).mapNotNull { entry -> Instants.of(entry, zone)?.let { entry to it } }
        val clashes = mutableListOf<Clash>()
        for (i in timed.indices) {
            for (j in i + 1 until timed.size) {
                val (a, spanA) = timed[i]
                val (b, spanB) = timed[j]
                if (spanA.start < spanB.end && spanB.start < spanA.end) {
                    (peopleOf(a) intersect peopleOf(b)).forEach { person -> clashes += Clash(person, a, b) }
                }
            }
        }
        return clashes
    }

    /** The clashes a new or changed occurrence would be part of (SPEC FR-34, shown before confirming). */
    public fun with(candidate: AgendaEntry, others: List<AgendaEntry>, zone: ZoneId): List<Clash> =
        find(others.filterNot { it.key == candidate.key } + candidate, zone).filter { it.first.key == candidate.key || it.second.key == candidate.key }

    private fun peopleOf(entry: AgendaEntry): Set<MemberId> = (entry.people + listOfNotNull(entry.taker)).toCollection(LinkedHashSet())
}

public object NeedsSomeone {
    /** Days ahead that the list covers, today included (SPEC FR-35). */
    public const val DAYS: Long = 14

    /** Unresolved occurrences from now to the end of the 14th day, that have not ended. */
    public fun list(state: CalendarState, now: Instant): List<AgendaEntry> {
        val zone = state.zone
        val today = now.atZone(zone).toLocalDate()
        return Agenda.resolve(state, DateWindow(today, today.plusDays(DAYS - 1)))
            .filter { it.taking == Taking.NeedsSomeone }
            .filter { entry -> Instants.of(entry, zone)?.let { it.end > now } ?: (entry.lastDay >= today) }
    }
}

public enum class ReminderScope { MINE, EVERYONE, NONE }

/** Why a reminder is for this phone's owner (SPEC FR-39: "You're taking Ada"). */
public enum class ReminderRole { INVOLVED, TAKING, OTHER }

public data class DueReminder(val at: Instant, val entry: AgendaEntry, val role: ReminderRole, val reminder: Reminder)

public object ReminderPlan {
    private const val LEAD_DAYS: Long = 8

    /** Reminders due in `(after, until]` for a phone owned by [owner] (SPEC FR-36, FR-37), by instant. */
    public fun due(state: CalendarState, owner: MemberId?, scope: ReminderScope, after: Instant, until: Instant): List<DueReminder> {
        if (scope == ReminderScope.NONE || (scope == ReminderScope.MINE && owner == null) || until <= after) return emptyList()
        val zone = state.zone
        val window = DateWindow(after.atZone(zone).toLocalDate().minusDays(1), until.atZone(zone).toLocalDate().plusDays(LEAD_DAYS))
        return Agenda.resolve(state, window).flatMap { entry ->
            val role = roleOf(entry, owner)
            if (scope == ReminderScope.MINE && role == ReminderRole.OTHER) return@flatMap emptyList()
            entry.reminders.mapNotNull { reminder ->
                instantOf(reminder, entry, zone)?.takeIf { it > after && it <= until }?.let { DueReminder(it, entry, role, reminder) }
            }
        }.sortedWith(compareBy<DueReminder> { it.at }.then(compareBy(Agenda.ORDER) { it.entry }))
    }

    private fun roleOf(entry: AgendaEntry, owner: MemberId?): ReminderRole = when {
        owner == null -> ReminderRole.OTHER
        entry.taker == owner && !entry.involves(owner) -> ReminderRole.TAKING
        entry.involves(owner) -> ReminderRole.INVOLVED
        else -> ReminderRole.OTHER
    }

    private fun instantOf(reminder: Reminder, entry: AgendaEntry, zone: ZoneId): Instant? = when (reminder) {
        is Reminder.MinutesBefore -> Instants.of(entry, zone)?.start?.minusSeconds(reminder.minutes * SECONDS_PER_MINUTE)
        is Reminder.DaysBeforeAt -> Instants.at(entry.day.minusDays(reminder.days.toLong()).atTime(reminder.at), zone)
        is Reminder.Unrecognised -> null
    }

    private const val SECONDS_PER_MINUTE: Long = 60
}

/** An earlier event whose fields a new one can start from (SPEC FR-13). */
public data class Suggestion(
    val from: EventId,
    val title: String,
    val icon: IconKey,
    val who: Who,
    val slot: Slot,
    val place: String,
    val taking: Taking,
)

public object Suggestions {
    public const val LIMIT: Int = 5

    public fun forTitle(state: CalendarState, typed: String, limit: Int = LIMIT): List<Suggestion> {
        val prefix = typed.trim().lowercase(Locale.ROOT)
        if (prefix.isEmpty()) return emptyList()
        return state.events.entries
            .filter { (_, record) -> record.deleted?.value != true }
            .mapNotNull { (id, record) ->
                val title = record.title?.value ?: return@mapNotNull null
                val slot = record.slot?.value ?: return@mapNotNull null
                if (!title.lowercase(Locale.ROOT).startsWith(prefix)) return@mapNotNull null
                record.changed to Suggestion(
                    id, title, record.icon?.value ?: IconKey.NONE, record.who?.value ?: Who.Everyone, slot,
                    record.place?.value.orEmpty(), record.taking?.value ?: Taking.NotNeeded,
                )
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.title.lowercase(Locale.ROOT) }
            .take(limit)
    }
}
