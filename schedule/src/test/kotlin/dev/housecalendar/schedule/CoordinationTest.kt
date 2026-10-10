package dev.housecalendar.schedule

import dev.housecalendar.contracts.DeviceUpsert
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.FamilySettings
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.Owner
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Needs someone (FR-35), reminders (FR-36, FR-37), suggestions (FR-13), and projection details. */
class CoordinationTest {
    private val family = SpecFamily()

    @Test
    fun `needs someone lists the unresolved occurrences of the next 14 days that have not ended`() {
        val party = family.log.event("Party at Mia's", date("2026-10-17"), timed("15:00", "17:00"), Who.Members(setOf(family.tom)), taking = Taking.NeedsSomeone)
        family.log.event("Far away", date("2026-11-20"), timed("15:00", "17:00"), Who.Members(setOf(family.tom)), taking = Taking.NeedsSomeone)
        family.log.edit(EventEdit(family.swimming, occurrence = date("2026-10-13"), taking = Taking.NeedsSomeone))

        val now = Instant.parse("2026-10-13T16:30:00Z") // 17:30 BST, half way through that swim
        assertEquals(
            listOf(family.swimming to date("2026-10-13"), party to date("2026-10-17")),
            NeedsSomeone.list(family.log.state(), now).map { it.event to it.occurrence },
        )
        val later = Instant.parse("2026-10-13T17:00:01Z")
        assertEquals(listOf(party), NeedsSomeone.list(family.log.state(), later).map { it.event })
    }

    @Test
    fun `a phone is reminded of what its owner is in or taking, and nothing else in scope MINE`() {
        val dentist = family.log.event("Dentist", date("2026-10-15"), timed("16:20", "17:00"), Who.Members(setOf(family.ada)))
        family.log.upsert(EventUpsert(dentist, reminders = listOf(Reminder.MinutesBefore(60), Reminder.DaysBeforeAt(1, time("19:00")))))
        family.log.upsert(EventUpsert(family.swimming, reminders = listOf(Reminder.MinutesBefore(30))))
        val after = Instant.parse("2026-10-13T00:00:00Z")
        val until = Instant.parse("2026-10-16T00:00:00Z")
        val state = family.log.state()

        val sams = ReminderPlan.due(state, family.sam, ReminderScope.MINE, after, until)
        assertEquals(listOf(family.swimming), sams.map { it.entry.event })
        assertEquals(ReminderRole.TAKING, sams.single().role)

        val adas = ReminderPlan.due(state, family.ada, ReminderScope.MINE, after, until)
        assertEquals(
            listOf(
                Instant.parse("2026-10-13T15:30:00Z") to family.swimming,
                Instant.parse("2026-10-14T18:00:00Z") to dentist,
                Instant.parse("2026-10-15T14:20:00Z") to dentist,
            ),
            adas.map { it.at to it.entry.event },
        )
        assertTrue(adas.all { it.role == ReminderRole.INVOLVED })

        assertEquals(3, ReminderPlan.due(state, family.alex, ReminderScope.EVERYONE, after, until).size)
        assertEquals(0, ReminderPlan.due(state, family.alex, ReminderScope.MINE, after, until).size)
        assertEquals(0, ReminderPlan.due(state, family.ada, ReminderScope.NONE, after, until).size)
        assertEquals(0, ReminderPlan.due(state, null, ReminderScope.MINE, after, until).size)
    }

    @Test
    fun `all-day events ignore minutes-before reminders and honour days-before-at`() {
        val trip = family.log.event("School trip", date("2026-10-16"), Slot.AllDay(1), Who.Members(setOf(family.ada)))
        family.log.upsert(EventUpsert(trip, reminders = listOf(Reminder.MinutesBefore(30), Reminder.DaysBeforeAt(0, time("07:30")))))
        val due = ReminderPlan.due(family.log.state(), family.ada, ReminderScope.MINE, Instant.parse("2026-10-16T00:00:00Z"), Instant.parse("2026-10-17T00:00:00Z"))
        assertEquals(listOf(Instant.parse("2026-10-16T06:30:00Z")), due.map { it.at })
    }

    @Test
    fun `suggestions match the start of earlier titles, most recently changed first, one per title`() {
        family.log.event("Swim gala", date("2026-11-21"), Slot.AllDay(1), Who.Members(setOf(family.ada)))
        family.log.event("swimming", date("2026-12-01"), timed("09:00", "10:00"), Who.Everyone)
        family.log.event("Football", date("2026-12-02"), timed("09:00", "10:00"), Who.Everyone)
        val found = Suggestions.forTitle(family.log.state(), "SWIM")
        assertEquals(listOf("swimming", "Swim gala"), found.map { it.title })
        assertEquals(Who.Everyone, found.first().who)
        assertEquals(emptyList<Suggestion>(), Suggestions.forTitle(family.log.state(), " "))
    }

    @Test
    fun `the first occurrence and repeating flags`() {
        val swims = family.swims("2026-10-06", "2026-10-13")
        assertEquals(listOf(true, false), swims.map { it.first })
        assertTrue(swims.all { it.repeating })
        val dentist = family.log.event("Dentist", date("2026-10-15"), timed("16:20", "17:00"), Who.Members(setOf(family.ada)))
        val entry = family.agenda("2026-10-15", "2026-10-15").single { it.event == dentist }
        assertTrue(entry.first)
        assertFalse(entry.repeating)
    }

    @Test
    fun `an event missing a title, start or slot has no occurrences`() {
        val id = dev.housecalendar.contracts.EventId(family.log.uuid())
        family.log.upsert(EventUpsert(id, title = "Half written", start = date("2026-10-15")))
        assertTrue(family.agenda("2026-10-01", "2026-10-31").none { it.event == id })
    }

    @Test
    fun `values written by a newer version have a defined meaning here`() {
        val unknown = buildJsonObject { put("type", "fromTheFuture") }
        val oneOff = family.log.event("Gala", date("2026-11-21"), Slot.Unrecognised(unknown), Who.Everyone)
        assertTrue(family.agenda("2026-11-01", "2026-11-30").none { it.event == oneOff })

        family.log.upsert(EventUpsert(family.swimming, repeat = Repeat.Unrecognised(unknown)))
        val swims = family.swims("2026-10-01", "2026-12-31")
        assertEquals(listOf(date("2026-10-06")), swims.map { it.day })
        assertTrue(swims.single().repeatUnrecognised)

        family.log.upsert(EventUpsert(family.swimming, who = Who.Unrecognised(unknown), taking = Taking.Unrecognised(unknown)))
        val entry = family.swims("2026-10-01", "2026-10-31").single()
        assertEquals(listOf(family.sam, family.alex, family.ada, family.tom), entry.people)
        assertEquals(Taking.NotNeeded, entry.taking)
    }

    @Test
    fun `settings, devices and members are last-writer-wins per field`() {
        val log = family.log
        log.add(FamilySettings(zone = ZoneId.of("Africa/Kampala")), log.phoneA, lamport = 60)
        log.add(FamilySettings(name = "Okello-Smiths"), log.phoneB, lamport = 61)
        log.add(DeviceUpsert(log.display, name = "Kitchen", role = DeviceRole.Display, owner = Owner.NoOne))
        log.add(DeviceUpsert(log.phoneA, name = "Sam's phone", role = DeviceRole.Phone, owner = Owner.Member(family.sam)))
        val state = log.state()
        assertEquals(ZoneId.of("Africa/Kampala"), state.zone)
        assertEquals("Okello-Smiths", state.familyName)
        assertEquals(DeviceRole.Display, state.devices.getValue(log.display).role?.value)
        assertEquals(family.sam, state.ownerOf(log.phoneA))
        assertEquals(null, state.ownerOf(log.display))
        assertEquals(listOf(family.sam, family.alex, family.ada, family.tom), state.activeMembers().map { it.id })
    }

    @Test
    fun `without a family the zone falls back to UTC`() {
        assertEquals(ZoneId.of("UTC"), Projection.fold(emptyList()).zone)
    }

    @Test
    fun `icons and cleared text resolve to their values`() {
        family.log.upsert(EventUpsert(family.swimming, icon = IconKey("swim"), place = "Pool"))
        family.log.upsert(EventUpsert(family.swimming, place = ""))
        val entry = family.swims("2026-10-06", "2026-10-06").single()
        assertEquals(IconKey("swim"), entry.icon)
        assertEquals("", entry.place)
    }

    @Test
    fun `recently deleted is newest deletion first`() {
        val a = family.log.event("A", date("2026-10-10"), Slot.AllDay(1), Who.Everyone)
        val b = family.log.event("B", date("2026-10-10"), Slot.AllDay(1), Who.Everyone)
        family.log.upsert(EventUpsert(b, deleted = true), family.log.phoneB)
        family.log.upsert(EventUpsert(a, deleted = true), family.log.phoneA)
        assertEquals(listOf(a, b), family.log.state().recentlyDeleted().map { it.event })
        assertEquals("A", family.log.state().recentlyDeleted().first().title)
    }

    @Test
    fun `the event remembers which devices added and last changed it`() {
        family.log.upsert(EventUpsert(family.swimming, place = "Pool"), family.log.phoneB)
        val record = family.log.state().events.getValue(family.swimming)
        assertEquals(family.log.phoneA, record.created.device)
        assertEquals(family.log.phoneB, record.changed.device)
    }

}
