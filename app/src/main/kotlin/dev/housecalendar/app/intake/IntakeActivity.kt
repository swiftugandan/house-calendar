package dev.housecalendar.app.intake

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import dev.housecalendar.app.HouseCalendarApp
import dev.housecalendar.app.MainActivity
import dev.housecalendar.app.model.Incoming
import dev.housecalendar.intake.IcsReader
import dev.housecalendar.intake.InsertExtras
import dev.housecalendar.intake.Refusal
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * The entry point for other apps (SAD §3.8 "Intake"): an "Add to calendar" insert intent, a `.ics` file viewed
 * or shared, or shared text. It reads at most 1 MiB, hands the raw input to the app, opens the main activity
 * and finishes. It never interprets times (the phone's screens do, in the family zone) and never records
 * anything: a person confirms first.
 */
class IntakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as HouseCalendarApp).graph.offerIncoming(read(intent))
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun read(intent: Intent?): Incoming? {
        val action = intent?.action ?: return null
        return when {
            action == Intent.ACTION_INSERT || action == Intent.ACTION_EDIT -> Incoming.Insert(extrasOf(intent))
            action == Intent.ACTION_VIEW -> intent.data?.let(::file)
            action == Intent.ACTION_SEND && intent.type?.startsWith(TEXT_CALENDAR) == true -> stream(intent)?.let(::file)
            action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(Incoming::Text)
            else -> null
        }
    }

    private fun extrasOf(intent: Intent): InsertExtras = InsertExtras(
        title = intent.getStringExtra(CalendarContract.Events.TITLE),
        beginMillis = intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME),
        endMillis = intent.longExtra(CalendarContract.EXTRA_EVENT_END_TIME),
        allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
        location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION),
        description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION),
        rrule = intent.getStringExtra(CalendarContract.Events.RRULE),
    )

    /** Begin and end times arrive as longs from most apps, but some send them as strings. */
    private fun Intent.longExtra(name: String): Long? = when (val value = extras?.get(name)) {
        is Long -> value
        is Int -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    private fun stream(intent: Intent): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION") // The typed overload exists only from API 33; minSdk is 28.
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun file(uri: Uri): Incoming = when (val read = readBounded(contentResolver, uri)) {
        is FileRead.Bytes -> Incoming.File(read.bytes)
        FileRead.TooLarge -> Incoming.Refused(Refusal.TOO_LARGE)
        FileRead.Unreadable -> Incoming.Refused(Refusal.NOT_A_CALENDAR)
    }

    private sealed interface FileRead {
        class Bytes(val bytes: ByteArray) : FileRead

        data object TooLarge : FileRead

        data object Unreadable : FileRead
    }

    /** SPEC FR-56: reads one chunk past the limit at most, so an oversized file is refused rather than truncated. */
    private fun readBounded(resolver: ContentResolver, uri: Uri): FileRead = try {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(CHUNK_BYTES)
            while (buffer.size() <= IcsReader.MAX_BYTES) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
            if (buffer.size() > IcsReader.MAX_BYTES) FileRead.TooLarge else FileRead.Bytes(buffer.toByteArray())
        } ?: FileRead.Unreadable
    } catch (e: IOException) {
        FileRead.Unreadable
    } catch (e: SecurityException) {
        FileRead.Unreadable
    }

    private companion object {
        const val TEXT_CALENDAR = "text/calendar"
        const val CHUNK_BYTES = 16 * 1024
    }
}
