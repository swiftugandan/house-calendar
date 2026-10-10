package dev.housecalendar.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import dev.housecalendar.app.calendar.CalendarSnapshot
import dev.housecalendar.app.display.DisplayRoot
import dev.housecalendar.app.display.DisplayRootTheme
import dev.housecalendar.app.onboarding.JoiningFlow
import dev.housecalendar.app.onboarding.OnboardingFlow
import dev.housecalendar.app.onboarding.WhosePhone
import dev.housecalendar.app.phone.PhoneRoot
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.format.DateText
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.HouseCalendarTheme
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.EventId
import dev.housecalendar.schedule.OccurrenceRef
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.util.UUID

/**
 * The single activity for both roles (SAD ADR-14): the device's role, which is family data, decides
 * whether it shows the phone screens or display mode, and a change of role takes effect live.
 */
class MainActivity : FragmentActivity() {
    /** An occurrence to open (from a reminder), cleared once navigation has handled it. */
    private val link = MutableStateFlow<OccurrenceRef?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) link.value = parse(intent?.dataString)
        enableEdgeToEdge()
        val graph = (application as HouseCalendarApp).graph
        setContent {
            val snapshot by graph.repository.snapshot.collectAsState()
            val key by graph.key.collectAsState()
            val minute by graph.minute.collectAsState()
            val pendingLink by link.collectAsState()
            val ready = snapshot as? CalendarSnapshot.Ready
            val display = ready != null && graph.role() == DeviceRole.Display
            HouseCalendarTheme(dark = if (display) DisplayRootTheme.dark(graph, minute) else androidx.compose.foundation.isSystemInDarkTheme()) {
                val locale = LocalConfiguration.current.locales[0]
                val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
                val zone = ready?.state?.zone ?: java.time.ZoneId.systemDefault()
                val today = minute.atZone(zone).toLocalDate()
                val dateText = remember(locale, is24Hour, today) { DateText(locale, is24Hour, today) }
                CompositionLocalProvider(LocalDateText provides dateText) {
                    Box(Modifier.fillMaxSize().background(Hc.colors.paper)) {
                        when {
                            key is KeyState.Loading || snapshot is CalendarSnapshot.Loading -> Unit
                            key is KeyState.None && ready == null -> OnboardingFlow(graph)
                            ready == null -> JoiningFlow(graph)
                            display -> DisplayRoot(graph, ready.state, minute)
                            needsOwner(graph, ready) -> WhosePhone(graph, ready.state)
                            else -> {
                                NotificationPermission()
                                PhoneRoot(graph, ready.state, minute, pendingLink) { link.value = null }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        link.value = parse(intent.dataString)
    }

    override fun onStart() {
        super.onStart()
        (application as HouseCalendarApp).graph.setForeground(true)
    }

    override fun onStop() {
        (application as HouseCalendarApp).graph.setForeground(false)
        super.onStop()
    }

    /** SPEC FR-5: a phone asks once whose it is, after it has the family's members. */
    private fun needsOwner(graph: AppGraph, ready: CalendarSnapshot.Ready): Boolean {
        val device = ready.state.devices[graph.deviceId] ?: return false
        return device.role?.value == DeviceRole.Phone && device.owner == null && ready.state.activeMembers().isNotEmpty()
    }

    companion object {
        private const val SCHEME = "housecalendar://occurrence/"

        /** The deep link a reminder opens (SPEC FR-39). */
        fun occurrenceLink(ref: OccurrenceRef): String = "$SCHEME${ref.event}/${ref.occurrence}"

        fun parse(data: String?): OccurrenceRef? {
            val path = data?.takeIf { it.startsWith(SCHEME) }?.removePrefix(SCHEME)?.split('/') ?: return null
            if (path.size != 2) return null
            return runCatching { OccurrenceRef(EventId(UUID.fromString(path[0])), LocalDate.parse(path[1])) }.getOrNull()
        }
    }
}

/** Asks once for notification permission (API 33+) on a phone, for reminders (SPEC FR-37). */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
