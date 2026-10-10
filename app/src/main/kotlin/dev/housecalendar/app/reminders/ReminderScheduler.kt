package dev.housecalendar.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.HouseCalendarApp
import dev.housecalendar.app.MainActivity
import dev.housecalendar.app.R
import dev.housecalendar.app.calendar.CalendarSnapshot
import dev.housecalendar.app.model.Posting
import dev.housecalendar.app.model.ReminderDecisions
import dev.housecalendar.app.ui.format.DateText
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.InstantMs
import dev.housecalendar.contracts.Slot
import dev.housecalendar.data.ReminderChoice
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.ReminderRole
import dev.housecalendar.schedule.ReminderScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * SPEC FR-36 to FR-39, SAD ADR-13: one exact alarm at the next reminder instant. When it fires, what is due is
 * recomputed from the log, so a sync that moved or cancelled an event in between is always respected.
 */
object ReminderScheduler {
    private const val CHANNEL = "reminders"
    private const val ACTION_FIRE = "dev.housecalendar.REMINDER"
    private const val REQUEST = 1
    private const val SETTLE_MS = 2_000L

    /** Follows the calendar and this device's settings for the life of the process. */
    @OptIn(FlowPreview::class)
    suspend fun follow(graph: AppGraph) {
        combine(graph.repository.snapshot, graph.settings) { snapshot, settings -> snapshot to settings }
            .debounce(SETTLE_MS)
            .collect { (snapshot, settings) ->
                val state = (snapshot as? CalendarSnapshot.Ready)?.state ?: return@collect
                if (settings == null) return@collect
                val decision = ReminderDecisions.decide(state, graph.ownerMember(), scopeOf(graph, settings.reminders), settings.lastReminderFired?.toInstant(), graph.clock.now())
                schedule(graph.appContext, decision.nextAlarm)
            }
    }

    /** Called when the alarm fires: post what is due, remember how far we got, and set the next alarm. */
    suspend fun fire(graph: AppGraph) {
        val snapshot = graph.repository.snapshot.first { it !is CalendarSnapshot.Loading }
        val state = (snapshot as? CalendarSnapshot.Ready)?.state ?: return
        val settings = graph.settingsStore.current()
        val decision = ReminderDecisions.decide(state, graph.ownerMember(), scopeOf(graph, settings.reminders), settings.lastReminderFired?.toInstant(), graph.clock.now())
        decision.post.forEach { post(graph.appContext, state, it, graph.clock.now()) }
        graph.settingsStore.update { it.copy(lastReminderFired = InstantMs.of(decision.fired)) }
        schedule(graph.appContext, decision.nextAlarm)
    }

    private fun scopeOf(graph: AppGraph, choice: ReminderChoice): ReminderScope = when {
        graph.role() == DeviceRole.Display -> ReminderScope.NONE
        choice == ReminderChoice.EVERYONE -> ReminderScope.EVERYONE
        choice == ReminderChoice.NONE -> ReminderScope.NONE
        else -> ReminderScope.MINE
    }

    private fun schedule(context: Context, at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            alarms.cancel(intent)
            return
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        } else {
            // Without the exact-alarm grant the reminder may be a little late; FR-38's late window covers it.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    private fun post(context: Context, state: CalendarState, posting: Posting, now: Instant) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val entry = posting.due.entry
        val locale = context.resources.configuration.locales[0]
        val text = DateText(locale, android.text.format.DateFormat.is24HourFormat(context), now.atZone(state.zone).toLocalDate())
        val whenText = (entry.slot as? Slot.Timed)?.let { text.time(it.start) } ?: context.getString(R.string.all_day)
        val names = entry.people.mapNotNull { id -> state.allMembers().firstOrNull { it.id == id }?.name }
        val body = when (posting.due.role) {
            ReminderRole.TAKING -> context.getString(R.string.reminder_you_are_taking, names.joinToString(", "))
            ReminderRole.INVOLVED, ReminderRole.OTHER -> listOf(names.joinToString(", "), entry.place).filter { it.isNotBlank() }.joinToString(" · ")
        }
        val title = context.getString(R.string.reminder_title, entry.title, whenText)
        val open = PendingIntent.getActivity(
            context, entry.key.hashCode(),
            Intent(context, MainActivity::class.java).setData(Uri.parse(MainActivity.occurrenceLink(entry.key))),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (posting.late) context.getString(R.string.reminder_late, title) else title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify((entry.key.hashCode() * 31 + posting.due.at.hashCode()), notification)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

/** The alarm: wakes the process, which recomputes and posts (SAD ADR-13). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val graph = (context.applicationContext as HouseCalendarApp).graph
        graph.scope.launch {
            try {
                ReminderScheduler.fire(graph)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Boot, clock and timezone changes (SPEC FR-37): starting the process starts [ReminderScheduler.follow],
 * which sets the next alarm from the current state.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as HouseCalendarApp).graph
        val pending = goAsync()
        graph.scope.launch {
            try {
                graph.repository.refreshNow()
                ReminderScheduler.fire(graph)
            } finally {
                pending.finish()
            }
        }
    }
}
