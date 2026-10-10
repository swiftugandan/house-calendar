package dev.housecalendar.app.onboarding

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.KeyState
import dev.housecalendar.app.R
import dev.housecalendar.app.calendar.CalendarActions
import dev.housecalendar.app.calendar.NewMember
import dev.housecalendar.app.model.Marks
import dev.housecalendar.app.model.PersonMark
import dev.housecalendar.app.settings.BackupPage
import dev.housecalendar.app.sync.PairingCode
import dev.housecalendar.app.sync.SyncState
import dev.housecalendar.app.ui.components.ChoiceRow
import dev.housecalendar.app.ui.components.Field
import dev.housecalendar.app.ui.components.OutcomeButton
import dev.housecalendar.app.ui.components.PenRing
import dev.housecalendar.app.ui.components.PersonDisc
import dev.housecalendar.app.ui.components.QuietButton
import dev.housecalendar.app.ui.components.SectionLabel
import dev.housecalendar.app.ui.components.Segmented
import dev.housecalendar.app.ui.components.TopBar
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.components.HcCard
import dev.housecalendar.app.ui.components.colourRail
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.CalendarIds
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.data.PendingJoin
import dev.housecalendar.nearby.NearbyPermissions
import dev.housecalendar.schedule.CalendarState
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

private enum class Step { WELCOME, FAMILY, PEOPLE, DEVICE, JOIN }

/** A person typed during setup, before anything is recorded. */
private data class Draftee(val name: String, val kind: MemberKind)

/** SPEC FR-1, FR-4, FR-5: start a family, or join one from another device. */
@Composable
fun OnboardingFlow(graph: AppGraph) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    var familyName by remember { mutableStateOf("") }
    val people = remember { mutableStateListOf(Draftee("", MemberKind.Adult)) }
    BackHandler(enabled = step != Step.WELCOME) {
        step = when (step) {
            Step.PEOPLE -> Step.FAMILY
            Step.DEVICE -> Step.PEOPLE
            else -> Step.WELCOME
        }
    }
    when (step) {
        Step.WELCOME -> Welcome(graph.clock.now().atZone(ZoneId.systemDefault()).dayOfMonth, onStart = { step = Step.FAMILY }, onJoin = { step = Step.JOIN })
        Step.FAMILY -> FamilyName(familyName, { familyName = it }, onBack = { step = Step.WELCOME }, onNext = { step = Step.PEOPLE })
        Step.PEOPLE -> People(people, onBack = { step = Step.FAMILY }, onNext = { step = Step.DEVICE })
        Step.DEVICE -> ThisDevice(graph, familyName, people.filter { it.name.isNotBlank() }, onBack = { step = Step.PEOPLE })
        Step.JOIN -> Join(graph, onBack = { step = Step.WELCOME })
    }
}

@Composable
private fun Page(title: String?, onBack: (() -> Unit)?, footer: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        if (title != null) TopBar(title, onBack, backLabel = stringResource(R.string.back)) else Spacer(Modifier.statusBarsPadding())
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) { content() }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.gutter)) { footer() }
    }
}

@Composable
private fun Welcome(dayOfMonth: Int, onStart: () -> Unit, onJoin: () -> Unit) {
    Page(title = null, onBack = null, footer = {
        OutcomeButton(stringResource(R.string.welcome_start), onStart)
        Spacer(Modifier.height(Space.m))
        QuietButton(stringResource(R.string.welcome_join), onJoin, Modifier.fillMaxWidth())
    }) {
        Spacer(Modifier.height(Space.xl))
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Text(dayOfMonth.toString(), style = Hc.type.largeTitle, color = Hc.colors.ink)
            PenRing(64.dp)
        }
        Text(stringResource(R.string.welcome_title), style = Hc.type.largeTitle, color = Hc.colors.ink)
        Text(stringResource(R.string.welcome_body), style = Hc.type.body, color = Hc.colors.inkMuted)
        WelcomeSamples()
    }
}

/** The product, shown rather than described: three entries as the app draws them, in three family colours. */
@Composable
private fun WelcomeSamples() {
    val colors = Hc.colors
    val samples = listOf(
        Triple(R.string.welcome_sample_1_title, R.string.welcome_sample_1_detail, listOf(colors.member(0))),
        Triple(R.string.welcome_sample_2_title, R.string.welcome_sample_2_detail, listOf(colors.member(3))),
        Triple(R.string.welcome_sample_3_title, R.string.welcome_sample_3_detail, (0 until SAMPLE_FAMILY).map { colors.member(it) }),
    )
    Column(Modifier.fillMaxWidth().padding(top = Space.s).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        samples.forEachIndexed { index, (title, detail, people) ->
            val fill = if (people.size > 1) colors.band else people.first().tint
            HcCard(Modifier.fillMaxWidth().padding(start = (index * SAMPLE_STEP).dp), color = fill) {
                Column(Modifier.fillMaxWidth().colourRail(people.map { it.fill }).padding(start = 22.dp, end = Space.l, top = Space.m, bottom = Space.m)) {
                    Text(stringResource(title), style = Hc.type.title, color = colors.ink)
                    Text(stringResource(detail), style = Hc.type.caption, color = colors.inkMuted)
                }
            }
        }
    }
}

private const val SAMPLE_FAMILY = 4
private const val SAMPLE_STEP = 14

@Composable
private fun FamilyName(name: String, onName: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Page(stringResource(R.string.settings_family), onBack, footer = {
        OutcomeButton(stringResource(R.string.continue_), onNext, enabled = name.isNotBlank())
    }) {
        Field(name, onName, stringResource(R.string.family_name_hint), label = stringResource(R.string.family_name))
    }
}

@Composable
private fun People(people: MutableList<Draftee>, onBack: () -> Unit, onNext: () -> Unit) {
    Page(stringResource(R.string.people_title), onBack, footer = {
        OutcomeButton(stringResource(R.string.continue_), onNext, enabled = people.any { it.name.isNotBlank() })
    }) {
        Text(stringResource(R.string.people_body), style = Hc.type.body, color = Hc.colors.inkMuted)
        people.forEachIndexed { index, person ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                val mark = PersonMark(CalendarIds.randomMember(0), person.name.ifBlank { " " }, person.name.trim().take(1).uppercase(Locale.ROOT), index)
                PersonDisc(mark, 40.dp, describe = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Field(person.name, { people[index] = person.copy(name = it) }, stringResource(R.string.person_name_hint))
                    Segmented(
                        listOf(MemberKind.Adult to stringResource(R.string.adult), MemberKind.Child to stringResource(R.string.child)),
                        person.kind, { people[index] = person.copy(kind = it) },
                    )
                }
            }
        }
        QuietButton(stringResource(R.string.add_another), { people += Draftee("", MemberKind.Child) }, icon = HcIcons.Plus)
    }
}

@Composable
private fun ThisDevice(graph: AppGraph, familyName: String, people: List<Draftee>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf<DeviceRole>(DeviceRole.Phone) }
    var owner by remember { mutableStateOf(people.indexOfFirst { it.kind == MemberKind.Adult }.coerceAtLeast(0)) }
    val phoneDefault = stringResource(R.string.device_name_phone_default, people.getOrNull(owner)?.name?.trim().orEmpty())
    val displayDefault = stringResource(R.string.device_name_display_default)
    var name by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val shownName = name ?: if (role == DeviceRole.Display) displayDefault else phoneDefault
    Page(stringResource(R.string.this_device_title), onBack, footer = {
        OutcomeButton(stringResource(R.string.create_family), enabled = !busy && shownName.isNotBlank(), onClick = {
            busy = true
            val members = people.map { NewMember(CalendarIds.randomMember(graph.clock.now().toEpochMilli()), it.name, it.kind) }
            val weekStart = WeekFields.of(Locale.getDefault()).firstDayOfWeek.takeIf { it == DayOfWeek.SUNDAY } ?: DayOfWeek.MONDAY
            graph.createFamily(
                familyName, ZoneId.systemDefault(), weekStart, members, shownName, role,
                owner = if (role == DeviceRole.Phone) members.getOrNull(owner)?.id else null,
            )
        })
    }) {
        RolePicker(role) { role = it }
        if (role == DeviceRole.Phone) {
            SectionLabel(stringResource(R.string.whose_phone))
            people.forEachIndexed { index, person ->
                ChoiceRow(person.name, index == owner, { owner = index }, leading = {
                    PersonDisc(PersonMark(CalendarIds.randomMember(0), person.name, person.name.trim().take(1).uppercase(Locale.ROOT), index), 32.dp, describe = false)
                })
            }
        }
        Field(shownName, { name = it }, "", label = stringResource(R.string.device_name))
    }
}

@Composable
private fun RolePicker(role: DeviceRole, onRole: (DeviceRole) -> Unit) {
    ChoiceRow(stringResource(R.string.role_phone), role == DeviceRole.Phone, { onRole(DeviceRole.Phone) }, explanation = stringResource(R.string.role_phone_body), leading = {
        androidx.compose.material3.Icon(HcIcons.Phone, null, tint = Hc.colors.ink)
    })
    ChoiceRow(stringResource(R.string.role_display), role == DeviceRole.Display, { onRole(DeviceRole.Display) }, explanation = stringResource(R.string.role_display_body), leading = {
        androidx.compose.material3.Icon(HcIcons.Display, null, tint = Hc.colors.ink)
    })
}

@Composable
private fun Join(graph: AppGraph, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf<DeviceRole>(DeviceRole.Phone) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    val defaultName = if (role == DeviceRole.Display) stringResource(R.string.device_name_display_default) else stringResource(R.string.role_phone_short)
    fun use(text: String) {
        val decoded = PairingCode.decode(text)
        if (decoded == null) {
            error = R.string.join_bad_code
            return
        }
        graph.join(decoded.first, decoded.second, PendingJoin(name.ifBlank { defaultName }.trim(), role))
    }
    Page(stringResource(R.string.join_title), onBack, footer = {
        OutcomeButton(stringResource(R.string.join_scan), icon = HcIcons.Qr, onClick = {
            val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let { code = it; use(it) } }
                .addOnFailureListener { error = R.string.join_scanner_unavailable }
        })
    }) {
        RolePicker(role) { role = it }
        Field(name, { name = it }, defaultName, label = stringResource(R.string.device_name))
        Text(stringResource(R.string.join_body), style = Hc.type.body, color = Hc.colors.inkMuted)
        SectionLabel(stringResource(R.string.join_paste))
        Field(code, { code = it; error = null }, "hc1:…", capitalize = false)
        QuietButton(stringResource(R.string.join_use_code), { use(code) }, enabled = code.isNotBlank())
        error?.let { Text(stringResource(it), style = Hc.type.body, color = Hc.colors.ink) }
    }
}

/** A key from a pairing code but no family yet: the first sync brings the calendar over (SPEC FR-4). */
@Composable
fun JoiningFlow(graph: AppGraph) {
    val key by graph.key.collectAsState()
    val state by graph.sync.state.collectAsState()
    val held = key as? KeyState.Held
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (held != null) graph.sync.startManual(graph.bluetooth, held.family, held.key, graph.deviceName())
    }
    var restoring by remember { mutableStateOf(false) }
    Page(stringResource(R.string.joining_title), onBack = null, footer = {
        QuietButton(stringResource(R.string.joining_bluetooth), {
            permissions.launch(NearbyPermissions.required(Build.VERSION.SDK_INT).toTypedArray())
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.s))
        QuietButton(stringResource(R.string.joining_restore), { restoring = !restoring }, Modifier.fillMaxWidth())
    }) {
        if (restoring && held != null) {
            BackupPage(graph, held.family, graph.clock.now().atZone(ZoneId.systemDefault()).toLocalDate(), allowExport = false)
        }
        Text(stringResource(R.string.joining_body), style = Hc.type.body, color = Hc.colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            CircularProgressIndicator(Modifier.widthIn(max = 24.dp).height(24.dp), color = Hc.colors.action, strokeWidth = 2.dp)
            Text(
                when (val current = state) {
                    is SyncState.Syncing -> stringResource(R.string.sync_running, current.peerName)
                    is SyncState.Failed -> stringResource(R.string.sync_failed_link)
                    else -> stringResource(R.string.sync_searching)
                },
                style = Hc.type.body, color = Hc.colors.inkMuted,
            )
        }
    }
}

/** SPEC FR-5: a phone that joined says whose it is, once the family's members have arrived. */
@Composable
fun WhosePhone(graph: AppGraph, state: CalendarState) {
    val scope = rememberCoroutineScope()
    Page(stringResource(R.string.joining_whose), onBack = null, footer = {}) {
        state.activeMembers().forEach { member ->
            val mark = Marks.mark(member)
            ChoiceRow(member.name, false, {
                scope.launch {
                    graph.record(listOf(CalendarActions.nameDevice(graph.deviceId, graph.deviceName().let { current ->
                        if (current == graph.appContext.getString(R.string.role_phone_short)) graph.appContext.getString(R.string.device_name_phone_default, member.name) else current
                    }, DeviceRole.Phone, member.id)))
                }
            }, leading = { PersonDisc(mark, 32.dp, describe = false) })
        }
    }
}
