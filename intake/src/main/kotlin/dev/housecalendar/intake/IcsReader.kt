package dev.housecalendar.intake

import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** One content line: `NAME;PARAM=VALUE:value` (RFC 5545 §3.1). */
internal data class ContentLine(val name: String, val params: Map<String, String>, val value: String)

/** A date or date-time value as written, before conversion to family time. */
internal sealed interface IcsTime {
    data class Date(val date: LocalDate) : IcsTime

    data class Floating(val dateTime: LocalDateTime) : IcsTime

    data class Utc(val dateTime: LocalDateTime) : IcsTime

    data class Zoned(val dateTime: LocalDateTime, val zone: ZoneId) : IcsTime
}

/**
 * The iCalendar subset of SPEC FR-50: `VEVENT`s with UID, SUMMARY, DTSTART, DTEND or DURATION, LOCATION,
 * DESCRIPTION, RRULE and EXDATE. Everything else is ignored. Input is untrusted: bounded (FR-56), and
 * nothing in it can make reading throw; what can't be read is skipped and counted.
 */
public object IcsReader {
    public const val MAX_BYTES: Int = 1024 * 1024
    public const val MAX_EVENTS: Int = 500

    private val DEFAULT_LENGTH: Duration = Duration.ofHours(1)

    public fun read(bytes: ByteArray, zone: ZoneId): IntakeResult {
        if (bytes.size > MAX_BYTES) return IntakeResult.Refused(Refusal.TOO_LARGE)
        val lines = unfold(String(bytes, Charsets.UTF_8)).mapNotNull(::contentLine)
        if (lines.none { it.name == "BEGIN" && it.value.equals("VCALENDAR", ignoreCase = true) }) return IntakeResult.Refused(Refusal.NOT_A_CALENDAR)
        val events = events(lines)
        if (events.size > MAX_EVENTS) return IntakeResult.Refused(Refusal.TOO_MANY_EVENTS)
        var skipped = 0
        var overrides = 0
        val drafts = events.mapNotNull { event ->
            if (event.any { it.name == "RECURRENCE-ID" }) {
                overrides += 1
                return@mapNotNull null
            }
            draft(event, zone).also { if (it == null) skipped += 1 }
        }
        return IntakeResult.Drafts(drafts, skipped, overrides)
    }

    /** RFC 5545 §3.1: a line starting with a space or tab continues the previous one. */
    internal fun unfold(text: String): List<String> {
        val lines = mutableListOf<StringBuilder>()
        text.split("\r\n", "\n").forEach { raw ->
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) lines.last().append(raw.substring(1)) else lines += StringBuilder(raw)
        }
        return lines.map { it.toString() }.filter { it.isNotBlank() }
    }

    internal fun contentLine(line: String): ContentLine? {
        var inQuotes = false
        var colon = -1
        for ((index, char) in line.withIndex()) {
            if (char == '"') inQuotes = !inQuotes
            if (char == ':' && !inQuotes) {
                colon = index
                break
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { param ->
            val equals = param.indexOf('=')
            if (equals <= 0) null else param.substring(0, equals).uppercase() to param.substring(equals + 1).trim('"')
        }.toMap()
        return ContentLine(head.first().uppercase(), params, line.substring(colon + 1))
    }

    private fun events(lines: List<ContentLine>): List<List<ContentLine>> {
        val events = mutableListOf<List<ContentLine>>()
        var current: MutableList<ContentLine>? = null
        var nested = 0
        lines.forEach { line ->
            when {
                line.name == "BEGIN" && line.value.equals("VEVENT", ignoreCase = true) -> current = mutableListOf()
                line.name == "END" && line.value.equals("VEVENT", ignoreCase = true) -> current?.let { events += it }.also { current = null }
                line.name == "BEGIN" && current != null -> nested += 1
                line.name == "END" && current != null && nested > 0 -> nested -= 1
                nested == 0 -> current?.add(line)
            }
        }
        return events
    }

    private fun draft(event: List<ContentLine>, zone: ZoneId): IncomingDraft? {
        fun first(name: String) = event.firstOrNull { it.name == name }
        val title = first("SUMMARY")?.value?.let(::unescape)?.takeIf { it.isNotBlank() } ?: return null
        val start = first("DTSTART")?.let(::time) ?: return null
        val end = first("DTEND")?.let(::time)
        val duration = first("DURATION")?.value?.let(Durations::parse)
        val placed = placement(start, end, duration, zone) ?: return null
        val rule = first("RRULE")?.value?.let { RruleMapper.map(it, placed.first, zone) }
        val exdates = event.filter { it.name == "EXDATE" }.flatMap { line ->
            line.value.split(',').mapNotNull { value -> time(line.copy(value = value))?.let { toFamilyDate(it, zone) } }
        }
        val (shortTitle, place, notes) = IncomingDraft.limited(title, first("LOCATION")?.value?.let(::unescape).orEmpty(), first("DESCRIPTION")?.value?.let(::unescape).orEmpty())
        return IncomingDraft(
            title = shortTitle, day = placed.first, slot = placed.second, repeat = (rule as? Mapped.Rule)?.repeat ?: Repeat.Never,
            count = (rule as? Mapped.Rule)?.count, unmappableRule = (rule as? Mapped.Unmappable)?.text, exdates = exdates,
            place = place, notes = notes, uid = first("UID")?.value?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** The day and slot in family time (SPEC FR-53). */
    private fun placement(start: IcsTime, end: IcsTime?, duration: Duration?, zone: ZoneId): Pair<LocalDate, Slot>? {
        if (start is IcsTime.Date) {
            val last = when {
                end is IcsTime.Date -> end.date.minusDays(1)
                duration != null -> start.date.plusDays(duration.toDays() - 1)
                else -> start.date
            }
            val days = (ChronoUnit.DAYS.between(start.date, last) + 1).coerceIn(1, Limits.MAX_SPAN_DAYS.toLong()).toInt()
            return start.date to Slot.AllDay(days)
        }
        val from = toFamily(start, zone) ?: return null
        val until = (end?.let { toFamily(it, zone) } ?: duration?.let { from.plus(it) })?.takeIf { it > from } ?: from.plus(DEFAULT_LENGTH)
        val offset = ChronoUnit.DAYS.between(from.toLocalDate(), until.toLocalDate()).coerceIn(0, Limits.MAX_SPAN_DAYS - 1L).toInt()
        val endTime = if (from.toLocalDate().plusDays(offset.toLong()) == until.toLocalDate()) until.toLocalTime() else LocalTime.of(LAST_HOUR, LAST_MINUTE)
        return from.toLocalDate() to Slot.Timed(from.toLocalTime().withSecond(0).withNano(0), endTime.withSecond(0).withNano(0), offset)
    }

    internal fun time(line: ContentLine): IcsTime? {
        val value = line.value.trim()
        val date = DATE.matchEntire(value)
        if (date != null || line.params["VALUE"].equals("DATE", ignoreCase = true)) {
            val (y, m, d) = (date ?: return null).destructured
            return localDate(y, m, d)?.let(IcsTime::Date)
        }
        val match = DATE_TIME.matchEntire(value) ?: return null
        val (y, m, d, hh, mm, ss, z) = match.destructured
        val dateTime = localDate(y, m, d)?.let { day -> localTime(hh, mm, ss)?.let { day.atTime(it) } } ?: return null
        val tzid = line.params["TZID"]
        return when {
            z == "Z" -> IcsTime.Utc(dateTime)
            tzid != null -> zoneOf(tzid)?.let { IcsTime.Zoned(dateTime, it) } ?: IcsTime.Floating(dateTime)
            else -> IcsTime.Floating(dateTime)
        }
    }

    /** SPEC FR-53: UTC and zoned times through their instant; floating times and dates as written. */
    internal fun toFamily(time: IcsTime, zone: ZoneId): LocalDateTime? = when (time) {
        is IcsTime.Date -> time.date.atStartOfDay()
        is IcsTime.Floating -> time.dateTime
        is IcsTime.Utc -> time.dateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
        is IcsTime.Zoned -> time.dateTime.atZone(time.zone).withZoneSameInstant(zone).toLocalDateTime()
    }

    internal fun toFamilyDate(time: IcsTime, zone: ZoneId): LocalDate? = when (time) {
        is IcsTime.Date -> time.date
        else -> toFamily(time, zone)?.toLocalDate()
    }

    /** RFC 5545 §3.3.11. */
    internal fun unescape(text: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\' && index + 1 < text.length) {
                when (val next = text[index + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(next)
                }
                index += 2
            } else {
                out.append(char)
                index += 1
            }
        }
        return out.toString()
    }

    private fun localDate(y: String, m: String, d: String): LocalDate? = try {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt())
    } catch (e: DateTimeException) {
        null // 30 February and the like: skipped, as FR-56 asks.
    }

    private fun localTime(hh: String, mm: String, ss: String): LocalTime? = try {
        LocalTime.of(hh.toInt(), mm.toInt(), ss.toInt().coerceAtMost(LEAP_SECOND - 1))
    } catch (e: DateTimeException) {
        null
    }

    private fun zoneOf(id: String): ZoneId? = try {
        ZoneId.of(id)
    } catch (e: DateTimeException) {
        null // Windows names such as "GMT Standard Time": read as floating (FR-53).
    }

    private val DATE = Regex("(\\d{4})(\\d{2})(\\d{2})")
    private val DATE_TIME = Regex("(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})(Z?)")
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
    private const val LEAP_SECOND = 60
}

/** RFC 5545 §3.3.6 durations: `P1D`, `PT1H30M`, `P2W`, signs ignored. */
internal object Durations {
    private val PATTERN = Regex("[+-]?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?")

    fun parse(text: String): Duration? {
        val match = PATTERN.matchEntire(text.trim()) ?: return null
        val (w, d, h, m, s) = match.destructured
        fun n(value: String) = value.toLongOrNull() ?: 0L
        return Duration.ofDays(n(w) * DAYS_PER_WEEK + n(d)).plusHours(n(h)).plusMinutes(n(m)).plusSeconds(n(s)).takeIf { !it.isZero }
    }

    private const val DAYS_PER_WEEK = 7L
}
