package dev.housecalendar.intake

import dev.housecalendar.contracts.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

internal sealed interface Mapped {
    data class Rule(val repeat: Repeat, val count: Int?) : Mapped

    data class Unmappable(val text: String) : Mapped
}

/**
 * RRULE text onto SPEC FR-20 (FR-52). Only rules that mean exactly the same thing map; anything with parts
 * the calendar can't express (BYSETPOS, several monthly weekdays, BYHOUR …) is unmappable.
 */
internal object RruleMapper {
    private val ALLOWED = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST")
    private val DAYS = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )
    private val ORDINALS = setOf(1, 2, 3, 4, -1)
    private val BY_DAY = Regex("([+-]?\\d)?(MO|TU|WE|TH|FR|SA|SU)")

    fun map(text: String, start: LocalDate, zone: ZoneId): Mapped {
        val unmappable = Mapped.Unmappable(text.trim())
        val parts = text.trim().split(';').filter { it.isNotBlank() }.mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) null else part.substring(0, equals).uppercase() to part.substring(equals + 1).uppercase()
        }.toMap()
        if (parts.keys.any { it !in ALLOWED }) return unmappable
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        if (interval < 1) return unmappable
        val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
        val until = parts["UNTIL"]?.let { value -> IcsReader.time(ContentLine("UNTIL", emptyMap(), value))?.let { IcsReader.toFamilyDate(it, zone) } ?: return unmappable }
        if (parts["WKST"] != null && parts["WKST"] != "MO" && interval > 1) return unmappable
        val byDay = parts["BYDAY"]?.split(',')?.map { BY_DAY.matchEntire(it) ?: return unmappable }
        val repeat = when (parts["FREQ"]) {
            "DAILY" -> if (byDay != null || parts["BYMONTHDAY"] != null) return unmappable else Repeat.Daily(interval, until)
            "WEEKLY" -> {
                if (byDay?.any { it.groupValues[1].isNotEmpty() } == true || parts["BYMONTHDAY"] != null) return unmappable
                Repeat.Weekly(interval, byDay?.mapNotNull { DAYS[it.groupValues[2]] }?.toSet() ?: setOf(start.dayOfWeek), until)
            }
            "MONTHLY" -> monthly(byDay, parts["BYMONTHDAY"], interval, until, start) ?: return unmappable
            "YEARLY" -> {
                val month = parts["BYMONTH"]?.toIntOrNull()
                val day = parts["BYMONTHDAY"]?.toIntOrNull()
                if (byDay != null || (month != null && month != start.monthValue) || (day != null && day != start.dayOfMonth)) return unmappable
                Repeat.Yearly(interval, until)
            }
            else -> return unmappable
        }
        if (parts["BYMONTH"] != null && parts["FREQ"] != "YEARLY") return unmappable
        return Mapped.Rule(repeat, count)
    }

    private fun monthly(byDay: List<MatchResult>?, byMonthDay: String?, interval: Int, until: LocalDate?, start: LocalDate): Repeat? = when {
        byDay != null && byMonthDay != null -> null
        byDay != null -> byDay.singleOrNull()?.let { match ->
            val ordinal = match.groupValues[1].toIntOrNull() ?: return@let null
            val weekday = DAYS[match.groupValues[2]] ?: return@let null
            if (ordinal in ORDINALS) Repeat.MonthlyOnWeekday(interval, ordinal, weekday, until) else null
        }
        byMonthDay != null -> byMonthDay.toIntOrNull()?.takeIf { it in 1..MAX_DAY }?.let { Repeat.MonthlyOnDay(interval, it, until) }
        else -> Repeat.MonthlyOnDay(interval, start.dayOfMonth, until)
    }

    private const val MAX_DAY = 31
}
