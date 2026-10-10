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
import dev.housecalendar.app.calendar.CalendarSnapshot
import dev.housecalendar.intake.IcsReader
import dev.housecalendar.intake.IncomingDraft
import dev.housecalendar.intake.InsertExtras
import dev.housecalendar.intake.InsertIntent
import dev.housecalendar.intake.IntakeResult
import dev.housecalendar.intake.Refusal
import dev.housecalendar.intake.SharedText
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId

/** What arrived from another app, waiting for the main activity to show it (SPEC FR-49 to FR-55). */
sealed interface Incoming {
    /** One draft: opens the add sheet. */
    data class One(val draft: IncomingDraft) : Incoming

    /** Several: the import review. [copies] marks drafts copied from this phone's calendars (FR-55). */
    data class Many(val result: IntakeResult.Drafts, val copies: Boolean) : Incoming

    data class Refused(val reason: Refusal) : Incoming
}

/**
 * The entry point for other apps (SAD §3.8 "Intake"): an "Add to calendar" insert intent, a `.ics` file viewed
 * or shared, or shared text. It reads at most 1 MiB, turns it into drafts, hands them to the app, opens the
 * main activity and finishes. It never records anything: a person confirms first.
 */
class IntakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as HouseCalendarApp).graph
        val zone = (graph.repository.snapshot.value as? CalendarSnapshot.Ready)?.state?.zone ?: ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(graph.clock.now(), zone)
        graph.offerIncoming(read(intent, zone, now))
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun read(intent: Intent?, zone: ZoneId, now: LocalDateTime): Incoming? {
        val action = intent?.action ?: return null
        return when {
            action == Intent.ACTION_INSERT || action == Intent.ACTION_EDIT -> InsertIntent.draft(extrasOf(intent), zone)?.let(Incoming::One)
            action == Intent.ACTION_VIEW -> intent.data?.let { file(it, zone) }
            action == Intent.ACTION_SEND && intent.type?.startsWith(TEXT_CALENDAR) == true -> stream(intent)?.let { file(it, zone) }
            action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { Incoming.One(SharedText.draft(it, now)) }
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

    private fun file(uri: Uri, zone: ZoneId): Incoming {
        val bytes = readBounded(contentResolver, uri) ?: return Incoming.Refused(Refusal.TOO_LARGE)
        return when (val result = IcsReader.read(bytes, zone)) {
            is IntakeResult.Refused -> Incoming.Refused(result.reason)
            is IntakeResult.Drafts -> result.drafts.singleOrNull()?.takeIf { result.skipped == 0 && result.overridesSkipped == 0 }?.let(Incoming::One)
                ?: Incoming.Many(result, copies = false)
        }
    }

    /** SPEC FR-56: reads one byte past the limit, so an oversized file is refused rather than truncated. */
    private fun readBounded(resolver: ContentResolver, uri: Uri): ByteArray? = try {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(CHUNK_BYTES)
            while (buffer.size() <= IcsReader.MAX_BYTES) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray().takeIf { it.size <= IcsReader.MAX_BYTES }
        }
    } catch (e: IOException) {
        ByteArray(0) // Unreadable: the reader then says it isn't a calendar file.
    } catch (e: SecurityException) {
        ByteArray(0)
    }

    private companion object {
        const val TEXT_CALENDAR = "text/calendar"
        const val CHUNK_BYTES = 16 * 1024
    }
}
