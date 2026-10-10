package dev.housecalendar.schedule

import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * SPEC FR-20 and FR-21 as a membership predicate rather than an iterator (SAD ADR-10): whether [date] is an
 * original date the rule produces from [anchor]. O(1) per date, total for every input.
 */
public object RepeatRules {
    private const val LAST: Int = -1
    private val ORDINALS: Set<Int> = setOf(1, 2, 3, 4, LAST)
    private const val DAYS_PER_WEEK: Int = 7
    private const val MONTHS_PER_YEAR: Int = 12

    public fun matches(rule: Repeat, anchor: LocalDate, date: LocalDate): Boolean {
        if (date < anchor) return false
        return when (rule) {
            Repeat.Never, is Repeat.Unrecognised -> date == anchor
            is Repeat.Daily -> within(rule.interval, rule.until, date) &&
                ChronoUnit.DAYS.between(anchor, date) % rule.interval == 0L
            is Repeat.Weekly -> within(rule.interval, rule.until, date) && date.dayOfWeek in rule.days &&
                ChronoUnit.WEEKS.between(monday(anchor), monday(date)) % rule.interval == 0L
            is Repeat.MonthlyOnDay -> within(rule.interval, rule.until, date) && date.dayOfMonth == rule.dayOfMonth &&
                monthsBetween(anchor, date) % rule.interval == 0L
            is Repeat.MonthlyOnWeekday -> within(rule.interval, rule.until, date) && rule.ordinal in ORDINALS &&
                date.dayOfWeek == rule.weekday && isOrdinal(date, rule.ordinal) && monthsBetween(anchor, date) % rule.interval == 0L
            is Repeat.Yearly -> within(rule.interval, rule.until, date) && date.month == anchor.month &&
                date.dayOfMonth == anchor.dayOfMonth && (date.year - anchor.year) % rule.interval == 0
        }
    }

    /** Whether a rule may be recorded (SPEC FR-19, FR-20). Unrecognised rules can only be forwarded. */
    public fun isWellFormed(rule: Repeat): Boolean = when (rule) {
        Repeat.Never -> true
        is Repeat.Unrecognised -> false
        is Repeat.Daily -> rule.interval >= 1
        is Repeat.Weekly -> rule.interval >= 1 && rule.days.isNotEmpty()
        is Repeat.MonthlyOnDay -> rule.interval >= 1 && rule.dayOfMonth in 1..31
        is Repeat.MonthlyOnWeekday -> rule.interval >= 1 && rule.ordinal in ORDINALS
        is Repeat.Yearly -> rule.interval >= 1
    }

    /** A rule that actually repeats, as opposed to [Repeat.Never] or one this version cannot read. */
    public fun repeats(rule: Repeat): Boolean = rule !is Repeat.Never && rule !is Repeat.Unrecognised

    public fun until(rule: Repeat): LocalDate? = when (rule) {
        Repeat.Never, is Repeat.Unrecognised -> null
        is Repeat.Daily -> rule.until
        is Repeat.Weekly -> rule.until
        is Repeat.MonthlyOnDay -> rule.until
        is Repeat.MonthlyOnWeekday -> rule.until
        is Repeat.Yearly -> rule.until
    }

    public fun withoutUntil(rule: Repeat): Repeat = when (rule) {
        Repeat.Never, is Repeat.Unrecognised -> rule
        is Repeat.Daily -> rule.copy(until = null)
        is Repeat.Weekly -> rule.copy(until = null)
        is Repeat.MonthlyOnDay -> rule.copy(until = null)
        is Repeat.MonthlyOnWeekday -> rule.copy(until = null)
        is Repeat.Yearly -> rule.copy(until = null)
    }

    public fun withUntil(rule: Repeat, until: LocalDate?): Repeat = when (rule) {
        Repeat.Never, is Repeat.Unrecognised -> rule
        is Repeat.Daily -> rule.copy(until = until)
        is Repeat.Weekly -> rule.copy(until = until)
        is Repeat.MonthlyOnDay -> rule.copy(until = until)
        is Repeat.MonthlyOnWeekday -> rule.copy(until = until)
        is Repeat.Yearly -> rule.copy(until = until)
    }

    /** The ordinal of [date]'s weekday in its month: 1 to 4, or 5 for a fifth occurrence. */
    public fun ordinalOf(date: LocalDate): Int = (date.dayOfMonth - 1) / DAYS_PER_WEEK + 1

    public fun isLastOfMonth(date: LocalDate): Boolean = date.plusDays(DAYS_PER_WEEK.toLong()).month != date.month

    private fun within(interval: Int, until: LocalDate?, date: LocalDate): Boolean = interval >= 1 && (until == null || date <= until)

    private fun isOrdinal(date: LocalDate, ordinal: Int): Boolean =
        if (ordinal == LAST) isLastOfMonth(date) else ordinalOf(date) == ordinal

    private fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun monthsBetween(from: LocalDate, to: LocalDate): Long =
        (to.year - from.year).toLong() * MONTHS_PER_YEAR + (to.monthValue - from.monthValue)
}

/** SPEC FR-9: which slots can be placed. */
public object Slots {
    public fun isValid(slot: Slot): Boolean = when (slot) {
        is Slot.AllDay -> slot.days in 1..Limits.MAX_SPAN_DAYS
        is Slot.Timed -> slot.endDayOffset in 0 until Limits.MAX_SPAN_DAYS && (slot.endDayOffset > 0 || slot.end > slot.start)
        is Slot.Unrecognised -> false
    }

    /** The last date a placement on [day] occupies; intervals are half-open (SPEC FR-25). */
    public fun lastDay(slot: Slot, day: LocalDate): LocalDate = when (slot) {
        is Slot.AllDay -> day.plusDays(slot.days - 1L)
        is Slot.Timed -> {
            val endDay = day.plusDays(slot.endDayOffset.toLong())
            if (slot.endDayOffset > 0 && slot.end == java.time.LocalTime.MIDNIGHT) endDay.minusDays(1) else endDay
        }
        is Slot.Unrecognised -> day
    }
}
