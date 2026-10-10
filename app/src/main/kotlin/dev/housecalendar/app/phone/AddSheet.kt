package dev.housecalendar.app.phone

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.R
import dev.housecalendar.app.model.Draft
import dev.housecalendar.app.model.DraftModel
import dev.housecalendar.app.model.Ends
import dev.housecalendar.app.model.Marks
import dev.housecalendar.app.model.NeedsSomeoneModel
import dev.housecalendar.app.model.Outcome
import dev.housecalendar.app.model.RepeatKind
import dev.housecalendar.app.ui.components.EmptyDisc
import dev.housecalendar.app.ui.components.EventIcon
import dev.housecalendar.app.ui.components.Field
import dev.housecalendar.app.ui.components.GroupDisc
import dev.housecalendar.app.ui.components.IconAction
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.components.OutcomeButton
import dev.housecalendar.app.ui.components.PersonDisc
import dev.housecalendar.app.ui.components.Rule
import dev.housecalendar.app.ui.components.SectionLabel
import dev.housecalendar.app.ui.components.SwitchRow
import dev.housecalendar.app.ui.components.Words
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.CalendarIds
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Changes
import dev.housecalendar.schedule.EditPlanner
import dev.housecalendar.schedule.OccurrenceRef
import dev.housecalendar.schedule.Plan
import dev.housecalendar.schedule.Recording
import dev.housecalendar.schedule.RepeatRules
import dev.housecalendar.schedule.Scope
import dev.housecalendar.schedule.Suggestions
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The add sheet (SPEC FR-12 to FR-16; DESIGN.md "Add sheet"): what, who, when, repeat, taking, then place,
 * notes and reminders folded away, and one button that says what will happen. On the display it is a
 * centred sheet with larger targets ([displaySized]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(graph: AppGraph, state: CalendarState, now: Instant, mode: SheetMode, displaySized: Boolean, onDismiss: () -> Unit, onRecorded: () -> Unit) {
    val zone = state.zone
    val original = remember(mode) {
        when (mode) {
            is SheetMode.New -> mode.prefill ?: DraftModel.blank(mode.day, LocalDateTime.ofInstant(now, zone))
            is SheetMode.Edit -> DraftModel.fromOccurrence(state, mode.ref)
        }
    }
    if (original == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var draft by remember(mode) { mutableStateOf(original) }
    var scopeFor by remember { mutableStateOf<Changes?>(null) }
    val scope = rememberCoroutineScope()
    val editing = mode as? SheetMode.Edit

    fun confirm() {
        if (editing == null) {
            val event = DraftModel.toEventDraft(draft, state)
            scope.launch {
                graph.record(listOf(Recording.newEvent(CalendarIds.randomEvent(graph.clock.now().toEpochMilli()), event)))
                onRecorded()
            }
            return
        }
        val changes = DraftModel.changes(original, draft)
        if (changes == Changes()) {
            onDismiss()
            return
        }
        val repeats = dev.housecalendar.schedule.Agenda.occurrence(state, editing.ref)?.repeating == true
        if (!repeats) {
            record(graph, scope, EditPlanner.change(state, editing.ref, Scope.ALL, changes), onRecorded)
        } else {
            scopeFor = changes
        }
    }

    val content: @Composable () -> Unit = {
        SheetBody(state, now, draft, { draft = it }, editing != null, displaySized, ::confirm, onDismiss)
    }
    if (displaySized) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(Space.xl).clip(Radius.medium).background(Hc.colors.surface)) { content() }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Hc.colors.surface,
            shape = Radius.sheet,
        ) { content() }
    }

    val pending = scopeFor
    if (pending != null && editing != null) {
        ScopeQuestion(
            state, editing.ref, draft.title.ifBlank { original.title }, offered = offeredScopes(state, editing.ref, pending), delete = false,
            onChoose = { chosen ->
                scopeFor = null
                record(graph, scope, EditPlanner.change(state, editing.ref, chosen, pending), onRecorded)
            },
            onDismiss = { scopeFor = null },
        )
    }
}

private fun record(graph: AppGraph, scope: kotlinx.coroutines.CoroutineScope, plan: Plan, onRecorded: () -> Unit) {
    val payloads = (plan as? Plan.Record)?.payloads ?: return
    scope.launch {
        graph.record(payloads)
        onRecorded()
    }
}

/**
 * The scopes worth offering (SPEC FR-15): series-level changes can't be "only this one"; a new day for a
 * rule that isn't weekly can't be "this and following".
 */
internal fun offeredScopes(state: CalendarState, ref: OccurrenceRef, changes: Changes): List<Scope> {
    val weekly = DraftModel.fromOccurrence(state, ref)?.let { DraftModel.repeatOf(it) } is Repeat.Weekly
    return buildList {
        if (changes.repeat == null && changes.termTimeOnly == null && changes.reminders == null) add(Scope.ONLY)
        if (changes.day == null || weekly) add(Scope.FOLLOWING)
        add(Scope.ALL)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetBody(
    state: CalendarState,
    now: Instant,
    draft: Draft,
    onDraft: (Draft) -> Unit,
    editing: Boolean,
    displaySized: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val disc: Dp = if (displaySized) 56.dp else 40.dp
    val today = now.atZone(state.zone).toLocalDate()
    var more by remember { mutableStateOf(draft.place.isNotBlank() || draft.notes.isNotBlank()) }
    var picking by remember { mutableStateOf<Picker?>(null) }
    var suggesting by remember { mutableStateOf(!editing) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!editing) focus.requestFocus() }

    Column(Modifier.fillMaxWidth().imePadding()) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPicker(draft, onDraft)
                Field(
                    draft.title, { onDraft(DraftModel.withTitle(draft, it)); suggesting = !editing },
                    stringResource(R.string.what_hint), Modifier.weight(1f).focusRequester(focus), label = stringResource(R.string.what),
                )
                IconAction(HcIcons.Close, stringResource(R.string.close), onDismiss)
            }
            val suggestions = if (suggesting) Suggestions.forTitle(state, draft.title) else emptyList()
            if (suggestions.isNotEmpty()) {
                SectionLabel(stringResource(R.string.suggestions))
                suggestions.forEach { suggestion ->
                    SuggestionRow(state, suggestion) { onDraft(DraftModel.applySuggestion(draft, suggestion)); suggesting = false }
                }
            }

            SectionLabel(stringResource(R.string.who))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                val everyone = draft.who !is Who.Members
                PersonToggle(stringResource(R.string.everyone), everyone, disc, { onDraft(draft.copy(who = if (everyone) Who.Members(emptySet()) else Who.Everyone)) }) {
                    GroupDisc(disc * 0.7f, stringResource(R.string.group_disc))
                }
                state.activeMembers().forEach { member ->
                    val chosen = (draft.who as? Who.Members)?.members?.contains(member.id) == true
                    PersonToggle(member.name, chosen, disc, {
                        val current = (draft.who as? Who.Members)?.members.orEmpty()
                        onDraft(draft.copy(who = Who.Members(if (chosen) current - member.id else current + member.id)))
                    }) { PersonDisc(Marks.mark(member), disc * 0.7f, describe = false) }
                }
            }

            SectionLabel(stringResource(R.string.`when`))
            DayChips(draft.day, today, displaySized, onPick = { picking = Picker.DAY }) { onDraft(DraftModel.withDay(draft, it)) }
            SwitchRow(stringResource(R.string.all_day), draft.allDay, { onDraft(DraftModel.withAllDay(draft, it)) })
            if (draft.allDay) {
                ValueRow(stringResource(R.string.ends_day), LocalDateText.current.dayShort(draft.endDay)) { picking = Picker.END_DAY }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    ValueRow(stringResource(R.string.starts), Words.time(draft.start), Modifier.weight(1f)) { picking = Picker.START }
                    ValueRow(stringResource(R.string.ends), Words.time(draft.end) + if (draft.endDay > draft.day) " " + LocalDateText.current.weekdayShort(draft.endDay) else "", Modifier.weight(1f)) { picking = Picker.END }
                }
            }

            RepeatSection(state, draft, onDraft, onPickUntil = { picking = Picker.UNTIL })

            SectionLabel(stringResource(R.string.taking_label))
            TakingChoices(state, draft.taking, disc) { onDraft(draft.copy(taking = it)) }

            Rule()
            Row(
                Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(role = Role.Button) { more = !more },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.more_details), style = Hc.type.label, color = Hc.colors.inkMuted, modifier = Modifier.weight(1f))
                Icon(if (more) HcIcons.Close else HcIcons.Plus, contentDescription = null, tint = Hc.colors.inkMuted)
            }
            if (more) {
                Field(draft.place, { onDraft(draft.copy(place = it)) }, stringResource(R.string.place_hint), label = stringResource(R.string.place))
                Field(draft.notes, { onDraft(draft.copy(notes = it)) }, stringResource(R.string.notes_hint), singleLine = false, label = stringResource(R.string.notes))
                RemindersSection(draft, onDraft)
            }

            DraftModel.clashes(draft, state).forEach { clash ->
                val person = state.allMembers().firstOrNull { it.id == clash.person }?.let(Marks::mark) ?: return@forEach
                val start = (clash.other.slot as? dev.housecalendar.contracts.Slot.Timed)?.start ?: return@forEach
                dev.housecalendar.app.ui.components.ClashLine(dev.housecalendar.app.model.ClashMark(person, clash.other.title, start))
            }
            Spacer(Modifier.size(Space.s))
        }
        val canAdd = DraftModel.canAdd(draft, state)
        val label = when {
            draft.title.isBlank() -> stringResource(R.string.title_needed)
            !canAdd -> stringResource(R.string.who_needed)
            editing -> stringResource(R.string.save_outcome, draft.title.trim())
            else -> outcomeLabel(DraftModel.outcome(draft))
        }
        OutcomeButton(label, onConfirm, Modifier.navigationBarsPadding().padding(Space.gutter), enabled = canAdd, height = if (displaySized) 64.dp else 56.dp)
    }

    when (picking) {
        Picker.DAY -> DatePickerSheet(draft.day, { onDraft(DraftModel.withDay(draft, it)) }) { picking = null }
        Picker.END_DAY -> DatePickerSheet(draft.endDay, { onDraft(DraftModel.withEndDay(draft, it)) }) { picking = null }
        Picker.UNTIL -> DatePickerSheet((draft.ends as? Ends.On)?.date ?: draft.day.plusMonths(3), { onDraft(draft.copy(ends = Ends.On(it))) }) { picking = null }
        Picker.START -> TimePickerSheet(draft.start, {
            val length = java.time.Duration.between(draft.start, draft.end).let { if (it.isNegative || it.isZero) java.time.Duration.ofHours(1) else it }
            val end = it.plus(length).let { candidate -> if (candidate < it) LocalTime.of(23, 59) else candidate }
            onDraft(DraftModel.withTimes(draft, it, end))
        }) { picking = null }
        Picker.END -> TimePickerSheet(draft.end, { onDraft(DraftModel.withTimes(draft, draft.start, it)) }) { picking = null }
        null -> Unit
    }
}

private enum class Picker { DAY, END_DAY, UNTIL, START, END }

@Composable
private fun outcomeLabel(outcome: Outcome): String {
    val text = LocalDateText.current
    val title = outcome.title
    return when (outcome.repeatKind) {
        RepeatKind.NEVER -> stringResource(R.string.add_outcome, title, listOfNotNull(text.dayShort(outcome.day), outcome.start?.let { Words.time(it) }).joinToString(" "))
        RepeatKind.WEEKLY, RepeatKind.FORTNIGHTLY -> stringResource(R.string.add_outcome_weekly, title, Words.weekdays(outcome.weekdays), text.dayAndMonth(outcome.day))
        else -> stringResource(R.string.add_outcome_repeating, title, text.dayAndMonth(outcome.day))
    }
}

@Composable
private fun IconPicker(draft: Draft, onDraft: (Draft) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(Space.touch).clip(Radius.medium).clickable(role = Role.Button) { open = true }, contentAlignment = Alignment.Center) {
        if (draft.icon.isNone) Icon(HcIcons.Plus, stringResource(R.string.icon), tint = Hc.colors.inkMuted) else EventIcon(draft.icon, 26.dp, Modifier.padding(start = Space.s))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Hc.colors.surface,
            title = { Text(stringResource(R.string.icon), style = Hc.type.title) },
            text = {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    dev.housecalendar.app.ui.icons.HcIcons.catalogue.forEach { (key, _) ->
                        val chosen = key == draft.icon
                        Box(
                            Modifier.size(Space.touch).clip(Radius.small).background(if (chosen) Hc.colors.sunk else Hc.colors.surface)
                                .clickable(role = Role.Button) { onDraft(DraftModel.withIcon(draft, key)); open = false },
                            contentAlignment = Alignment.Center,
                        ) { EventIcon(key, 24.dp, Modifier.padding(start = Space.s)) }
                    }
                }
            },
            confirmButton = { TextButton({ onDraft(DraftModel.withIcon(draft, dev.housecalendar.contracts.IconKey.NONE)); open = false }) { Text(stringResource(R.string.no_icon), color = Hc.colors.ink) } },
        )
    }
}

@Composable
private fun SuggestionRow(state: CalendarState, suggestion: dev.housecalendar.schedule.Suggestion, onClick: () -> Unit) {
    val people = (suggestion.who as? Who.Members)?.members.orEmpty().mapNotNull { id -> state.allMembers().firstOrNull { it.id == id }?.let(Marks::mark) }
    val slotText = (suggestion.slot as? dev.housecalendar.contracts.Slot.Timed)?.let { Words.timeRange(it.start, it.end, false) } ?: stringResource(R.string.all_day)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.touch).clip(Radius.small).background(Hc.colors.sunk).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        EventIcon(suggestion.icon, 20.dp)
        Column(Modifier.weight(1f)) {
            Text(suggestion.title, style = Hc.type.title, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(slotText, Words.people(people, suggestion.who !is Who.Members), suggestion.place).filter { it.isNotBlank() }.joinToString(" · "), style = Hc.type.caption, color = Hc.colors.inkMuted, maxLines = 1)
        }
    }
}

@Composable
private fun PersonToggle(name: String, chosen: Boolean, disc: Dp, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val colors = Hc.colors
    Row(
        Modifier
            .heightIn(min = disc)
            .clip(Radius.full)
            .background(if (chosen) colors.sunk else colors.surface)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) colors.ink else colors.rule, Radius.full)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(start = Space.xs, end = Space.m),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        leading()
        Text(name, style = Hc.type.label, color = colors.ink)
        if (chosen) Icon(HcIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DayChips(day: LocalDate, today: LocalDate, displaySized: Boolean, onPick: () -> Unit, onDay: (LocalDate) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Chip(stringResource(R.string.today), day == today) { onDay(today) }
        Chip(stringResource(R.string.tomorrow), day == today.plusDays(1)) { onDay(today.plusDays(1)) }
        Chip(if (day != today && day != today.plusDays(1)) LocalDateText.current.dayShort(day) else stringResource(R.string.pick_date), day != today && day != today.plusDays(1), onPick)
    }
    if (displaySized) Spacer(Modifier.size(Space.xs))
}

@Composable
internal fun Chip(label: String, chosen: Boolean, onClick: () -> Unit) {
    val colors = Hc.colors
    Box(
        Modifier.heightIn(min = Space.touch).clip(Radius.small).background(if (chosen) colors.ink else colors.sunk)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = Space.m, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Hc.type.label, color = if (chosen) colors.surface else colors.ink, maxLines = 1) }
}

@Composable
private fun ValueRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radius.small).background(Hc.colors.sunk).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.m, vertical = Space.s),
    ) {
        Text(label, style = Hc.type.caption, color = Hc.colors.inkMuted)
        Text(value, style = Hc.type.timeStrong, color = Hc.colors.ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(state: CalendarState, draft: Draft, onDraft: (Draft) -> Unit, onPickUntil: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val text = LocalDateText.current
    val day = draft.day
    val options = buildList {
        add(RepeatKind.NEVER to stringResource(R.string.repeat_never))
        add(RepeatKind.DAILY to stringResource(R.string.repeat_daily))
        add(RepeatKind.WEEKLY to stringResource(R.string.repeat_weekly))
        add(RepeatKind.FORTNIGHTLY to stringResource(R.string.repeat_fortnightly))
        add(RepeatKind.MONTHLY_ON_DAY to stringResource(R.string.repeat_monthly_day, Words.ordinalDay(day.dayOfMonth)))
        if (RepeatRules.ordinalOf(day) <= MAX_ORDINAL) add(RepeatKind.MONTHLY_ON_WEEKDAY to stringResource(R.string.repeat_monthly_weekday, Words.ordinal(RepeatRules.ordinalOf(day)), Words.weekdayName(day.dayOfWeek)))
        if (RepeatRules.isLastOfMonth(day)) add(RepeatKind.MONTHLY_ON_LAST_WEEKDAY to stringResource(R.string.repeat_monthly_last, Words.weekdayName(day.dayOfWeek)))
        add(RepeatKind.YEARLY to stringResource(R.string.repeat_yearly, text.dayAndMonth(day)))
        if (draft.repeatKind == RepeatKind.CUSTOM) add(RepeatKind.CUSTOM to stringResource(R.string.repeat_custom))
    }
    val current = options.firstOrNull { it.first == draft.repeatKind }?.second ?: options.first().second
    ValueRow(stringResource(R.string.repeat), current) { open = !open }
    if (open) {
        options.forEach { (kind, label) ->
            Chip(label, kind == draft.repeatKind) { onDraft(DraftModel.withRepeat(draft, kind)); if (kind != RepeatKind.WEEKLY && kind != RepeatKind.FORTNIGHTLY) open = false }
        }
    }
    if (draft.repeatKind == RepeatKind.WEEKLY || draft.repeatKind == RepeatKind.FORTNIGHTLY) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            weekOrder(state.weekStart).forEach { weekday ->
                val chosen = weekday in draft.weekdays
                Chip(text.weekdayShort(day.with(java.time.temporal.TemporalAdjusters.nextOrSame(weekday))), chosen) {
                    val next = if (chosen) draft.weekdays - weekday else draft.weekdays + weekday
                    if (next.isNotEmpty()) onDraft(draft.copy(weekdays = next))
                }
            }
        }
    }
    if (draft.repeatKind != RepeatKind.NEVER) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ends), style = Hc.type.label, color = Hc.colors.inkMuted)
            Chip(stringResource(R.string.ends_never), draft.ends == Ends.Never) { onDraft(draft.copy(ends = Ends.Never)) }
            Chip((draft.ends as? Ends.On)?.let { text.dayAndMonth(it.date) } ?: stringResource(R.string.ends_on), draft.ends is Ends.On, onPickUntil)
            Chip(stringResource(R.string.ends_after), draft.ends is Ends.After) { onDraft(draft.copy(ends = Ends.After(DEFAULT_COUNT))) }
        }
        (draft.ends as? Ends.After)?.let { after -> CountStepper(after.count) { onDraft(draft.copy(ends = Ends.After(it))) } }
        if (state.activeHolidays().isNotEmpty()) {
            SwitchRow(stringResource(R.string.term_time_only), draft.termTimeOnly, { onDraft(draft.copy(termTimeOnly = it)) }, explanation = stringResource(R.string.term_time_only_hint))
        }
    }
}

private fun weekOrder(start: DayOfWeek): List<DayOfWeek> = (0L until DAYS).map { start.plus(it) }

/** "After 10 times", with − and + (SPEC FR-22). */
@Composable
private fun CountStepper(count: Int, onCount: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        IconAction(HcIcons.Back, "−", { onCount((count - 1).coerceAtLeast(1)) })
        Text(pluralStringResource(R.plurals.ends_after_times, count, count), style = Hc.type.title, color = Hc.colors.ink)
        IconAction(HcIcons.Forward, "+", { onCount((count + 1).coerceAtMost(MAX_COUNT)) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakingChoices(state: CalendarState, taking: Taking, disc: Dp, onTaking: (Taking) -> Unit) {
    var everyone by remember { mutableStateOf(false) }
    val adults = NeedsSomeoneModel.adults(state)
    val people = if (everyone) state.activeMembers().map(Marks::mark) else adults
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Chip(stringResource(R.string.taking_not_needed), taking == Taking.NotNeeded) { onTaking(Taking.NotNeeded) }
        PersonToggle(stringResource(R.string.needs_someone), taking == Taking.NeedsSomeone, disc, { onTaking(Taking.NeedsSomeone) }) { EmptyDisc(disc * 0.6f) }
        people.forEach { person ->
            PersonToggle(person.name, taking == Taking.Person(person.id), disc, { onTaking(Taking.Person(person.id)) }) { PersonDisc(person, disc * 0.6f, describe = false) }
        }
        if (!everyone && state.activeMembers().size > adults.size) Chip(stringResource(R.string.taking_someone_else), false) { everyone = true }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(draft: Draft, onDraft: (Draft) -> Unit) {
    SectionLabel(stringResource(R.string.reminders))
    val choices = if (draft.allDay) {
        listOf(Reminder.DaysBeforeAt(1, LocalTime.of(19, 0)), Reminder.DaysBeforeAt(0, LocalTime.of(7, 30)))
    } else {
        listOf(Reminder.MinutesBefore(10), Reminder.MinutesBefore(30), Reminder.MinutesBefore(60), Reminder.DaysBeforeAt(1, LocalTime.of(19, 0)))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        choices.forEach { reminder ->
            val chosen = reminder in draft.reminders
            Chip(Words.reminder(reminder), chosen) {
                val next = if (chosen) draft.reminders - reminder else (draft.reminders + reminder).takeLast(dev.housecalendar.contracts.Limits.MAX_REMINDERS)
                onDraft(draft.copy(reminders = next))
            }
        }
    }
}

/** The scope question (SPEC FR-15, FR-16; DESIGN.md: three full-width choices in the event's own day words). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeQuestion(state: CalendarState, ref: OccurrenceRef, title: String, offered: List<Scope>, delete: Boolean, onChoose: (Scope) -> Unit, onDismiss: () -> Unit) {
    val text = LocalDateText.current
    val repeat = DraftModel.fromOccurrence(state, ref)?.let { DraftModel.repeatOf(it) }
    val day = text.dayShort(ref.occurrence)
    val weekly = (repeat as? Repeat.Weekly)?.days?.singleOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Hc.colors.surface, shape = Radius.sheet) {
        Column(Modifier.padding(horizontal = Space.gutter).navigationBarsPadding().padding(bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(stringResource(if (delete) R.string.scope_delete_title else R.string.scope_change_title), style = Hc.type.headline, color = Hc.colors.ink)
            if (Scope.ONLY in offered) dev.housecalendar.app.ui.components.ChoiceRow(stringResource(R.string.scope_only, day), false, { onChoose(Scope.ONLY) })
            if (Scope.FOLLOWING in offered) {
                val label = if (weekly != null) stringResource(R.string.scope_following, day, Words.weekdayName(weekly)) else stringResource(R.string.scope_following_generic, day)
                dev.housecalendar.app.ui.components.ChoiceRow(label, false, { onChoose(Scope.FOLLOWING) })
            }
            if (Scope.ALL in offered) dev.housecalendar.app.ui.components.ChoiceRow(stringResource(R.string.scope_all, title), false, { onChoose(Scope.ALL) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerSheet(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                picker.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.save), color = Hc.colors.action) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel), color = Hc.colors.ink) } },
    ) { DatePicker(picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerSheet(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hc.colors.surface,
        text = { TimePicker(picker) },
        confirmButton = { TextButton({ onPicked(LocalTime.of(picker.hour, picker.minute)); onDismiss() }) { Text(stringResource(R.string.save), color = Hc.colors.action) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel), color = Hc.colors.ink) } },
    )
}

private const val MAX_ORDINAL = 4
private const val DAYS = 7L
private const val DEFAULT_COUNT = 10
private const val MAX_COUNT = 52
