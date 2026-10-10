package dev.housecalendar.app.model

import dev.housecalendar.app.TestFamily
import dev.housecalendar.app.date
import dev.housecalendar.app.london
import dev.housecalendar.app.time
import dev.housecalendar.app.timed
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Who
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

/** The display's views (SPEC FR-31, FR-32; DESIGN.md "Organiser grid", "Today board") and the month grid. */
class BoardModelsTest {
    private val family = TestFamily()
    private val swim = family.swimming()

    @Test
    fun `the organiser has a column per member and starts today`() {
        val organiser = OrganiserModel.build(family.state(), london("2026-10-12T08:00"))
        assertEquals(OrganiserLayout.COLUMNS, organiser.layout)
        assertEquals(listOf("Sam", "Alex", "Ada", "Tom"), organiser.columns.map { it.name })
        assertEquals(7, organiser.days.size)
        assertEquals(date("2026-10-12"), organiser.days.first().date)
        assertEquals(DayWord.TODAY, organiser.days.first().word)
    }

    @Test
    fun `an occurrence is in the column of each person involved, and in the taker's column as taking`() {
        val tuesday = OrganiserModel.build(family.state(), london("2026-10-12T08:00")).days[1]
        val ada = tuesday.cells[2].single()
        assertEquals(swim, ada.key.event)
        assertEquals(CellRole.INVOLVED, ada.role)
        val sam = tuesday.cells[0].single()
        assertEquals(swim, sam.key.event)
        assertEquals(CellRole.TAKING, sam.role)
        assertTrue(tuesday.cells[1].isEmpty())
    }

    @Test
    fun `other people involved are marked on each column's entry, and everyone events go in the band`() {
        family.event("Cinema", "2026-10-13", timed("19:00", "21:00"), Who.Members(setOf(family.ada, family.tom)))
        family.event("Family dinner", "2026-10-13", timed("18:00", "19:00"), Who.Everyone)
        val tuesday = OrganiserModel.build(family.state(), london("2026-10-12T08:00")).days[1]
        val adaCinema = tuesday.cells[2].single { it.title == "Cinema" }
        assertEquals(listOf("Tom"), adaCinema.others.map { it.name })
        assertEquals(listOf("Family dinner"), tuesday.everyone.map { it.title })
        assertTrue(tuesday.cells.flatten().none { it.title == "Family dinner" })
    }

    @Test
    fun `five or six members see five days, and more than six become one list per day`() {
        family.member("Bea", MemberKind.Child, 4)
        val five = OrganiserModel.build(family.state(), london("2026-10-12T08:00"))
        assertEquals(5, five.days.size)
        assertEquals(OrganiserLayout.COLUMNS, five.layout)
        family.member("Cal", MemberKind.Child, 5)
        family.member("Dee", MemberKind.Adult, 6)
        val seven = OrganiserModel.build(family.state(), london("2026-10-12T08:00"))
        assertEquals(OrganiserLayout.LIST, seven.layout)
        assertEquals(7, seven.days.size)
        assertEquals(listOf(swim), seven.days[1].list.map { it.key.event })
    }

    @Test
    fun `what is happening now is marked, and what has ended is too`() {
        family.event("Breakfast club", "2026-10-13", timed("07:30", "08:30"), Who.Members(setOf(family.ada)))
        val today = OrganiserModel.build(family.state(), london("2026-10-13T17:15")).days.first()
        val ada = today.cells[2]
        assertTrue(ada.single { it.title == "Breakfast club" }.ended)
        assertTrue(ada.single { it.key.event == swim }.now)
    }

    @Test
    fun `the today board gives each member now and next`() {
        family.event("Dentist", "2026-10-15", timed("16:20", "17:00"), Who.Members(setOf(family.ada)))
        val board = TodayBoardModel.build(family.state(), london("2026-10-13T17:15"))
        val ada = board.single { it.person?.name == "Ada" }
        assertEquals("Swimming", ada.now?.title)
        assertEquals("Dentist", ada.next?.title)
        assertEquals(date("2026-10-15"), ada.next?.date)
        val sam = board.single { it.person?.name == "Sam" }
        assertEquals("Swimming", sam.now?.title)
        val alex = board.single { it.person?.name == "Alex" }
        assertEquals(null, alex.now)
    }

    @Test
    fun `an everyone row leads the board when an everyone event is now or next today`() {
        family.event("Family dinner", "2026-10-13", timed("18:30", "19:30"), Who.Everyone)
        val board = TodayBoardModel.build(family.state(), london("2026-10-13T12:00"))
        assertEquals(null, board.first().person)
        assertEquals("Family dinner", board.first().next?.title)
        assertEquals(time("18:30"), board.first().next?.start)
    }

    @Test
    fun `the month grid has whole weeks from the family's week start, with people marks and up to three titles`() {
        listOf("Bake sale", "Book fair", "Disco", "Fireworks").forEach { family.event(it, "2026-10-13", Slot.AllDay(1), Who.Members(setOf(family.tom))) }
        family.holiday("Half term", "2026-10-26", "2026-10-30")
        val grid = MonthModel.build(family.state(), YearMonth.of(2026, 10), today = date("2026-10-12"))
        assertEquals(DayOfWeek.MONDAY, grid.weekdays.first())
        assertEquals(5, grid.weeks.size)
        assertEquals(date("2026-09-28"), grid.weeks.first().first().date)
        val cell = grid.weeks.flatten().single { it.date == date("2026-10-13") }
        assertEquals(listOf("Ada", "Tom"), cell.people.map { it.name })
        assertEquals(3, cell.titles.size)
        assertEquals(2, cell.more)
        assertTrue(grid.weeks.flatten().single { it.date == date("2026-10-12") }.today)
        assertTrue(grid.weeks.flatten().single { it.date == date("2026-10-27") }.holiday)
        assertTrue(!grid.weeks.first().first().inMonth)
    }

    @Test
    fun `needs someone lists the next fortnight's unresolved lifts`() {
        family.event("Party", "2026-10-17", timed("15:00", "17:00"), Who.Members(setOf(family.tom)), taking = dev.housecalendar.contracts.Taking.NeedsSomeone)
        val rows = NeedsSomeoneModel.build(family.state(), london("2026-10-12T08:00"))
        assertEquals(listOf("Party"), rows.map { it.title })
        assertEquals(date("2026-10-17"), rows.single().key.occurrence)
        assertEquals(listOf("Sam", "Alex"), NeedsSomeoneModel.adults(family.state()).map { it.name })
    }
}
