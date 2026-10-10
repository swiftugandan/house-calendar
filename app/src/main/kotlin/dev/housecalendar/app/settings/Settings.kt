package dev.housecalendar.app.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.KeyState
import dev.housecalendar.app.R
import dev.housecalendar.app.calendar.CalendarActions
import dev.housecalendar.app.model.Marks
import dev.housecalendar.app.phone.DatePickerSheet
import dev.housecalendar.app.phone.TimePickerSheet
import dev.housecalendar.app.phone.syncLine
import dev.housecalendar.app.sync.PairingCode
import dev.housecalendar.app.sync.SyncFailure
import dev.housecalendar.app.sync.SyncState
import dev.housecalendar.app.ui.components.ChoiceRow
import dev.housecalendar.app.ui.components.Field
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.components.OutcomeButton
import dev.housecalendar.app.ui.components.PersonDisc
import dev.housecalendar.app.ui.components.QuietButton
import dev.housecalendar.app.ui.components.Rule
import dev.housecalendar.app.ui.components.SectionLabel
import dev.housecalendar.app.ui.components.Segmented
import dev.housecalendar.app.ui.components.TopBar
import dev.housecalendar.app.ui.components.Words
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.CalendarIds
import dev.housecalendar.contracts.DeviceRemoved
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.FamilySettings
import dev.housecalendar.contracts.HolidayId
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.data.DisplayHome
import dev.housecalendar.data.ReminderChoice
import dev.housecalendar.data.SyncHistoryEntry
import dev.housecalendar.nearby.NearbyPermissions
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.MemberView
import dev.housecalendar.sync.FamilyKey
import dev.housecalendar.sync.SyncOutcome
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SettingsRoute { Index, Family, People, Holidays, Devices, Reminders, Display, Diagnostics }

/** Settings: the family's shared settings, and this device's own (SAD §3.5 `DeviceSettingsStore`). */
@Composable
fun SettingsScreen(
    graph: AppGraph,
    state: CalendarState,
    now: Instant,
    page: SettingsRoute,
    onBack: () -> Unit,
    onOpen: (SettingsRoute) -> Unit,
    onRecentlyDeleted: () -> Unit,
) {
    val title = when (page) {
        SettingsRoute.Index -> stringResource(R.string.settings)
        SettingsRoute.Family -> stringResource(R.string.settings_family)
        SettingsRoute.People -> stringResource(R.string.settings_people)
        SettingsRoute.Holidays -> stringResource(R.string.settings_holidays)
        SettingsRoute.Devices -> stringResource(R.string.settings_devices)
        SettingsRoute.Reminders -> stringResource(R.string.settings_reminders)
        SettingsRoute.Display -> stringResource(R.string.settings_display)
        SettingsRoute.Diagnostics -> stringResource(R.string.settings_diagnostics)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack, backLabel = stringResource(R.string.back))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.gutter, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            when (page) {
                SettingsRoute.Index -> Index(graph, state, onOpen, onRecentlyDeleted)
                SettingsRoute.Family -> FamilyPage(graph, state)
                SettingsRoute.People -> PeoplePage(graph, state)
                SettingsRoute.Holidays -> HolidaysPage(graph, state, now)
                SettingsRoute.Devices -> DevicesPage(graph, state, now)
                SettingsRoute.Reminders -> RemindersPage(graph)
                SettingsRoute.Display -> DisplayPage(graph)
                SettingsRoute.Diagnostics -> DiagnosticsPage(graph, state)
            }
        }
    }
}

@Composable
private fun Index(graph: AppGraph, state: CalendarState, onOpen: (SettingsRoute) -> Unit, onRecentlyDeleted: () -> Unit) {
    if (state.diagnostics.newestSchema > dev.housecalendar.contracts.Op.CURRENT_SCHEMA || state.diagnostics.unknownByType.isNotEmpty()) {
        Text(stringResource(R.string.newer_version_notice), style = Hc.type.body, color = Hc.colors.ink)
    }
    val display = graph.role() == DeviceRole.Display
    LinkRow(stringResource(R.string.settings_family), HcIcons.Settings) { onOpen(SettingsRoute.Family) }
    LinkRow(stringResource(R.string.settings_people), HcIcons.People) { onOpen(SettingsRoute.People) }
    LinkRow(stringResource(R.string.settings_holidays), HcIcons.Holiday) { onOpen(SettingsRoute.Holidays) }
    LinkRow(stringResource(R.string.settings_devices), HcIcons.Sync) { onOpen(SettingsRoute.Devices) }
    if (!display) LinkRow(stringResource(R.string.settings_reminders), HcIcons.Bell) { onOpen(SettingsRoute.Reminders) }
    if (display) LinkRow(stringResource(R.string.settings_display), HcIcons.Display) { onOpen(SettingsRoute.Display) }
    LinkRow(stringResource(R.string.recently_deleted), HcIcons.Undo, onRecentlyDeleted)
    LinkRow(stringResource(R.string.settings_diagnostics), HcIcons.Notes) { onOpen(SettingsRoute.Diagnostics) }
}

@Composable
private fun LinkRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(icon, contentDescription = null, tint = Hc.colors.ink)
        Text(label, style = Hc.type.title, color = Hc.colors.ink, modifier = Modifier.weight(1f))
        Icon(HcIcons.Forward, contentDescription = null, tint = Hc.colors.inkMuted)
    }
    Rule()
}

/** SPEC FR-2: name, timezone and week start, each a last-writer-wins setting. */
@Composable
private fun FamilyPage(graph: AppGraph, state: CalendarState) {
    val scope = rememberCoroutineScope()
    var name by remember(state.familyName) { mutableStateOf(state.familyName) }
    Field(name, { name = it }, stringResource(R.string.family_name_hint), label = stringResource(R.string.family_name))
    if (name.isNotBlank() && name.trim() != state.familyName) QuietButton(stringResource(R.string.save), { scope.launch { graph.record(listOf(FamilySettings(name = name.trim()))) } })
    SectionLabel(stringResource(R.string.week_starts))
    Segmented(
        listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY).map { it to Words.weekdayName(it) },
        state.weekStart, { scope.launch { graph.record(listOf(FamilySettings(weekStart = it))) } },
    )
    SectionLabel(stringResource(R.string.timezone))
    Text(state.zone.id, style = Hc.type.body, color = Hc.colors.ink)
    val here = ZoneId.systemDefault()
    if (here != state.zone) QuietButton(here.id, { scope.launch { graph.record(listOf(FamilySettings(zone = here))) } })
}

/** SPEC FR-7: add, rename, recolour, archive. Archiving never removes anyone from any occurrence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeoplePage(graph: AppGraph, state: CalendarState) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<MemberView?>(null) }
    var newName by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf<MemberKind>(MemberKind.Child) }
    state.allMembers().forEach { member ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { editing = member },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            PersonDisc(Marks.mark(member), 36.dp, describe = false)
            Column(Modifier.weight(1f)) {
                Text(member.name, style = Hc.type.title, color = if (member.archived) Hc.colors.inkMuted else Hc.colors.ink)
                Text(
                    listOfNotNull(stringResource(if (member.kind == MemberKind.Child) R.string.child else R.string.adult), if (member.archived) stringResource(R.string.archived) else null).joinToString(" · "),
                    style = Hc.type.caption, color = Hc.colors.inkMuted,
                )
            }
            Icon(HcIcons.Edit, contentDescription = stringResource(R.string.rename), tint = Hc.colors.inkMuted)
        }
        Rule()
    }
    if (state.activeMembers().size < Limits.MAX_MEMBERS) {
        SectionLabel(stringResource(R.string.add_another))
        Field(newName, { newName = it }, stringResource(R.string.person_name_hint))
        Segmented(listOf(MemberKind.Adult to stringResource(R.string.adult), MemberKind.Child to stringResource(R.string.child)), newKind, { newKind = it })
        QuietButton(stringResource(R.string.add_person, newName.trim()), {
            val payload = CalendarActions.addMember(state, CalendarIds.randomMember(graph.clock.now().toEpochMilli()), newName, newKind)
            scope.launch { graph.record(listOf(payload)) }
            newName = ""
        }, enabled = newName.isNotBlank(), icon = HcIcons.Plus)
    }
    editing?.let { member ->
        var name by remember(member) { mutableStateOf(member.name) }
        var colour by remember(member) { mutableStateOf(member.colorIndex) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = Hc.colors.surface,
            title = { Text(member.name, style = Hc.type.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    Field(name, { name = it }, stringResource(R.string.person_name_hint), label = stringResource(R.string.rename))
                    SectionLabel(stringResource(R.string.colour))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        (0 until Limits.MEMBER_COLOURS).forEach { index ->
                            Box(
                                Modifier.size(Space.touch).clip(Radius.full).background(Hc.colors.member(index).fill)
                                    .then(if (index == colour) Modifier.border(3.dp, Hc.colors.ink, Radius.full) else Modifier)
                                    .clickable(role = Role.RadioButton) { colour = index },
                            )
                        }
                    }
                    QuietButton(stringResource(if (member.archived) R.string.unarchive else R.string.archive), {
                        scope.launch { graph.record(listOf(MemberUpsert(member.id, archived = !member.archived))) }
                        editing = null
                    })
                }
            },
            confirmButton = {
                TextButton({
                    scope.launch { graph.record(listOf(MemberUpsert(member.id, name = name.trim().takeIf { it.isNotEmpty() && it != member.name }, colorIndex = colour.takeIf { it != member.colorIndex }))) }
                    editing = null
                }) { Text(stringResource(R.string.save), color = Hc.colors.action) }
            },
            dismissButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel), color = Hc.colors.ink) } },
        )
    }
}

/** SPEC FR-28: the family's holidays; term-time activities skip them. */
@Composable
private fun HolidaysPage(graph: AppGraph, state: CalendarState, now: Instant) {
    val scope = rememberCoroutineScope()
    val text = LocalDateText.current
    var editing by remember { mutableStateOf<HolidayEdit?>(null) }
    val holidays = state.activeHolidays()
    if (holidays.isEmpty()) Text(stringResource(R.string.holidays_empty), style = Hc.type.body, color = Hc.colors.inkMuted)
    holidays.forEach { holiday ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { editing = HolidayEdit(holiday.id, holiday.name, holiday.first, holiday.last) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(holiday.name, style = Hc.type.title, color = Hc.colors.ink)
                Text(stringResource(R.string.time_range, text.dayShort(holiday.first), text.dayShort(holiday.last)), style = Hc.type.caption, color = Hc.colors.inkMuted)
            }
            Icon(HcIcons.Edit, contentDescription = null, tint = Hc.colors.inkMuted)
        }
        Rule()
    }
    val today = now.atZone(state.zone).toLocalDate()
    QuietButton(stringResource(R.string.holiday_add), { editing = HolidayEdit(null, "", today, today.plusDays(4)) }, icon = HcIcons.Plus)
    editing?.let { edit ->
        var name by remember(edit) { mutableStateOf(edit.name) }
        var first by remember(edit) { mutableStateOf(edit.first) }
        var last by remember(edit) { mutableStateOf(edit.last) }
        var picking by remember { mutableStateOf<Boolean?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = Hc.colors.surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    Field(name, { name = it }, stringResource(R.string.holiday_name_hint))
                    QuietButton(stringResource(R.string.holiday_from, text.dayShort(first)), { picking = true })
                    QuietButton(stringResource(R.string.holiday_to, text.dayShort(last)), { picking = false })
                    if (edit.id != null) QuietButton(stringResource(R.string.delete), {
                        scope.launch { graph.record(listOf(HolidayUpsert(edit.id, deleted = true))) }
                        editing = null
                    }, icon = HcIcons.Delete)
                }
            },
            confirmButton = {
                TextButton({
                    val id = edit.id ?: CalendarIds.randomHoliday(graph.clock.now().toEpochMilli())
                    scope.launch { graph.record(listOf(HolidayUpsert(id, name = name.trim(), first = first, last = maxOf(first, last), deleted = false))) }
                    editing = null
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save), color = Hc.colors.action) }
            },
            dismissButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel), color = Hc.colors.ink) } },
        )
        when (picking) {
            true -> DatePickerSheet(first, { first = it; if (last < it) last = it }) { picking = null }
            false -> DatePickerSheet(last, { last = it }) { picking = null }
            null -> Unit
        }
    }
}

private data class HolidayEdit(val id: HolidayId?, val name: String, val first: LocalDate, val last: LocalDate)

/** SPEC FR-4 to FR-6, FR-41, FR-45: this device, the others with when they last synced, pairing and removal. */
@Composable
private fun DevicesPage(graph: AppGraph, state: CalendarState, now: Instant) {
    val scope = rememberCoroutineScope()
    val key by graph.key.collectAsState()
    val history by graph.history.collectAsState()
    val syncState by graph.sync.state.collectAsState()
    val held = key as? KeyState.Held
    var showCode by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (held != null) graph.sync.startManual(graph.bluetooth, held.family, held.key, graph.deviceName())
    }
    Text(syncLine(syncState, history, state, now), style = Hc.type.body, color = Hc.colors.ink)
    SyncStatus(syncState) { graph.sync.reset(); graph.resumeAutomatic() }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        QuietButton(stringResource(R.string.sync_now), { if (held != null) graph.sync.startManual(graph.localNetwork, held.family, held.key, graph.deviceName()) }, icon = HcIcons.Sync)
        QuietButton(stringResource(R.string.sync_bluetooth), { permissions.launch(NearbyPermissions.required(Build.VERSION.SDK_INT).toTypedArray()) })
    }
    Rule()
    DeviceRow(state.devices[graph.deviceId]?.name?.value ?: graph.deviceName(), graph.role(), stringResource(R.string.this_device), null)
    state.devices.keys.filter { it != graph.deviceId && it !in state.removedDevices }.sortedBy { state.devices[it]?.name?.value.orEmpty() }.forEach { id ->
        val record = state.devices.getValue(id)
        val last = history.firstOrNull { it.peer == id && it.result == SyncHistoryEntry.Result.COMPLETED }
        DeviceRow(
            record.name?.value.orEmpty().ifBlank { stringResource(R.string.a_device) }, record.role?.value ?: DeviceRole.Phone,
            last?.let { stringResource(R.string.last_synced, Words.relative(it.endedAt.toInstant(), now)) } ?: stringResource(R.string.not_synced_yet),
            onRemove = {
                val family = held?.family ?: return@DeviceRow
                scope.launch {
                    graph.record(listOf(DeviceRemoved(id)))
                    graph.store(family, FamilyKey.generate())
                }
            },
        )
    }
    Rule()
    OutcomeButton(stringResource(R.string.show_code), { showCode = !showCode }, icon = HcIcons.Qr)
    if (showCode && held != null) {
        val code = PairingCode.encode(held.family, held.key)
        Text(stringResource(R.string.show_code_body), style = Hc.type.body, color = Hc.colors.inkMuted)
        val image = remember(code) { PairingCode.qr(code, QR_PX) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(image, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(240.dp).clip(Radius.small))
        }
        Text(code, style = Hc.type.caption.copy(fontFamily = FontFamily.Monospace), color = Hc.colors.ink)
    }
}

@Composable
private fun DeviceRow(name: String, role: DeviceRole, status: String, onRemove: (() -> Unit)?) {
    var confirming by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Icon(if (role == DeviceRole.Display) HcIcons.Display else HcIcons.Phone, contentDescription = null, tint = Hc.colors.ink)
        Column(Modifier.weight(1f)) {
            Text(name, style = Hc.type.title, color = Hc.colors.ink)
            Text(listOf(stringResource(if (role == DeviceRole.Display) R.string.role_display_short else R.string.role_phone_short), status).joinToString(" · "), style = Hc.type.caption, color = Hc.colors.inkMuted)
        }
        if (onRemove != null) dev.housecalendar.app.ui.components.IconAction(HcIcons.Delete, stringResource(R.string.remove_device), { confirming = true }, tint = Hc.colors.inkMuted)
    }
    if (confirming && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Hc.colors.surface,
            title = { Text(stringResource(R.string.remove_device), style = Hc.type.title) },
            text = { Text(stringResource(R.string.remove_device_body), style = Hc.type.body) },
            confirmButton = { TextButton({ confirming = false; onRemove() }) { Text(stringResource(R.string.remove_device), color = Hc.colors.ink) } },
            dismissButton = { TextButton({ confirming = false }) { Text(stringResource(R.string.cancel), color = Hc.colors.ink) } },
        )
    }
}

@Composable
private fun SyncStatus(state: SyncState, onDone: () -> Unit) {
    val message = when (state) {
        SyncState.Idle -> return
        is SyncState.Searching -> stringResource(R.string.sync_searching)
        is SyncState.Syncing -> stringResource(R.string.sync_running, state.peerName)
        is SyncState.Failed -> stringResource(if (state.failure == SyncFailure.LINK_UNAVAILABLE) R.string.sync_failed_bluetooth else R.string.sync_failed_link)
        is SyncState.Finished -> when (state.outcome) {
            is SyncOutcome.Completed -> stringResource(R.string.sync_done_with, state.peerName) + " · " +
                androidx.compose.ui.res.pluralStringResource(R.plurals.sync_new_ops, state.received, state.received)
            is SyncOutcome.Rejected -> stringResource(R.string.sync_rejected)
            is SyncOutcome.Interrupted -> stringResource(R.string.sync_failed_link)
        }
    }
    Text(message, style = Hc.type.body, color = Hc.colors.ink)
    if (state is SyncState.Finished || state is SyncState.Failed) QuietButton(stringResource(R.string.sync_done), onDone)
}

/** SPEC FR-37: whose events this phone reminds about. */
@Composable
private fun RemindersPage(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val settings by graph.settings.collectAsState()
    val current = settings?.reminders ?: ReminderChoice.MINE
    listOf(ReminderChoice.MINE to R.string.reminders_mine, ReminderChoice.EVERYONE to R.string.reminders_everyone, ReminderChoice.NONE to R.string.reminders_off).forEach { (choice, label) ->
        ChoiceRow(stringResource(label), choice == current, { scope.launch { graph.settingsStore.update { it.copy(reminders = choice) } } })
    }
}

/** SPEC FR-31, NFR-A11Y-4: the display's day hours, idle home view, and the text size check. */
@Composable
private fun DisplayPage(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val settings by graph.settings.collectAsState()
    val current = settings ?: return
    var picking by remember { mutableStateOf<Boolean?>(null) }
    SectionLabel(stringResource(R.string.display_day_hours))
    Text(stringResource(R.string.display_day_hours_body), style = Hc.type.caption, color = Hc.colors.inkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        QuietButton(Words.time(current.dayStarts), { picking = true })
        QuietButton(Words.time(current.dayEnds), { picking = false })
    }
    SectionLabel(stringResource(R.string.display_home))
    Segmented(
        listOf(DisplayHome.WEEK to stringResource(R.string.view_week), DisplayHome.TODAY to stringResource(R.string.view_today), DisplayHome.MONTH to stringResource(R.string.view_month)),
        current.displayHome, { choice -> scope.launch { graph.settingsStore.update { it.copy(displayHome = choice) } } },
    )
    SectionLabel(stringResource(R.string.display_size))
    Text(stringResource(R.string.display_size_body), style = Hc.type.caption, color = Hc.colors.inkMuted)
    SizeBar(current.sizePercent)
    Slider(
        value = current.sizePercent.toFloat(), valueRange = MIN_SIZE..MAX_SIZE, steps = SIZE_STEPS,
        onValueChange = { value -> scope.launch { graph.settingsStore.update { it.copy(sizePercent = value.toInt()) } } },
        colors = SliderDefaults.colors(thumbColor = Hc.colors.action, activeTrackColor = Hc.colors.action, inactiveTrackColor = Hc.colors.sunk),
    )
    when (picking) {
        true -> TimePickerSheet(current.dayStarts, { time -> scope.launch { graph.settingsStore.update { it.copy(dayStarts = time) } } }) { picking = null }
        false -> TimePickerSheet(current.dayEnds, { time -> scope.launch { graph.settingsStore.update { it.copy(dayEnds = time) } } }) { picking = null }
        null -> Unit
    }
}

/** A bar 5 cm long at the current scale, to hold a ruler against (DESIGN.md "Display type"). */
@Composable
private fun SizeBar(sizePercent: Int) {
    val metrics = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    val ppi = dev.housecalendar.app.ui.theme.PhysicalType.ppi(metrics.xdpi, metrics.densityDpi)
    val px = FIVE_CM_INCHES * ppi * sizePercent / PERCENT
    val width = with(androidx.compose.ui.platform.LocalDensity.current) { px.toDp() }
    Box(Modifier.size(width, 12.dp).background(Hc.colors.ink))
}

/** NFR-OPS-3: this device, its log, and its last 20 syncs. Nothing is sent anywhere. */
@Composable
private fun DiagnosticsPage(graph: AppGraph, state: CalendarState) {
    val history by graph.history.collectAsState()
    val settings by graph.settings.collectAsState()
    val vector = remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(state) { vector.value = graph.opLog.vector().toString() }
    Mono("device ${graph.deviceId}")
    Mono("role ${graph.role()}  family ${(graph.key.value as? KeyState.Held)?.family}")
    Mono("zone ${state.zone.id}  members ${state.allMembers().size}  events ${state.events.size}  holidays ${state.holidays.size}")
    Mono("vector ${vector.value.orEmpty()}")
    Mono("unknown ${state.diagnostics.unknownByType}  malformed ${state.diagnostics.malformed}  newest schema ${state.diagnostics.newestSchema}")
    Mono("last reminder fired ${settings?.lastReminderFired?.toInstant()}")
    Rule()
    history.forEach { entry ->
        Mono("${entry.startedAt.toInstant()} ${entry.result} peer=${entry.peer?.toString()?.take(8)} sent=${entry.sent} received=${entry.received} ${entry.detail}")
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, style = Hc.type.caption.copy(fontFamily = FontFamily.Monospace), color = Hc.colors.ink)
}

private const val QR_PX = 720
private const val FIVE_CM_INCHES = 50f / 25.4f
private const val PERCENT = 100f
private const val MIN_SIZE = 70f
private const val MAX_SIZE = 150f
private const val SIZE_STEPS = 15
