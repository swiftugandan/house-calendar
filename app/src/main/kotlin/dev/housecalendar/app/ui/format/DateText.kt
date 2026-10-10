package dev.housecalendar.app.ui.format

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dates and times in the device's locale and 12- or 24-hour setting (SPEC NFR-OPS-2, DESIGN.md "Copy
 * patterns"). Words such as "Today" come from string resources; this only formats the dates themselves.
 */
class DateText(private val locale: Locale, is24Hour: Boolean, private val today: LocalDate) {
    private val time = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayShortWithYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val dayLongShortMonth = DateTimeFormatter.ofPattern("EEEE d MMM", locale)
    private val weekdayShort = DateTimeFormatter.ofPattern("EEE", locale)
    private val weekdayLong = DateTimeFormatter.ofPattern("EEEE", locale)
    private val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun time(value: LocalTime): String = time.format(value)

    /** "Tue 20 Oct", with the year only when it isn't this year. */
    fun dayShort(date: LocalDate): String = (if (date.year == today.year) dayShort else dayShortWithYear).format(date)

    /** "Saturday 10 October". */
    fun dayLong(date: LocalDate): String = dayLong.format(date)

    /** "Saturday 10 Oct", for where the full month doesn't fit. */
    fun dayLongShortMonth(date: LocalDate): String = dayLongShortMonth.format(date)

    fun weekdayShort(date: LocalDate): String = weekdayShort.format(date)

    fun weekdayLong(date: LocalDate): String = weekdayLong.format(date)

    /** "20 Oct". */
    fun dayAndMonth(date: LocalDate): String = dayAndMonth.format(date)

    fun month(value: YearMonth): String = month.format(value)

    fun dayNumber(date: LocalDate): String = date.dayOfMonth.toString()
}
