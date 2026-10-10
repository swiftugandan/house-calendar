package dev.housecalendar.app.display

import android.app.Activity
import android.app.KeyguardManager
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.R
import dev.housecalendar.app.phone.AddSheet
import dev.housecalendar.app.phone.SheetMode
import dev.housecalendar.app.phone.syncLine
import dev.housecalendar.app.settings.SettingsRoute
import dev.housecalendar.app.settings.SettingsScreen
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.components.Segmented
import dev.housecalendar.app.ui.components.Words
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.DayHours
import dev.housecalendar.app.ui.theme.DisplayType
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.PhysicalType
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.data.DeviceSettings
import dev.housecalendar.data.DisplayHome
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.OccurrenceRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Display-mode type, provided by [DisplayRoot] (DESIGN.md "Display type"). */
val LocalDisplayType = staticCompositionLocalOf<DisplayType> { error("DisplayType is only provided inside display mode") }

/** When the display uses its dark palette (DESIGN.md "Dark"): from 30 minutes before day hours end. */
object DisplayRootTheme {
    private val DUSK: Duration = Duration.ofMinutes(30)

    fun dark(graph: AppGraph, now: Instant): Boolean {
        val settings = graph.settings.value ?: DeviceSettings()
        val state = (graph.repository.snapshot.value as? dev.housecalendar.app.calendar.CalendarSnapshot.Ready)?.state ?: return false
        val local = now.atZone(state.zone).toLocalTime()
        val dusk = DayHours(settings.dayStarts, settings.dayEnds.minus(DUSK))
        return !dusk.isDay(local)
    }
}

private sealed interface DisplayScreen {
    data object Board : DisplayScreen

    data class Detail(val ref: OccurrenceRef) : DisplayScreen

    data class Settings(val page: SettingsRoute) : DisplayScreen

    data object Deleted : DisplayScreen
}

/**
 * Display mode (SPEC FR-31): full screen, kept on all day and night so the device stays reachable for sync,
 * dimmed to a night clock outside day hours, back to its home view after 2 minutes idle, and moved by up
 * to 2 px every 10 minutes against burn-in.
 */
@Composable
fun DisplayRoot(graph: AppGraph, state: CalendarState, now: Instant) {
    val settingsState by graph.settings.collectAsState()
    val settings = settingsState ?: DeviceSettings()
    val activity = LocalContext.current as Activity
    val home = when (settings.displayHome) {
        DisplayHome.WEEK -> DisplayView.WEEK
        DisplayHome.TODAY -> DisplayView.TODAY
        DisplayHome.MONTH -> DisplayView.MONTH
    }
    val scope = rememberCoroutineScope()
    var view by remember(home) { mutableStateOf(home) }
    var screen by remember { mutableStateOf<DisplayScreen>(DisplayScreen.Board) }
    var sheet by remember { mutableStateOf<SheetMode?>(null) }
    var lastTouch by remember { mutableStateOf(graph.clock.now()) }
    var wakeUntil by remember { mutableStateOf(Instant.MIN) }
    val local = now.atZone(state.zone).toLocalTime()
    val awake = DayHours(settings.dayStarts, settings.dayEnds).isDay(local) || graph.clock.now() < wakeUntil

    KeepOnAndImmersive(activity)
    LaunchedEffect(awake) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = if (awake) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else NIGHT_BRIGHTNESS
        }
    }
    LaunchedEffect(lastTouch, view, screen, sheet) {
        delay(IDLE_MS)
        view = home
        screen = DisplayScreen.Board
        sheet = null
    }
    val shift = burnInShift(now)
    Box(
        Modifier
            .fillMaxSize()
            .background(Hc.colors.paper)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    lastTouch = graph.clock.now()
                    if (!awake) wakeUntil = lastTouch.plusMillis(IDLE_MS)
                }
            },
    ) {
        if (!awake) {
            NightScreen(now, state)
            return@Box
        }
        val type = rememberDisplayType(settings.sizePercent)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDisplayType provides type, LocalDensity provides Density(density.density, fontScale = 1f)) {
            Box(Modifier.fillMaxSize().offset { IntOffset(shift.first, shift.second) }) {
                when (val current = screen) {
                    DisplayScreen.Board -> Board(graph, state, now, view, { view = it }, onOpen = { screen = DisplayScreen.Detail(it) }, onAdd = { sheet = SheetMode.New(now.atZone(state.zone).toLocalDate()) }, onSettings = { screen = DisplayScreen.Settings(SettingsRoute.Index) })
                    is DisplayScreen.Detail -> CompositionLocalProvider(LocalDensity provides density) {
                        dev.housecalendar.app.phone.EventDetailScreen(graph, state, current.ref, onBack = { screen = DisplayScreen.Board }, onEdit = { sheet = SheetMode.Edit(current.ref) }, onPlan = { plan, deleted ->
                            (plan as? dev.housecalendar.schedule.Plan.Record)?.payloads?.let { payloads -> scope.launch { graph.record(payloads) } }
                            if (deleted != null) screen = DisplayScreen.Board
                        })
                    }
                    is DisplayScreen.Settings -> CompositionLocalProvider(LocalDensity provides density) {
                        SettingsScreen(graph, state, now, current.page, onBack = { screen = if (current.page == SettingsRoute.Index) DisplayScreen.Board else DisplayScreen.Settings(SettingsRoute.Index) }, onOpen = { screen = DisplayScreen.Settings(it) }, onRecentlyDeleted = { screen = DisplayScreen.Deleted })
                    }
                    DisplayScreen.Deleted -> CompositionLocalProvider(LocalDensity provides density) {
                        dev.housecalendar.app.phone.RecentlyDeletedScreen(graph, state, onBack = { screen = DisplayScreen.Settings(SettingsRoute.Index) })
                    }
                }
            }
            sheet?.let { mode ->
                CompositionLocalProvider(LocalDensity provides Density(density.density * DISPLAY_SHEET_SCALE, fontScale = 1f)) {
                    AddSheet(graph, state, now, mode, displaySized = true, onDismiss = { sheet = null }, onRecorded = { sheet = null })
                }
            }
        }
    }
}

enum class DisplayView { WEEK, TODAY, MONTH }

/** The board: the date and clock to read from across the room, the three views, Add and settings. */
@Composable
private fun Board(
    graph: AppGraph,
    state: CalendarState,
    now: Instant,
    view: DisplayView,
    onView: (DisplayView) -> Unit,
    onOpen: (OccurrenceRef) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    val type = LocalDisplayType.current
    val text = LocalDateText.current
    val zoned = now.atZone(state.zone)
    val history by graph.history.collectAsState()
    val syncing by graph.sync.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = Space.displayGutter, vertical = Space.m)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text.dayLong(zoned.toLocalDate()), style = type.glanceDisplay, color = Hc.colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text(Words.time(zoned.toLocalTime()), style = type.glanceTime, color = Hc.colors.ink)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(syncLine(syncing, history, state, now), style = type.detailCaption, color = Hc.colors.inkMuted, maxLines = 1, modifier = Modifier.weight(1f))
            Segmented(
                listOf(DisplayView.WEEK to stringResource(R.string.view_week), DisplayView.TODAY to stringResource(R.string.view_today), DisplayView.MONTH to stringResource(R.string.view_month)),
                view, onView, Modifier.width(SEGMENTS_WIDTH),
            )
            SettingsGate(onSettings)
        }
        Box(Modifier.weight(1f)) {
            Crossfade(view, animationSpec = tween(VIEW_FADE_MS), label = "view") { shown ->
                when (shown) {
                    DisplayView.WEEK -> OrganiserView(state, now, onOpen)
                    DisplayView.TODAY -> TodayBoardView(state, now, onOpen)
                    DisplayView.MONTH -> DisplayMonthView(state, now, onOpen)
                }
            }
            AddDisc(onAdd, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** DESIGN.md: Add as a 64 dp action disc with a plus, bottom right. */
@Composable
private fun AddDisc(onAdd: () -> Unit, modifier: Modifier) {
    Box(
        modifier.padding(Space.l).size(Space.displayTouch).clip(Radius.full).background(Hc.colors.action).clickable(role = Role.Button, onClickLabel = stringResource(R.string.add), onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) { Icon(HcIcons.Plus, contentDescription = stringResource(R.string.add), tint = Hc.colors.onAction, modifier = Modifier.size(32.dp)) }
}

/**
 * SPEC FR-31: settings need the screen lock when one is set; otherwise a 2-second hold, with a ring that
 * fills while held, so children don't open them by accident.
 */
@Composable
private fun SettingsGate(onOpen: () -> Unit) {
    val context = LocalContext.current
    val secure = remember { ContextCompat.getSystemService(context, KeyguardManager::class.java)?.isDeviceSecure == true }
    var holding by remember { mutableStateOf(false) }
    val lockLabel = stringResource(R.string.unlock_settings)
    val holdLabel = stringResource(R.string.settings_hold)
    Box(
        Modifier.size(Space.displayTouch).clip(Radius.full).pointerInput(secure) {
            awaitEachGesture {
                awaitFirstDown()
                if (secure) {
                    waitForUpOrCancellation()?.let { authenticate(context, lockLabel, onOpen) }
                } else {
                    holding = true
                    val released = withTimeoutOrNull(HOLD_MS) { waitForUpOrCancellation() }
                    holding = false
                    if (released == null) onOpen()
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (holding) dev.housecalendar.app.ui.components.PenRing(Space.displayTouch, progress = 1f)
        Icon(HcIcons.Settings, contentDescription = if (secure) lockLabel else holdLabel, tint = Hc.colors.inkMuted)
    }
}

private fun authenticate(context: android.content.Context, title: String, onSuccess: () -> Unit) {
    val activity = context as? FragmentActivity ?: return
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

/** Outside day hours: only a dim clock and the date on the dark ground (SPEC FR-31). */
@Composable
private fun NightScreen(now: Instant, state: CalendarState) {
    val zoned = now.atZone(state.zone)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Words.time(zoned.toLocalTime()), style = Hc.type.headline.copy(fontSize = NIGHT_CLOCK_SP.sp, lineHeight = NIGHT_CLOCK_SP.sp), color = Hc.colors.inkMuted)
        Spacer(Modifier.size(Space.m))
        Text(LocalDateText.current.dayLong(zoned.toLocalDate()), style = Hc.type.title, color = Hc.colors.inkMuted)
    }
}

/** Keeps the screen on (SPEC FR-31, [R5]) and hides the system bars while display mode is shown. */
@Composable
private fun KeepOnAndImmersive(activity: Activity) {
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** NFR-A11Y-4: the type tiers in pixels from the screen's physical density, then in sp at a font scale of 1. */
@Composable
private fun rememberDisplayType(sizePercent: Int): DisplayType {
    val metrics = LocalContext.current.resources.displayMetrics
    return remember(sizePercent, metrics.xdpi, metrics.densityDpi) {
        val ppi = PhysicalType.ppi(metrics.xdpi, metrics.densityDpi)
        fun sp(px: Float) = (px / metrics.density).sp
        DisplayType.of(
            glanceBricolage = sp(PhysicalType.fontPx(PhysicalType.GLANCE_X_HEIGHT_MM, PhysicalType.BRICOLAGE_X_HEIGHT, ppi, sizePercent)),
            glance = sp(PhysicalType.fontPx(PhysicalType.GLANCE_X_HEIGHT_MM, PhysicalType.ATKINSON_X_HEIGHT, ppi, sizePercent)),
            detail = sp(PhysicalType.fontPx(PhysicalType.DETAIL_X_HEIGHT_MM, PhysicalType.ATKINSON_X_HEIGHT, ppi, sizePercent)),
        )
    }
}

/** DESIGN.md "Burn-in": a slow circle of up to 2 px, one step every 10 minutes. */
private fun burnInShift(now: Instant): Pair<Int, Int> {
    val step = (now.epochSecond / BURN_IN_STEP_SECONDS) % BURN_IN_STEPS
    val angle = 2 * Math.PI * step / BURN_IN_STEPS
    return (BURN_IN_PX * cos(angle)).roundToInt() to (BURN_IN_PX * sin(angle)).roundToInt()
}

private const val IDLE_MS = 120_000L
private const val HOLD_MS = 2_000L
private const val NIGHT_BRIGHTNESS = 0.01f
private const val NIGHT_CLOCK_SP = 96
private const val VIEW_FADE_MS = 200
private const val BURN_IN_STEP_SECONDS = 600L
private const val BURN_IN_STEPS = 8
private const val BURN_IN_PX = 2.0
private const val DISPLAY_SHEET_SCALE = 1.25f
private val SEGMENTS_WIDTH = 420.dp
