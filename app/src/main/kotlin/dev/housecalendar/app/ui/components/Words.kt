package dev.housecalendar.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.housecalendar.app.R
import dev.housecalendar.app.model.DayWord
import dev.housecalendar.app.model.DeviceLabel
import dev.housecalendar.app.model.PersonMark
import dev.housecalendar.app.model.TakingMark
import dev.housecalendar.app.ui.format.DateText
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.schedule.RepeatRules
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** The date formatter for the current locale, 12/24-hour setting and family "today"; provided by each root. */
val LocalDateText = staticCompositionLocalOf { DateText(Locale.getDefault(), true, LocalDate.now()) }

/**
 * Structure to words (SPEC NFR-OPS-2): every sentence is assembled here from string resources, so the view
 * models never hold English and a translation touches only `strings.xml`.
 */
object Words {
    @Composable
    @ReadOnlyComposable
    fun day(date: LocalDate, word: DayWord): String = when (word) {
        DayWord.TODAY -> stringResource(R.string.today)
        DayWord.TOMORROW -> stringResource(R.string.tomorrow)
        DayWord.NONE -> LocalDateText.current.dayShort(date)
    }

    @Composable
    @ReadOnlyComposable
    fun time(value: LocalTime): String = LocalDateText.current.time(value)

    @Composable
    @ReadOnlyComposable
    fun timeRange(start: LocalTime, end: LocalTime, endsOnLaterDay: Boolean, endDay: LocalDate? = null): String {
        val text = LocalDateText.current
        return if (endsOnLaterDay && endDay != null) {
            stringResource(R.string.time_range_next_day, text.time(start), text.weekdayShort(endDay), text.time(end))
        } else {
            stringResource(R.string.time_range, text.time(start), text.time(end))
        }
    }

    @Composable
    @ReadOnlyComposable
    fun taking(mark: TakingMark): String? = when (mark) {
        TakingMark.None -> null
        TakingMark.NeedsSomeone -> stringResource(R.string.needs_someone)
        is TakingMark.Person -> stringResource(R.string.taking_person, mark.person.name)
    }

    @Composable
    @ReadOnlyComposable
    fun people(people: List<PersonMark>, everyone: Boolean): String = when {
        everyone -> stringResource(R.string.everyone)
        else -> join(people.map { it.name })
    }

    @Composable
    @ReadOnlyComposable
    fun join(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> stringResource(R.string.and_separator, names.dropLast(1).joinToString(stringResource(R.string.list_separator)), names.last())
    }

    @Composable
    @ReadOnlyComposable
    fun device(label: DeviceLabel): String = when {
        label.isSelf -> stringResource(R.string.this_device)
        label.name.isBlank() -> stringResource(R.string.a_device)
        else -> label.name
    }

    @Composable
    @ReadOnlyComposable
    fun relative(instant: Instant, now: Instant): String {
        val elapsed = Duration.between(instant, now)
        return when {
            elapsed.toMinutes() < 1 -> stringResource(R.string.just_now)
            elapsed.toHours() < 1 -> pluralStringResource(R.plurals.minutes_ago, elapsed.toMinutes().toInt(), elapsed.toMinutes())
            elapsed.toDays() < 1 -> pluralStringResource(R.plurals.hours_ago, elapsed.toHours().toInt(), elapsed.toHours())
            else -> pluralStringResource(R.plurals.days_ago, elapsed.toDays().toInt(), elapsed.toDays())
        }
    }

    @Composable
    @ReadOnlyComposable
    fun reminder(reminder: Reminder): String = when (reminder) {
        is Reminder.MinutesBefore -> when {
            reminder.minutes == 0 -> stringResource(R.string.reminder_at_start)
            reminder.minutes % MINUTES_PER_HOUR == 0 -> pluralStringResource(R.plurals.reminder_hours, reminder.minutes / MINUTES_PER_HOUR, reminder.minutes / MINUTES_PER_HOUR)
            else -> pluralStringResource(R.plurals.reminder_minutes, reminder.minutes, reminder.minutes)
        }
        is Reminder.DaysBeforeAt -> when (reminder.days) {
            0 -> stringResource(R.string.reminder_on_day_at, time(reminder.at))
            1 -> stringResource(R.string.reminder_day_before_at, time(reminder.at))
            else -> pluralStringResource(R.plurals.reminder_days_before_at, reminder.days, reminder.days, time(reminder.at))
        }
        is Reminder.Unrecognised -> ""
    }

    /** DESIGN.md: "Every Tuesday in term time, until 15 Dec". */
    @Composable
    @ReadOnlyComposable
    fun repeatSummary(repeat: Repeat, termTimeOnly: Boolean, start: LocalDate): String? {
        if (repeat is Repeat.Unrecognised) return stringResource(R.string.summary_unrecognised)
        if (!RepeatRules.repeats(repeat)) return null
        val base = when (repeat) {
            is Repeat.Daily -> if (repeat.interval == 1) stringResource(R.string.summary_daily) else stringResource(R.string.summary_every_n_days, repeat.interval)
            is Repeat.Weekly -> {
                val days = weekdays(repeat.days)
                when (repeat.interval) {
                    1 -> stringResource(R.string.summary_weekly, days)
                    2 -> stringResource(R.string.summary_fortnightly, days)
                    else -> stringResource(R.string.summary_every_n_weeks, repeat.interval, days)
                }
            }
            is Repeat.MonthlyOnDay -> if (repeat.interval == 1) stringResource(R.string.summary_monthly_day, ordinalDay(repeat.dayOfMonth)) else stringResource(R.string.summary_every_n_months, repeat.interval)
            is Repeat.MonthlyOnWeekday -> when {
                repeat.interval != 1 -> stringResource(R.string.summary_every_n_months, repeat.interval)
                repeat.ordinal < 0 -> stringResource(R.string.summary_monthly_last, weekdayName(repeat.weekday))
                else -> stringResource(R.string.summary_monthly_weekday, ordinal(repeat.ordinal), weekdayName(repeat.weekday))
            }
            is Repeat.Yearly -> if (repeat.interval == 1) stringResource(R.string.summary_yearly, LocalDateText.current.dayAndMonth(start)) else stringResource(R.string.summary_every_n_years, repeat.interval)
            Repeat.Never, is Repeat.Unrecognised -> return null
        }
        val termed = if (termTimeOnly) stringResource(R.string.summary_term_time, base) else base
        val until = RepeatRules.until(repeat)
        return if (until != null) stringResource(R.string.summary_until, termed, LocalDateText.current.dayAndMonth(until)) else termed
    }

    @Composable
    @ReadOnlyComposable
    fun weekdays(days: Set<DayOfWeek>): String = join(days.sorted().map { weekdayName(it) })

    @Composable
    @ReadOnlyComposable
    fun weekdayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale())

    @Composable
    @ReadOnlyComposable
    fun ordinal(ordinal: Int): String = when (ordinal) {
        1 -> stringResource(R.string.ordinal_1)
        2 -> stringResource(R.string.ordinal_2)
        3 -> stringResource(R.string.ordinal_3)
        else -> stringResource(R.string.ordinal_4)
    }

    /** "13th", in the device's language, through ICU's ordinal rules. */
    @Composable
    @ReadOnlyComposable
    fun ordinalDay(day: Int): String = android.icu.text.MessageFormat("{0,ordinal}", locale()).format(arrayOf<Any>(day))

    @Composable
    @ReadOnlyComposable
    private fun locale(): Locale = LocalContext.current.resources.configuration.locales[0]

    private const val MINUTES_PER_HOUR = 60
}
