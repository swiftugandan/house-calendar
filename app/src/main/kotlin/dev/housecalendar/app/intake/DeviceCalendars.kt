package dev.housecalendar.app.intake

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import dev.housecalendar.intake.IncomingDraft
import dev.housecalendar.intake.InsertExtras
import dev.housecalendar.intake.InsertIntent
import dev.housecalendar.intake.IntakeResult
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * SPEC FR-55: one-way copies from the phone's own calendars, read through the calendar provider [R21] with
 * READ_CALENDAR, which the screen asks for only at that moment. Each occurrence becomes a one-off draft
 * (repeat rules are not followed into the copy), with the event's UID for FR-54's deterministic id.
 */
object DeviceCalendars {
    /** DESIGN.md: the next 8 weeks; the review lets the person untick anything. */
    val WINDOW: Duration = Duration.ofDays(56)

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.UID_2445,
        CalendarContract.Instances.START_DAY,
    )

    fun read(context: Context, from: Instant, zone: ZoneId): IntakeResult.Drafts {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.toEpochMilli())
            ContentUris.appendId(it, from.plus(WINDOW).toEpochMilli())
        }.build()
        val drafts = mutableListOf<IncomingDraft>()
        var skipped = 0
        context.contentResolver.query(uri, PROJECTION, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val extras = InsertExtras(
                    title = cursor.getString(0), beginMillis = cursor.getLong(1), endMillis = cursor.getLong(2), allDay = cursor.getInt(3) == 1,
                    location = cursor.getString(4), description = cursor.getString(5), rrule = null,
                )
                val draft = InsertIntent.draft(extras, zone)
                if (draft == null || draft.title.isBlank()) {
                    skipped += 1
                } else {
                    // One occurrence of a repeating event: its UID plus its day keeps copies distinct and stable.
                    val uid = cursor.getString(6)?.takeIf { it.isNotBlank() }?.let { "$it/${cursor.getInt(7)}" }
                    drafts += draft.copy(uid = uid)
                }
            }
        }
        return IntakeResult.Drafts(drafts, skipped, 0)
    }
}
