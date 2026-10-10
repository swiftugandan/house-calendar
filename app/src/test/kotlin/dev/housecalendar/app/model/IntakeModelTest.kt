package dev.housecalendar.app.model

import dev.housecalendar.app.TestFamily
import dev.housecalendar.app.date
import dev.housecalendar.app.time
import dev.housecalendar.app.timed
import dev.housecalendar.contracts.CalendarIds
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Who
import dev.housecalendar.intake.IncomingDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** SPEC FR-49 to FR-55 in the app: drafts from other apps into the add sheet, the import review's payloads. */
class IntakeModelTest {
    private val family = TestFamily()
    private val now = LocalDateTime.parse("2026-10-10T14:20")

    @Test
    fun `an incoming draft fills the add sheet, keeping what it doesn't say from the blank sheet`() {
        val incoming = IncomingDraft("Swimming lessons", day = date("2026-10-13"), slot = timed("17:00", "18:00"), place = "Leisure Centre")
        val draft = IntakeModel.toDraft(incoming, now)
        assertEquals("Swimming lessons", draft.title)
        assertEquals(IconKey("swim"), draft.icon)
        assertEquals(date("2026-10-13"), draft.day)
        assertEquals(time("17:00"), draft.start)
        assertEquals("Leisure Centre", draft.place)
        assertEquals(Who.Members(emptySet()), draft.who)
        val bare = IntakeModel.toDraft(IncomingDraft("Forms"), now)
        assertEquals(date("2026-10-10"), bare.day)
        assertEquals(time("15:00"), bare.start)
    }

    @Test
    fun `a counted rule becomes after N times and an until becomes ends on`() {
        val counted = IntakeModel.toDraft(IncomingDraft("Swim", day = date("2026-10-06"), slot = timed("17:00", "18:00"), repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY)), count = 10), now)
        assertEquals(RepeatKind.WEEKLY, counted.repeatKind)
        assertEquals(Ends.After(10), counted.ends)
        val until = IntakeModel.toDraft(IncomingDraft("Swim", day = date("2026-10-06"), slot = timed("17:00", "18:00"), repeat = Repeat.Weekly(2, setOf(DayOfWeek.TUESDAY), until = date("2026-12-15"))), now)
        assertEquals(RepeatKind.FORTNIGHTLY, until.repeatKind)
        assertEquals(Ends.On(date("2026-12-15")), until.ends)
    }

    @Test
    fun `the review records events for everyone, with ids from UIDs and EXDATEs as cancelled occurrences`() {
        val items = listOf(
            IncomingDraft("Book fair", day = date("2026-10-15"), slot = Slot.AllDay(1), uid = "fair@school"),
            IncomingDraft("Club", day = date("2026-10-06"), slot = timed("16:00", "17:00"), repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY)), count = 3, exdates = listOf(date("2026-10-13"))),
        )
        val payloads = IntakeModel.eventPayloads(items, family.state()) { CalendarIds.randomEvent(1) }
        val fair = payloads.filterIsInstance<EventUpsert>().first()
        assertEquals(CalendarIds.externalEvent("fair@school"), fair.eventId)
        assertEquals(Who.Everyone, fair.who)
        val club = payloads.filterIsInstance<EventUpsert>()[1]
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY), until = date("2026-10-20")), club.repeat)
        assertEquals(listOf(EventEdit(club.eventId, occurrence = date("2026-10-13"), cancelled = true)), payloads.filterIsInstance<EventEdit>())
    }

    @Test
    fun `all-day items can be added as holidays, with ids from UIDs`() {
        val items = listOf(IncomingDraft("Christmas holidays", day = date("2026-12-21"), slot = Slot.AllDay(14), uid = "hols-1@school"))
        assertTrue(IntakeModel.canBeHolidays(items))
        assertFalse(IntakeModel.canBeHolidays(items + IncomingDraft("Play", day = date("2026-12-10"), slot = timed("18:00", "19:00"))))
        assertEquals(
            listOf(HolidayUpsert(CalendarIds.externalHoliday("hols-1@school"), name = "Christmas holidays", first = date("2026-12-21"), last = date("2027-01-03"), deleted = false)),
            IntakeModel.holidayPayloads(items) { CalendarIds.randomHoliday(1) },
        )
    }

    @Test
    fun `items already on the calendar are recognised by their UID (SPEC FR-54)`() {
        family.add(EventUpsert(CalendarIds.externalEvent("fair@school"), title = "Book fair", start = date("2026-10-15"), slot = Slot.AllDay(1), who = Who.Everyone))
        val state = family.state()
        assertTrue(IntakeModel.alreadyOnCalendar(IncomingDraft("Book fair", uid = "fair@school"), state))
        assertFalse(IntakeModel.alreadyOnCalendar(IncomingDraft("Book fair", uid = "other@school"), state))
        assertFalse(IntakeModel.alreadyOnCalendar(IncomingDraft("Book fair"), state))
    }
}

/** Raw input from other apps is converted only where the family zone is known (CLAUDE.md: family time). */
class IntakeResolveTest {
    private val zone = java.time.ZoneId.of("Europe/London")
    private val now = LocalDateTime.parse("2026-10-10T14:20")

    @Test
    fun `an insert intent converts its instants in the family zone, not the device's`() {
        val extras = dev.housecalendar.intake.InsertExtras("Parents evening", 1_791_651_600_000, 1_791_654_000_000, false, null, null, null)
        val arrival = IntakeModel.resolve(Incoming.Insert(extras), java.time.ZoneId.of("Asia/Tokyo"), now)
        val draft = (arrival as Arrival.One).draft
        assertEquals(date("2026-10-11"), draft.day)
        assertEquals(timed("03:00", "03:40"), draft.slot)
    }

    @Test
    fun `a file with one event opens the sheet, several go to the review, and a refusal is passed on`() {
        fun ics(vararg events: String) = ("BEGIN:VCALENDAR\r\n" + events.joinToString("") { "BEGIN:VEVENT\r\nUID:$it\r\nDTSTART;VALUE=DATE:20261102\r\nSUMMARY:$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n").toByteArray()
        assertTrue(IntakeModel.resolve(Incoming.File(ics("a")), zone, now) is Arrival.One)
        assertEquals(2, (IntakeModel.resolve(Incoming.File(ics("a", "b")), zone, now) as Arrival.Many).result.drafts.size)
        assertEquals(Arrival.Refused(dev.housecalendar.intake.Refusal.NOT_A_CALENDAR), IntakeModel.resolve(Incoming.File("hello".toByteArray()), zone, now))
    }

    @Test
    fun `shared text reads its date relative to now in family time`() {
        val draft = (IntakeModel.resolve(Incoming.Text("Party at Mia's, Sat 3pm"), zone, now) as Arrival.One).draft
        assertEquals(date("2026-10-10"), draft.day)
        assertEquals(time("15:00"), (draft.slot as Slot.Timed).start)
    }
}
