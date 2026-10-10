package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Who
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

/** SPEC FR-15 and FR-16: each scope is recorded as exactly the payloads the spec names. */
class EditPlannerTest {
    private val family = SpecFamily()
    private val swim = family.swimming

    private fun plan(occurrence: String, scope: Scope, changes: Changes): Plan =
        EditPlanner.change(family.log.state(), OccurrenceRef(swim, date(occurrence)), scope, changes)

    private fun recorded(plan: Plan): List<dev.housecalendar.contracts.Payload> = (plan as Plan.Record).payloads

    private fun apply(plan: Plan) = recorded(plan).forEach { family.log.add(it) }

    @Test
    fun `only this one is an occurrence edit, and a new day is movedTo`() {
        val payloads = recorded(plan("2026-10-20", Scope.ONLY, Changes(day = date("2026-10-21"), place = "Pool")))
        assertEquals(listOf(EventEdit(swim, occurrence = date("2026-10-20"), movedTo = date("2026-10-21"), place = "Pool")), payloads)
    }

    @Test
    fun `this and following is a change from the occurrence's original date`() {
        val payloads = recorded(plan("2026-10-20", Scope.FOLLOWING, Changes(slot = timed("17:30", "18:30"))))
        assertEquals(listOf(EventChange(swim, from = date("2026-10-20"), slot = timed("17:30", "18:30"))), payloads)
    }

    @Test
    fun `this and following from the first occurrence is recorded as all`() {
        val payloads = recorded(plan("2026-10-06", Scope.FOLLOWING, Changes(title = "Swim club")))
        assertEquals(listOf(EventUpsert(swim, title = "Swim club")), payloads)
    }

    @Test
    fun `all writes the event and every change from that sets the same field`() {
        family.log.change(EventChange(swim, from = date("2026-11-03"), place = "New pool", notes = "Bring a lock"))
        family.log.change(EventChange(swim, from = date("2026-11-17"), notes = "Kit bag"))
        val payloads = recorded(plan("2026-10-13", Scope.ALL, Changes(place = "Leisure Centre")))
        assertEquals(
            listOf(EventUpsert(swim, place = "Leisure Centre"), EventChange(swim, from = date("2026-11-03"), place = "Leisure Centre")),
            payloads,
        )
    }

    @Test
    fun `a non-repeating event is always updated as a whole, and a new day moves its start`() {
        val dentist = family.log.event("Dentist", date("2026-10-15"), timed("16:20", "17:00"), Who.Members(setOf(family.ada)))
        val plan = EditPlanner.change(family.log.state(), OccurrenceRef(dentist, date("2026-10-15")), Scope.ONLY, Changes(day = date("2026-10-16")))
        assertEquals(listOf(EventUpsert(dentist, start = date("2026-10-16"))), recorded(plan))
    }

    @Test
    fun `moving all to another weekday shifts the start and the rule`() {
        apply(plan("2026-10-13", Scope.ALL, Changes(day = date("2026-10-14"))))
        val days = family.swims("2026-10-01", "2026-11-11").map { it.day }
        assertEquals(listOf("2026-10-07", "2026-10-14", "2026-10-21", "2026-11-04", "2026-11-11").map(::date), days)
    }

    @Test
    fun `moving this and following to a later weekday shifts the rule from that date`() {
        apply(plan("2026-10-20", Scope.FOLLOWING, Changes(day = date("2026-10-21"))))
        val days = family.swims("2026-10-01", "2026-11-11").map { it.day }
        assertEquals(listOf("2026-10-06", "2026-10-13", "2026-10-21", "2026-11-04", "2026-11-11").map(::date), days)
        val change = family.log.state().changes.getValue(swim).values.single()
        assertEquals(Repeat.Weekly(1, setOf(WEDNESDAY)), change.repeat?.value)
    }

    @Test
    fun `moving this and following to an earlier weekday starts the change on the new day`() {
        apply(plan("2026-10-20", Scope.FOLLOWING, Changes(day = date("2026-10-19"))))
        val days = family.swims("2026-10-01", "2026-11-10").map { it.day }
        assertEquals(listOf("2026-10-06", "2026-10-13", "2026-10-19", "2026-11-02", "2026-11-09").map(::date), days)
        assertEquals(Repeat.Weekly(1, setOf(MONDAY)), family.log.state().changes.getValue(swim).values.single().repeat?.value)
    }

    @Test
    fun `this and following cannot move the day of a rule that is not weekly`() {
        val bins = family.log.event("Bins", date("2026-10-31"), Slot.AllDay(1), Who.Everyone, repeat = Repeat.MonthlyOnDay(1, 31))
        val plan = EditPlanner.change(family.log.state(), OccurrenceRef(bins, date("2026-12-31")), Scope.FOLLOWING, Changes(day = date("2026-12-30")))
        assertEquals(Plan.Refused(PlanRefusal.MOVE_NEEDS_WEEKLY_RULE), plan)
    }

    @Test
    fun `only this one cannot change what belongs to the series`() {
        assertEquals(Plan.Refused(PlanRefusal.SERIES_FIELD_IN_SINGLE_EDIT),
            plan("2026-10-20", Scope.ONLY, Changes(reminders = listOf(Reminder.MinutesBefore(10)))))
        assertEquals(Plan.Refused(PlanRefusal.SERIES_FIELD_IN_SINGLE_EDIT),
            plan("2026-10-20", Scope.ONLY, Changes(repeat = Repeat.Weekly(1, setOf(TUESDAY)), termTimeOnly = false)))
    }

    @Test
    fun `a date that is not an occurrence cannot be edited`() {
        assertEquals(Plan.Refused(PlanRefusal.NOT_AN_OCCURRENCE), plan("2026-10-27", Scope.ONLY, Changes(title = "x")))
        assertEquals(Plan.Refused(PlanRefusal.NOT_AN_OCCURRENCE), plan("2026-10-21", Scope.ONLY, Changes(title = "x")))
    }

    @Test
    fun `deleting in each scope`() {
        val state = family.log.state()
        fun delete(day: String, scope: Scope) = EditPlanner.delete(state, OccurrenceRef(swim, date(day)), scope)
        assertEquals(Plan.Record(listOf(EventEdit(swim, occurrence = date("2026-10-20"), cancelled = true))), delete("2026-10-20", Scope.ONLY))
        assertEquals(Plan.Record(listOf(EventChange(swim, from = date("2026-10-20"), ended = true))), delete("2026-10-20", Scope.FOLLOWING))
        assertEquals(Plan.Record(listOf(EventUpsert(swim, deleted = true))), delete("2026-10-06", Scope.FOLLOWING))
        assertEquals(Plan.Record(listOf(EventUpsert(swim, deleted = true))), delete("2026-10-20", Scope.ALL))
    }

    @Test
    fun `ending this and following leaves the earlier occurrences`() {
        family.log.add(recorded(EditPlanner.delete(family.log.state(), OccurrenceRef(swim, date("2026-11-03")), Scope.FOLLOWING)).single())
        assertEquals(listOf("2026-10-06", "2026-10-13", "2026-10-20").map(::date), family.swims("2026-10-01", "2026-12-31").map { it.day })
    }

    @Test
    fun `restoring writes deleted false`() {
        assertEquals(EventUpsert(swim, deleted = false), EditPlanner.restore(swim))
        assertTrue(family.log.state().recentlyDeleted().isEmpty())
    }
}
