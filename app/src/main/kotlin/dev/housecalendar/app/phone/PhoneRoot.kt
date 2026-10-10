package dev.housecalendar.app.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.R
import dev.housecalendar.app.model.Draft
import dev.housecalendar.app.settings.SettingsRoute
import dev.housecalendar.app.settings.SettingsScreen
import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.Payload
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.OccurrenceRef
import dev.housecalendar.schedule.Plan
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Where the phone is. The home screen is always at the bottom of the stack. */
sealed interface PhoneRoute {
    data object Home : PhoneRoute

    data class Detail(val ref: OccurrenceRef) : PhoneRoute

    data object NeedsSomeone : PhoneRoute

    data object RecentlyDeleted : PhoneRoute

    data class Settings(val page: SettingsRoute) : PhoneRoute
}

/** What the add sheet is doing: a new event (optionally prefilled), or editing an occurrence. */
sealed interface SheetMode {
    data class New(val day: LocalDate, val prefill: Draft? = null) : SheetMode

    data class Edit(val ref: OccurrenceRef) : SheetMode
}

/** The phone's screens (SPEC FR-29; DESIGN.md "Screens and what wins each one"). */
@Composable
fun PhoneRoot(graph: AppGraph, state: CalendarState, now: Instant, link: OccurrenceRef?, onLinkHandled: () -> Unit) {
    val stack = remember { mutableStateListOf<PhoneRoute>(PhoneRoute.Home) }
    var sheet by remember { mutableStateOf<SheetMode?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(link) {
        if (link != null) {
            stack.add(PhoneRoute.Detail(link))
            onLinkHandled()
        }
    }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    fun open(route: PhoneRoute) = stack.add(route)
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Records a plan; a deletion offers undo with its inverse writes (SPEC FR-17: nothing is erased). */
    fun recordPlan(plan: Plan, undoLabel: String? = null) {
        val payloads = (plan as? Plan.Record)?.payloads ?: return
        scope.launch {
            graph.record(payloads)
            if (undoLabel != null) offerUndo(snackbar, context.getString(R.string.deleted_snack, undoLabel), context.getString(R.string.undo)) {
                graph.record(inverseOfDeletion(payloads))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val route = stack.last()) {
            PhoneRoute.Home -> HomeScreen(
                graph, state, now,
                onOpen = { open(PhoneRoute.Detail(it)) },
                onAdd = { day -> sheet = SheetMode.New(day) },
                onNeedsSomeone = { open(PhoneRoute.NeedsSomeone) },
                onSettings = { open(PhoneRoute.Settings(SettingsRoute.Index)) },
            )
            is PhoneRoute.Detail -> EventDetailScreen(
                graph, state, route.ref,
                onBack = ::back,
                onEdit = { sheet = SheetMode.Edit(route.ref) },
                onPlan = { plan, deletedTitle ->
                    recordPlan(plan, deletedTitle)
                    if (deletedTitle != null) back()
                },
            )
            PhoneRoute.NeedsSomeone -> NeedsSomeoneScreen(graph, state, now, onBack = ::back, onOpen = { open(PhoneRoute.Detail(it)) }, onPlan = { recordPlan(it) })
            PhoneRoute.RecentlyDeleted -> RecentlyDeletedScreen(graph, state, onBack = ::back)
            is PhoneRoute.Settings -> SettingsScreen(
                graph, state, now, route.page,
                onBack = ::back,
                onOpen = { page -> open(PhoneRoute.Settings(page)) },
                onRecentlyDeleted = { open(PhoneRoute.RecentlyDeleted) },
            )
        }
        sheet?.let { mode ->
            AddSheet(graph, state, now, mode, displaySized = false, onDismiss = { sheet = null }, onRecorded = { sheet = null })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }
}

/** Undo for a deletion: the same registers written back (SPEC FR-16, FR-17). */
internal fun inverseOfDeletion(payloads: List<Payload>): List<Payload> = payloads.mapNotNull { payload ->
    when (payload) {
        is EventEdit -> if (payload.cancelled == true) EventEdit(payload.eventId, occurrence = payload.occurrence, cancelled = false) else null
        is EventChange -> if (payload.ended == true) EventChange(payload.eventId, from = payload.from, ended = false) else null
        is EventUpsert -> if (payload.deleted == true) EventUpsert(payload.eventId, deleted = false) else null
        else -> null
    }
}

internal suspend fun offerUndo(host: SnackbarHostState, message: String, action: String, undo: suspend () -> Unit) {
    if (host.showSnackbar(message, actionLabel = action, withDismissAction = true) == SnackbarResult.ActionPerformed) undo()
}
