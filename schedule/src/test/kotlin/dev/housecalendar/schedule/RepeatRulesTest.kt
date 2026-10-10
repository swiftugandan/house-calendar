package dev.housecalendar.schedule

import dev.housecalendar.contracts.Repeat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate

/** SPEC FR-20 and FR-21, one rule at a time. */
class RepeatRulesTest {
    private fun dates(repeat: Repeat, anchor: String, from: String, to: String): List<LocalDate> {
        val start = date(from)
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= date(to) }
            .filter { RepeatRules.matches(repeat, date(anchor), it) }.toList()
    }

    @Test
    fun `daily every 3 days from the anchor`() {
        assertEquals(listOf("2026-10-01", "2026-10-04", "2026-10-07", "2026-10-10").map(::date),
            dates(Repeat.Daily(3), "2026-10-01", "2026-09-25", "2026-10-10"))
    }

    @Test
    fun `nothing before the anchor`() {
        assertFalse(RepeatRules.matches(Repeat.Daily(1), date("2026-10-01"), date("2026-09-30")))
    }

    @Test
    fun `weekly on two days`() {
        assertEquals(listOf("2026-10-06", "2026-10-08", "2026-10-13", "2026-10-15").map(::date),
            dates(Repeat.Weekly(1, setOf(TUESDAY, THURSDAY)), "2026-10-06", "2026-10-01", "2026-10-15"))
    }

    @Test
    fun `fortnightly weeks are counted from the Monday of the anchor's week, whatever the family's week start`() {
        // Anchor Sunday 11 Oct: its Monday-week starts 5 Oct, so the "on" weeks start 5 Oct, 19 Oct, 2 Nov.
        assertEquals(listOf("2026-10-11", "2026-10-19", "2026-10-25", "2026-11-02", "2026-11-08").map(::date),
            dates(Repeat.Weekly(2, setOf(SUNDAY, MONDAY)), "2026-10-11", "2026-10-11", "2026-11-08"))
    }

    @Test
    fun `until is inclusive`() {
        assertEquals(listOf("2026-10-06", "2026-10-13").map(::date),
            dates(Repeat.Weekly(1, setOf(TUESDAY), until = date("2026-10-13")), "2026-10-06", "2026-10-01", "2026-10-31"))
    }

    @Test
    fun `monthly on the 31st skips short months`() {
        assertEquals(listOf("2027-01-31", "2027-03-31", "2027-05-31").map(::date),
            dates(Repeat.MonthlyOnDay(1, 31), "2027-01-31", "2027-01-01", "2027-06-30"))
    }

    @Test
    fun `every other month on the 15th`() {
        assertEquals(listOf("2026-10-15", "2026-12-15", "2027-02-15").map(::date),
            dates(Repeat.MonthlyOnDay(2, 15), "2026-10-15", "2026-10-01", "2027-03-31"))
    }

    @Test
    fun `second Tuesday and last Friday`() {
        assertEquals(listOf("2026-10-13", "2026-11-10", "2026-12-08").map(::date),
            dates(Repeat.MonthlyOnWeekday(1, 2, TUESDAY), "2026-10-13", "2026-10-01", "2026-12-31"))
        assertEquals(listOf("2027-01-29", "2027-02-26", "2027-04-30").map(::date),
            dates(Repeat.MonthlyOnWeekday(1, -1, FRIDAY), "2027-01-01", "2027-01-01", "2027-04-30").filter { it.monthValue != 3 })
    }

    @Test
    fun `a fourth weekday exists every month, a fifth is not offered`() {
        assertEquals(12, dates(Repeat.MonthlyOnWeekday(1, 4, MONDAY), "2027-01-01", "2027-01-01", "2027-12-31").size)
    }

    @Test
    fun `yearly on 29 February only in leap years`() {
        assertEquals(listOf("2028-02-29", "2032-02-29").map(::date),
            dates(Repeat.Yearly(1), "2028-02-29", "2028-01-01", "2033-12-31"))
    }

    @Test
    fun `yearly every 2 years`() {
        assertEquals(listOf("2026-10-10", "2028-10-10").map(::date),
            dates(Repeat.Yearly(2), "2026-10-10", "2026-01-01", "2029-12-31"))
    }

    @Test
    fun `nonsense from a hostile peer produces nothing rather than failing`() {
        assertEquals(emptyList<LocalDate>(), dates(Repeat.Daily(0), "2026-10-01", "2026-10-01", "2026-10-10"))
        assertEquals(emptyList<LocalDate>(), dates(Repeat.Weekly(1, emptySet()), "2026-10-01", "2026-10-01", "2026-10-10"))
        assertEquals(emptyList<LocalDate>(), dates(Repeat.MonthlyOnDay(1, 32), "2026-10-01", "2026-10-01", "2026-12-31"))
        assertEquals(emptyList<LocalDate>(), dates(Repeat.MonthlyOnWeekday(1, 5, MONDAY), "2026-10-01", "2026-10-01", "2026-12-31"))
        assertEquals(emptyList<LocalDate>(), dates(Repeat.Daily(-2), "2026-10-01", "2026-10-01", "2026-10-10"))
    }

    @Test
    fun `never and unrecognised rules produce only the anchor`() {
        assertEquals(listOf(date("2026-10-01")), dates(Repeat.Never, "2026-10-01", "2026-09-01", "2026-12-31"))
        val future = Repeat.Unrecognised(buildJsonObject { put("type", "hourly") })
        assertEquals(listOf(date("2026-10-01")), dates(future, "2026-10-01", "2026-09-01", "2026-12-31"))
    }
}
