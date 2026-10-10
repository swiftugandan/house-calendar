package dev.housecalendar.app.model

import dev.housecalendar.app.TestFamily
import dev.housecalendar.app.date
import dev.housecalendar.app.london
import dev.housecalendar.app.time
import dev.housecalendar.app.timed
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's home (SPEC FR-29, FR-33, FR-34; DESIGN.md "Agenda row"). */
class AgendaModelTest {
    private val family = TestFamily()
    private val swim = family.swimming()
    private val trip = family.event("School trip", "2026-10-13", Slot.AllDay(2), Who.Members(setOf(family.ada)))
    private val dentist = family.event("Dentist", "2026-10-15", timed("16:20", "17:00"), Who.Members(setOf(family.tom)), taking = Taking.NeedsSomeone)

    private fun agenda(now: String, filter: dev.housecalendar.contracts.MemberId? = null) =
        AgendaModel.build(family.state(), date(now.take(10)), days = 7, now = london(now), filter = filter)

    @Test
    fun `days with nothing on are collapsed, but today is always shown`() {
        family.event("Party", "2026-10-17", timed("15:00", "17:00"), Who.Members(setOf(family.tom)))
        val days = agenda("2026-10-12T09:00")
        assertEquals(listOf("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-17").map(::date), days.map { it.date })
        assertTrue(days.first().rows.isEmpty())
        assertEquals(DayWord.TODAY, days[0].word)
        assertEquals(DayWord.TOMORROW, days[1].word)
        assertEquals(DayWord.NONE, days[2].word)
    }

    @Test
    fun `all-day rows come first, and a multi-day event continues on its later days`() {
        val days = agenda("2026-10-13T09:00").associateBy { it.date }
        val tuesday = days.getValue(date("2026-10-13")).rows
        assertEquals(listOf(trip, swim), tuesday.map { it.key.event })
        assertTrue(tuesday.first().allDay)
        assertFalse(tuesday.first().continued)
        val wednesday = days.getValue(date("2026-10-14")).rows.single()
        assertEquals(trip, wednesday.key.event)
        assertTrue(wednesday.continued)
    }

    @Test
    fun `rows carry time, people, taking, place and the repeat mark`() {
        val row = agenda("2026-10-13T09:00").first().rows.single { it.key.event == swim }
        assertEquals(time("17:00"), row.start)
        assertEquals(time("18:00"), row.end)
        assertEquals(listOf("Ada"), row.people.map { it.name })
        assertEquals("A", row.people.single().initial)
        assertEquals(2, row.people.single().colorIndex)
        assertEquals(TakingMark.Person(PersonMark(family.sam, "Sam", "S", 0)), row.taking)
        assertEquals("Leisure Centre", row.place)
        assertTrue(row.repeating)
        val needs = agenda("2026-10-13T09:00").single { it.date == date("2026-10-15") }.rows.single()
        assertEquals(TakingMark.NeedsSomeone, needs.taking)
    }

    @Test
    fun `the now line sits before the first timed row that has not started, and ended rows are marked`() {
        val before = agenda("2026-10-13T16:30").first()
        assertEquals(1, before.nowLineBefore)
        assertFalse(before.rows[1].ended)
        val after = agenda("2026-10-13T18:30").first()
        assertEquals(2, after.nowLineBefore)
        assertTrue(after.rows[1].ended)
        assertEquals(null, agenda("2026-10-12T09:00")[1].nowLineBefore)
    }

    @Test
    fun `filtering to one person keeps what they are in or taking`() {
        assertEquals(setOf(swim), agenda("2026-10-13T09:00", filter = family.sam).flatMap { it.rows }.map { it.key.event }.toSet())
        assertEquals(setOf(dentist), agenda("2026-10-13T09:00", filter = family.tom).flatMap { it.rows }.map { it.key.event }.toSet())
    }

    @Test
    fun `clashes name the person and the other event`() {
        family.event("Football", "2026-10-13", timed("17:30", "18:30"), Who.Members(setOf(family.tom)), taking = Taking.Person(family.sam))
        val tuesday = agenda("2026-10-13T09:00").first().rows
        val swimClash = tuesday.single { it.key.event == swim }.clashes.single()
        assertEquals("Sam", swimClash.person.name)
        assertEquals("Football", swimClash.otherTitle)
        assertEquals(time("17:30"), swimClash.otherStart)
    }

    @Test
    fun `holidays are named on the days they cover`() {
        family.holiday("Half term", "2026-10-26", "2026-10-30")
        family.event("Zoo", "2026-10-27", Slot.AllDay(1), Who.Everyone)
        val zooDay = AgendaModel.build(family.state(), date("2026-10-26"), 7, london("2026-10-26T09:00")).single { it.date == date("2026-10-27") }
        assertEquals(listOf("Half term"), zooDay.holidays)
        assertTrue(zooDay.rows.single().everyone)
    }
}
