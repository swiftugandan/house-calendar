package dev.housecalendar.app.model

import dev.housecalendar.app.TestFamily
import dev.housecalendar.app.date
import dev.housecalendar.app.time
import dev.housecalendar.app.timed
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import dev.housecalendar.schedule.Changes
import dev.housecalendar.schedule.OccurrenceRef
import dev.housecalendar.schedule.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** The add sheet (SPEC FR-12, FR-13, FR-14, FR-22; DESIGN.md "Add sheet"). */
class DraftModelTest {
    private val family = TestFamily()
    private val blank = DraftModel.blank(viewedDay = date("2026-10-13"), now = LocalDateTime.parse("2026-10-10T14:20"))

    @Test
    fun `a new event starts at the next whole hour for an hour, on the day being viewed, with a 30-minute reminder`() {
        assertEquals(date("2026-10-13"), blank.day)
        assertEquals(time("15:00"), blank.start)
        assertEquals(time("16:00"), blank.end)
        assertFalse(blank.allDay)
        assertEquals(RepeatKind.NEVER, blank.repeatKind)
        assertEquals(listOf(Reminder.MinutesBefore(30)), blank.reminders)
        assertEquals(Who.Members(emptySet()), blank.who)
        assertEquals(Taking.NotNeeded, blank.taking)
    }

    @Test
    fun `late in the evening the next whole hour stays on the same day`() {
        val late = DraftModel.blank(date("2026-10-13"), LocalDateTime.parse("2026-10-13T23:10"))
        assertEquals(time("23:00"), late.start)
        assertEquals(time("23:59"), late.end)
    }

    @Test
    fun `typing a title suggests an icon until one is chosen`() {
        val swimming = DraftModel.withTitle(blank, "Swimming lessons")
        assertEquals(IconKey("swim"), swimming.icon)
        val chosen = DraftModel.withIcon(swimming, IconKey("star"))
        assertEquals(IconKey("star"), DraftModel.withTitle(chosen, "Dentist").icon)
    }

    @Test
    fun `weekly repeats default to the day's weekday and follow the day when it moves`() {
        val weekly = DraftModel.withRepeat(blank, RepeatKind.WEEKLY)
        assertEquals(setOf(DayOfWeek.TUESDAY), weekly.weekdays)
        val moved = DraftModel.withDay(weekly, date("2026-10-14"))
        assertEquals(setOf(DayOfWeek.WEDNESDAY), moved.weekdays)
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.WEDNESDAY)), DraftModel.repeatOf(moved))
        assertEquals(Repeat.Weekly(2, setOf(DayOfWeek.WEDNESDAY)), DraftModel.repeatOf(DraftModel.withRepeat(moved, RepeatKind.FORTNIGHTLY)))
    }

    @Test
    fun `monthly and yearly repeats come from the day`() {
        val day = DraftModel.withDay(blank, date("2026-10-30"))
        assertEquals(Repeat.MonthlyOnDay(1, 30), DraftModel.repeatOf(DraftModel.withRepeat(day, RepeatKind.MONTHLY_ON_DAY)))
        assertEquals(Repeat.MonthlyOnWeekday(1, -1, DayOfWeek.FRIDAY), DraftModel.repeatOf(DraftModel.withRepeat(day, RepeatKind.MONTHLY_ON_LAST_WEEKDAY)))
        assertEquals(Repeat.MonthlyOnWeekday(1, 2, DayOfWeek.TUESDAY), DraftModel.repeatOf(DraftModel.withRepeat(blank, RepeatKind.MONTHLY_ON_WEEKDAY)))
        assertEquals(Repeat.Yearly(1), DraftModel.repeatOf(DraftModel.withRepeat(day, RepeatKind.YEARLY)))
    }

    @Test
    fun `all day switches the slot and drops the default reminder`() {
        val allDay = DraftModel.withAllDay(blank, true)
        assertTrue(allDay.allDay)
        assertEquals(emptyList<Reminder>(), allDay.reminders)
        assertEquals(Slot.AllDay(1), DraftModel.slotOf(allDay))
        val twoDays = DraftModel.withEndDay(allDay, date("2026-10-14"))
        assertEquals(Slot.AllDay(2), DraftModel.slotOf(twoDays))
    }

    @Test
    fun `a slot that ends on a later day records the offset`() {
        val overnight = DraftModel.withTimes(blank, time("22:00"), time("06:00"))
        assertEquals(Slot.Timed(time("22:00"), time("06:00"), 1), DraftModel.slotOf(overnight))
    }

    @Test
    fun `a suggestion fills in who, time, place, taking and icon but keeps the day`() {
        val suggestion = Suggestion(
            dev.housecalendar.contracts.EventId(family.uuid()), "Swimming", IconKey("swim"), Who.Members(setOf(family.ada)),
            timed("17:00", "18:00"), "Leisure Centre", Taking.Person(family.sam),
        )
        val filled = DraftModel.applySuggestion(blank, suggestion)
        assertEquals("Swimming", filled.title)
        assertEquals(Who.Members(setOf(family.ada)), filled.who)
        assertEquals(time("17:00"), filled.start)
        assertEquals("Leisure Centre", filled.place)
        assertEquals(Taking.Person(family.sam), filled.taking)
        assertEquals(date("2026-10-13"), filled.day)
    }

    @Test
    fun `after N times becomes an until date, counting term time (SPEC Example 9)`() {
        family.holiday("Half term", "2026-10-26", "2026-10-30")
        val draft = DraftModel.withRepeat(DraftModel.withDay(DraftModel.withTitle(blank, "Swimming"), date("2026-10-06")), RepeatKind.WEEKLY)
            .copy(termTimeOnly = true, ends = Ends.After(10), who = Who.Members(setOf(family.ada)))
        val event = DraftModel.toEventDraft(draft, family.state())
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY), until = date("2026-12-15")), event.repeat)
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY), until = date("2026-11-30")),
            DraftModel.toEventDraft(draft.copy(ends = Ends.On(date("2026-11-30"))), family.state()).repeat)
    }

    @Test
    fun `the outcome describes what the button will do`() {
        val once = DraftModel.withTitle(blank, "Dentist")
        assertEquals(Outcome("Dentist", date("2026-10-13"), RepeatKind.NEVER, emptySet(), time("15:00")), DraftModel.outcome(once))
        val weekly = DraftModel.withRepeat(DraftModel.withAllDay(once, true), RepeatKind.WEEKLY)
        assertEquals(Outcome("Dentist", date("2026-10-13"), RepeatKind.WEEKLY, setOf(DayOfWeek.TUESDAY), null), DraftModel.outcome(weekly))
    }

    @Test
    fun `a draft shows the clash it would create`() {
        family.swimming()
        val draft = DraftModel.withTitle(DraftModel.withTimes(blank, time("17:30"), time("18:30")), "Football")
            .copy(who = Who.Members(setOf(family.tom)), taking = Taking.Person(family.sam))
        val clashes = DraftModel.clashes(draft, family.state())
        assertEquals(listOf(family.sam), clashes.map { it.person })
        assertEquals("Swimming", clashes.single().other.title)
    }

    @Test
    fun `editing starts from the occurrence and reports only what changed`() {
        val swim = family.swimming()
        val state = family.state()
        val original = requireNotNull(DraftModel.fromOccurrence(state, OccurrenceRef(swim, date("2026-10-20"))))
        assertEquals("Swimming", original.title)
        assertEquals(date("2026-10-20"), original.day)
        assertEquals(RepeatKind.WEEKLY, original.repeatKind)
        assertTrue(original.termTimeOnly)
        val edited = DraftModel.withTimes(original.copy(place = "Pool"), time("17:30"), time("18:30"))
        assertEquals(Changes(slot = timed("17:30", "18:30"), place = "Pool"), DraftModel.changes(original, edited))
        assertEquals(Changes(day = date("2026-10-21")), DraftModel.changes(original, DraftModel.withDay(original, date("2026-10-21")).copy(weekdays = original.weekdays)))
    }

    @Test
    fun `the draft needs a title and someone before it can be added`() {
        assertFalse(DraftModel.canAdd(blank, family.state()))
        assertFalse(DraftModel.canAdd(DraftModel.withTitle(blank, "Dentist"), family.state()))
        assertTrue(DraftModel.canAdd(DraftModel.withTitle(blank, "Dentist").copy(who = Who.Everyone), family.state()))
    }
}
