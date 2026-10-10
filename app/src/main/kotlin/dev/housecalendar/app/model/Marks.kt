package dev.housecalendar.app.model

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Taking
import dev.housecalendar.schedule.AgendaEntry
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Clash
import dev.housecalendar.schedule.Instants
import dev.housecalendar.schedule.MemberView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The view models emit structure (dates, times, ids, enums), never sentences: the words live in string
 * resources and the composables put them together (SPEC NFR-OPS-2).
 */
enum class DayWord { TODAY, TOMORROW, NONE }

/** A person as drawn: their disc (initial and colour) and name. */
data class PersonMark(val id: MemberId, val name: String, val initial: String, val colorIndex: Int)

/** Who is taking them (SPEC FR-11), as drawn: nothing, the empty disc, or a person. */
sealed interface TakingMark {
    data object None : TakingMark

    data object NeedsSomeone : TakingMark

    data class Person(val person: PersonMark) : TakingMark
}

/** "Sam is also down for Football at 17:30" (SPEC FR-34). */
data class ClashMark(val person: PersonMark, val otherTitle: String, val otherStart: LocalTime)

/** A device as shown in "Added by" (SPEC FR-18). */
data class DeviceLabel(val name: String, val isSelf: Boolean)

/** Lookups shared by the view models. */
internal class Marks(private val state: CalendarState) {
    private val members: Map<MemberId, MemberView> = state.allMembers().associateBy { it.id }

    fun person(id: MemberId): PersonMark? = members[id]?.let(::mark)

    fun people(ids: List<MemberId>): List<PersonMark> = ids.mapNotNull(::person)

    fun taking(taking: Taking): TakingMark = when (taking) {
        Taking.NeedsSomeone -> TakingMark.NeedsSomeone
        is Taking.Person -> person(taking.member)?.let(TakingMark::Person) ?: TakingMark.None
        Taking.NotNeeded, is Taking.Unrecognised -> TakingMark.None
    }

    fun device(id: DeviceId, self: DeviceId): DeviceLabel = DeviceLabel(state.devices[id]?.name?.value.orEmpty(), id == self)

    /** Clash marks for [entry], naming the other occurrence of each clash it is part of. */
    fun clashes(entry: AgendaEntry, clashes: List<Clash>): List<ClashMark> = clashes.mapNotNull { clash ->
        val other = when (entry.key) {
            clash.first.key -> clash.second
            clash.second.key -> clash.first
            else -> return@mapNotNull null
        }
        val person = person(clash.person) ?: return@mapNotNull null
        val start = Instants.of(other, state.zone)?.localStart?.toLocalTime() ?: return@mapNotNull null
        ClashMark(person, other.title, start)
    }

    companion object {
        fun mark(member: MemberView): PersonMark =
            PersonMark(member.id, member.name, member.name.trim().take(1).uppercase(java.util.Locale.ROOT), member.colorIndex)

        fun word(date: LocalDate, today: LocalDate): DayWord = when (date) {
            today -> DayWord.TODAY
            today.plusDays(1) -> DayWord.TOMORROW
            else -> DayWord.NONE
        }

        fun today(state: CalendarState, now: Instant): LocalDate = now.atZone(state.zone).toLocalDate()

        /** Whether a timed entry has ended by [now]; all-day entries end with their last day. */
        fun ended(entry: AgendaEntry, state: CalendarState, now: Instant): Boolean =
            Instants.of(entry, state.zone)?.let { it.end <= now } ?: (entry.lastDay < today(state, now))

        /** Whether [now] is inside a timed entry. */
        fun happening(entry: AgendaEntry, state: CalendarState, now: Instant): Boolean =
            Instants.of(entry, state.zone)?.let { now >= it.start && now < it.end } ?: false

        /** The entry's start on [day], if it is timed and starts that day (multi-day entries continue). */
        fun startOn(entry: AgendaEntry, day: LocalDate): LocalTime? =
            (entry.slot as? dev.housecalendar.contracts.Slot.Timed)?.takeIf { entry.day == day }?.start
    }
}
