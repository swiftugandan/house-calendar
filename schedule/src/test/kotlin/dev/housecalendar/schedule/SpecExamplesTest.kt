package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime

/** SPEC "Worked examples" 1 to 12, as written. Each is an acceptance test (NFR-DET-3). */
class SpecExamplesTest {
    @Test
    fun `example 1 - weekly, term time only`() {
        val family = SpecFamily()
        assertEquals(
            listOf("2026-10-06", "2026-10-13", "2026-10-20", "2026-11-03", "2026-11-10").map(::date),
            family.swims("2026-10-06", "2026-11-10").map { it.day },
        )

        val halfTerm = requireNotNull(family.halfTerm)
        family.log.add(HolidayUpsert(halfTerm, deleted = true))
        assertTrue(date("2026-10-27") in family.swims("2026-10-06", "2026-11-10").map { it.day })
    }

    @Test
    fun `example 2 - daylight saving`() {
        val family = SpecFamily()
        family.log.upsert(EventUpsert(family.swimming, reminders = listOf(Reminder.MinutesBefore(30))))
        val swims = family.swims("2026-10-20", "2026-11-03").associateBy { it.day }

        val oct20 = requireNotNull(Instants.of(requireNotNull(swims[date("2026-10-20")]), LONDON))
        val nov3 = requireNotNull(Instants.of(requireNotNull(swims[date("2026-11-03")]), LONDON))
        assertEquals(Instant.parse("2026-10-20T16:00:00Z"), oct20.start)
        assertEquals(Instant.parse("2026-11-03T17:00:00Z"), nov3.start)
        assertEquals(LocalDateTime.parse("2026-10-20T17:00"), oct20.localStart)
        assertEquals(LocalDateTime.parse("2026-11-03T17:00"), nov3.localStart)

        val due = ReminderPlan.due(
            family.log.state(), owner = family.sam, scope = ReminderScope.MINE,
            after = Instant.parse("2026-10-19T00:00:00Z"), until = Instant.parse("2026-11-04T00:00:00Z"),
        )
        assertEquals(
            listOf("2026-10-20T15:30:00Z", "2026-11-03T16:30:00Z").map(Instant::parse),
            due.filter { it.entry.event == family.swimming }.map { it.at },
        )

        val repeated = family.log.event("Late film", date("2026-10-25"), timed("01:30", "02:00"), Who.Everyone)
        val skipped = family.log.event("Early run", date("2026-03-29"), timed("01:30", "02:15"), Who.Everyone)
        val state = family.log.state()
        val film = Agenda.resolve(state, DateWindow(date("2026-10-25"), date("2026-10-25"))).single { it.event == repeated }
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), Instants.of(film, LONDON)?.start)
        val run = Agenda.resolve(state, DateWindow(date("2026-03-29"), date("2026-03-29"))).single { it.event == skipped }
        val runInstants = requireNotNull(Instants.of(run, LONDON))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), runInstants.start)
        assertEquals(LocalDateTime.parse("2026-03-29T02:30"), runInstants.localStart)
    }

    @Test
    fun `example 3 - only this one, and this and following, while apart`() {
        val family = SpecFamily()
        val log = family.log
        log.edit(EventEdit(family.swimming, occurrence = date("2026-10-20"), slot = timed("18:00", "19:00")), log.phoneA, lamport = 50)
        log.change(EventChange(family.swimming, from = date("2026-10-13"), place = "Leisure Centre"), log.phoneB, lamport = 50)

        val swims = family.swims("2026-10-06", "2026-11-10").associateBy { it.day }
        assertEquals(timed("17:00", "18:00"), swims[date("2026-10-06")]?.slot)
        assertEquals("", swims[date("2026-10-06")]?.place)
        assertEquals("Leisure Centre", swims[date("2026-10-13")]?.place)
        assertEquals(timed("18:00", "19:00"), swims[date("2026-10-20")]?.slot)
        assertEquals("Leisure Centre", swims[date("2026-10-20")]?.place)
        assertEquals(timed("17:00", "18:00"), swims[date("2026-11-03")]?.slot)
        assertEquals("Leisure Centre", swims[date("2026-11-10")]?.place)
    }

    @Test
    fun `example 4 - the same field changed on two phones`() {
        val family = SpecFamily()
        family.log.upsert(EventUpsert(family.swimming, title = "Swim club"), family.log.phoneA, lamport = 40)
        family.log.upsert(EventUpsert(family.swimming, title = "Swimming lessons"), family.log.phoneB, lamport = 42)
        assertEquals(setOf("Swimming lessons"), family.swims("2026-10-06", "2026-11-10").map { it.title }.toSet())
    }

    @Test
    fun `example 5 - two this-and-following changes at different dates`() {
        val family = SpecFamily()
        val log = family.log
        log.change(EventChange(family.swimming, from = date("2026-10-20"), slot = timed("17:30", "18:30")), log.phoneA, lamport = 50)
        log.change(EventChange(family.swimming, from = date("2026-11-10"), who = Who.Members(setOf(family.ada, family.tom))), log.phoneB, lamport = 50)

        val swims = family.swims("2026-10-06", "2026-11-17")
        assertEquals(swims.map { it.day }.distinct(), swims.map { it.day })
        val byDay = swims.associateBy { it.day }
        assertEquals(timed("17:00", "18:00"), byDay[date("2026-10-13")]?.slot)
        assertEquals(listOf(family.ada), byDay[date("2026-10-13")]?.people)
        assertEquals(timed("17:30", "18:30"), byDay[date("2026-10-20")]?.slot)
        assertEquals(timed("17:30", "18:30"), byDay[date("2026-11-03")]?.slot)
        assertEquals(listOf(family.ada), byDay[date("2026-11-03")]?.people)
        assertEquals(timed("17:30", "18:30"), byDay[date("2026-11-10")]?.slot)
        assertEquals(listOf(family.ada, family.tom), byDay[date("2026-11-10")]?.people)
    }

    @Test
    fun `example 6 - delete on one phone, edit on the other`() {
        val family = SpecFamily()
        val log = family.log
        log.upsert(EventUpsert(family.swimming, deleted = true), log.phoneA, lamport = 50)
        log.change(EventChange(family.swimming, from = date("2026-10-13"), place = "Pool"), log.phoneB, lamport = 50)

        assertEquals(emptyList<AgendaEntry>(), family.swims("2026-10-06", "2026-11-10"))
        val deleted = log.state().recentlyDeleted().single()
        assertEquals(family.swimming, deleted.event)
        assertEquals(log.phoneA, deleted.by)

        log.upsert(EditPlanner.restore(family.swimming), log.display)
        val restored = family.swims("2026-10-06", "2026-11-10")
        assertEquals("", restored.first().place)
        assertEquals("Pool", restored.single { it.day == date("2026-10-13") }.place)
    }

    @Test
    fun `example 7 - a holiday added after an occurrence edit`() {
        val family = SpecFamily(withHalfTerm = false)
        val log = family.log
        log.edit(EventEdit(family.swimming, occurrence = date("2026-10-27"), slot = timed("16:00", "17:00")))
        val halfTerm = log.holiday("Half term", date("2026-10-26"), date("2026-10-30"), log.phoneB)

        assertTrue(family.swims("2026-10-26", "2026-11-01").isEmpty())
        log.add(HolidayUpsert(halfTerm, deleted = true))
        assertEquals(timed("16:00", "17:00"), family.swims("2026-10-26", "2026-11-01").single().slot)
    }

    @Test
    fun `example 8 - monthly on the 31st, and the last Friday`() {
        val family = SpecFamily()
        val bins = family.log.event("Bins", date("2026-10-31"), Slot.AllDay(1), Who.Everyone, repeat = Repeat.MonthlyOnDay(1, 31))
        val books = family.log.event(
            "Book club", date("2026-10-30"), timed("19:30", "21:00"), Who.Members(setOf(family.alex)),
            repeat = Repeat.MonthlyOnWeekday(1, -1, DayOfWeek.FRIDAY),
        )
        val agenda = family.agenda("2026-10-01", "2027-05-31")
        assertEquals(
            listOf("2026-10-31", "2026-12-31", "2027-01-31", "2027-03-31", "2027-05-31").map(::date),
            agenda.filter { it.event == bins }.map { it.day },
        )
        assertEquals(
            listOf("2026-10-30", "2026-11-27", "2026-12-25").map(::date),
            agenda.filter { it.event == books && it.day < date("2027-01-01") }.map { it.day },
        )
    }

    @Test
    fun `example 9 - after 10 times`() {
        val family = SpecFamily()
        val until = Recording.untilForCount(
            family.log.state(), start = date("2026-10-06"), repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY)),
            termTimeOnly = true, count = 10,
        )
        assertEquals(date("2026-12-15"), until)

        family.log.upsert(EventUpsert(family.swimming, repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY), until = until)))
        family.log.holiday("Inset", date("2026-12-08"), date("2026-12-08"))
        assertEquals(
            listOf("2026-10-06", "2026-10-13", "2026-10-20", "2026-11-03", "2026-11-10", "2026-11-17", "2026-11-24", "2026-12-01", "2026-12-15").map(::date),
            family.swims("2026-10-01", "2027-01-31").map { it.day },
        )
    }

    @Test
    fun `example 10 - a clash`() {
        val family = SpecFamily()
        val football = family.log.event(
            "Football", date("2026-10-20"), timed("17:30", "18:30"), Who.Members(setOf(family.tom)), taking = Taking.Person(family.sam),
        )
        val day = family.agenda("2026-10-20", "2026-10-20")
        val clashes = Clashes.find(day, LONDON)
        assertEquals(1, clashes.size)
        val clash = clashes.single()
        assertEquals(family.sam, clash.person)
        assertEquals(setOf(family.swimming, football), setOf(clash.first.event, clash.second.event))

        family.log.upsert(EventUpsert(football, taking = Taking.Person(family.alex)))
        assertEquals(emptyList<Clash>(), Clashes.find(family.agenda("2026-10-20", "2026-10-20"), LONDON))

        family.log.event("Family dinner", date("2026-10-20"), timed("17:45", "18:45"), Who.Everyone)
        val withDinner = Clashes.find(family.agenda("2026-10-20", "2026-10-20"), LONDON)
        assertEquals(setOf(family.ada, family.tom, family.sam, family.alex), withDinner.map { it.person }.toSet())
    }

    @Test
    fun `example 11 - everyone, with a new member`() {
        val family = SpecFamily()
        val visit = family.log.event("Grandma visits", date("2026-11-07"), Slot.AllDay(1), Who.Everyone)
        val football = family.log.event("Football", date("2026-11-07"), timed("10:00", "11:00"), Who.Members(setOf(family.tom)))
        fun on7th() = family.agenda("2026-11-07", "2026-11-07")

        assertEquals(listOf(family.sam, family.alex, family.ada, family.tom), on7th().single { it.event == visit }.people)
        val bea = family.log.member("Bea", MemberKind.Child, 4)
        assertEquals(listOf(family.sam, family.alex, family.ada, family.tom, bea), on7th().single { it.event == visit }.people)

        family.log.add(MemberUpsert(family.tom, archived = true))
        assertEquals(listOf(family.sam, family.alex, family.ada, bea), on7th().single { it.event == visit }.people)
        assertEquals(listOf(family.tom), on7th().single { it.event == football }.people)
    }

    @Test
    fun `example 12 - moved across the window edge`() {
        val family = SpecFamily()
        family.log.edit(EventEdit(family.swimming, occurrence = date("2026-11-03"), movedTo = date("2026-10-31")))

        val moved = family.swims("2026-10-26", "2026-11-01").single()
        assertEquals(date("2026-10-31"), moved.day)
        assertEquals(date("2026-11-03"), moved.occurrence)
        assertTrue(moved.moved)
        assertTrue(family.swims("2026-11-02", "2026-11-08").none { it.occurrence == date("2026-11-03") })
    }
}
