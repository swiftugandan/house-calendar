package dev.housecalendar.intake

import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The extras of Android's calendar insert intent (SPEC FR-49), already read out of the Intent. */
public data class InsertExtras(
    val title: String?,
    val beginMillis: Long?,
    val endMillis: Long?,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val rrule: String?,
)

/** SPEC FR-49 and FR-53: an "Add to calendar" button's extras as a draft. */
public object InsertIntent {
    private const val DEFAULT_MINUTES = 60L

    public fun draft(extras: InsertExtras, zone: ZoneId): IncomingDraft? {
        val (title, place, notes) = IncomingDraft.limited(extras.title.orEmpty(), extras.location.orEmpty(), extras.description.orEmpty())
        val begin = extras.beginMillis
        if (begin == null) return IncomingDraft(title, place = place, notes = notes)
        val (day, slot) = if (extras.allDay) {
            // Android's convention for all-day events: midnight UTC boundaries, end exclusive [SPEC R21].
            val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
            val last = extras.endMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1) }?.takeIf { it >= first } ?: first
            first to Slot.AllDay((ChronoUnit.DAYS.between(first, last) + 1).toInt().coerceAtMost(Limits.MAX_SPAN_DAYS))
        } else {
            val from = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
            val until = extras.endMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }?.takeIf { it > from } ?: from.plusMinutes(DEFAULT_MINUTES)
            val offset = ChronoUnit.DAYS.between(from.toLocalDate(), until.toLocalDate()).toInt().coerceIn(0, Limits.MAX_SPAN_DAYS - 1)
            from.toLocalDate() to Slot.Timed(from.toLocalTime().truncatedTo(ChronoUnit.MINUTES), until.toLocalTime().truncatedTo(ChronoUnit.MINUTES), offset)
        }
        val rule = extras.rrule?.let { RruleMapper.map(it, day, zone) }
        return IncomingDraft(
            title = title, day = day, slot = slot, repeat = (rule as? Mapped.Rule)?.repeat ?: Repeat.Never, count = (rule as? Mapped.Rule)?.count,
            unmappableRule = (rule as? Mapped.Unmappable)?.text, place = place, notes = notes,
        )
    }
}

/** SPEC FR-51: shared text as a draft; the first line is the title, the whole text the notes. */
public object SharedText {
    private const val DEFAULT_MINUTES = 60L

    public fun draft(text: String, now: LocalDateTime): IncomingDraft {
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val (title, _, notes) = IncomingDraft.limited(firstLine, "", text)
        val hint = DateTimeHints.find(text, now)
        val time = hint.time
        val day = hint.date ?: time?.let { if (it > now.toLocalTime()) now.toLocalDate() else now.toLocalDate().plusDays(1) }
        val slot = time?.let { start -> Slot.Timed(start, start.plusMinutes(DEFAULT_MINUTES).let { end -> if (end <= start) LocalTime.of(LAST_HOUR, LAST_MINUTE) else end }, 0) }
        return IncomingDraft(title = title, day = day, slot = slot, notes = notes)
    }

    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
}

/** One date and one time found in text; either may be missing. */
public data class Hint(val date: LocalDate?, val time: LocalTime?)

/**
 * A small English grammar for "Sat 3pm", "tomorrow at 10:30", "12 Oct" (SPEC FR-51), relative to [now].
 * Weekdays mean the next one from today; dates without a year mean the next one from today.
 */
public object DateTimeHints {
    private val WEEKDAYS: Map<String, DayOfWeek> = DayOfWeek.entries.flatMap { day ->
        val name = day.name.lowercase(Locale.ROOT)
        listOf(name to day, name.take(3) to day) + if (day == DayOfWeek.TUESDAY) listOf("tues" to day) else if (day == DayOfWeek.THURSDAY) listOf("thur" to day, "thurs" to day) else emptyList()
    }.toMap()
    private val MONTHS: Map<String, Month> = Month.entries.flatMap { month ->
        val name = month.name.lowercase(Locale.ROOT)
        listOf(name to month, name.take(3) to month) + if (month == Month.SEPTEMBER) listOf("sept" to month) else emptyList()
    }.toMap()
    private val MONTH_WORDS = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
    private val DAY_MONTH = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+($MONTH_WORDS)\\b")
    private val MONTH_DAY = Regex("\\b($MONTH_WORDS)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
    private val TWELVE_HOUR = Regex("\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)\\b")
    private val TWENTY_FOUR = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b")
    private val WORD = Regex("[a-z]+")
    private const val NOON = 12

    public fun find(text: String, now: LocalDateTime): Hint {
        val lower = text.lowercase(Locale.ROOT)
        return Hint(date(lower, now.toLocalDate()), time(lower))
    }

    private fun date(text: String, today: LocalDate): LocalDate? {
        val words = WORD.findAll(text).map { it.value }.toList()
        when {
            "tomorrow" in words -> return today.plusDays(1)
            "today" in words || "tonight" in words -> return today
        }
        (DAY_MONTH.find(text)?.let { it.groupValues[1] to it.groupValues[2] } ?: MONTH_DAY.find(text)?.let { it.groupValues[2] to it.groupValues[1] })
            ?.let { (day, month) -> dayOfMonth(day.toInt(), MONTHS.getValue(month), today)?.let { return it } }
        return words.firstNotNullOfOrNull { WEEKDAYS[it] }?.let { today.with(TemporalAdjusters.nextOrSame(it)) }
    }

    private fun dayOfMonth(day: Int, month: Month, today: LocalDate): LocalDate? {
        if (day !in 1..month.maxLength()) return null
        val thisYear = LocalDate.of(today.year, month, day.coerceAtMost(month.length(today.isLeapYear)))
        return if (thisYear < today) LocalDate.of(today.year + 1, month, day.coerceAtMost(month.length(Year.isLeap(today.year + 1L)))) else thisYear
    }

    private fun time(text: String): LocalTime? {
        TWELVE_HOUR.find(text)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour !in 1..NOON || minute > MAX_MINUTE) return null
            val base = hour % NOON
            return LocalTime.of(if (match.groupValues[3] == "pm") base + NOON else base, minute)
        }
        return TWENTY_FOUR.find(text)?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
    }

    private const val MAX_MINUTE = 59
}
