package dev.housecalendar.app.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.R
import dev.housecalendar.app.model.IntakeModel
import dev.housecalendar.app.ui.components.EventIcon
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.components.OutcomeButton
import dev.housecalendar.app.ui.components.QuietButton
import dev.housecalendar.app.ui.components.Rule
import dev.housecalendar.app.ui.components.TopBar
import dev.housecalendar.app.ui.components.Words
import dev.housecalendar.app.ui.icons.IconSuggester
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.CalendarIds
import dev.housecalendar.contracts.Slot
import dev.housecalendar.intake.IncomingDraft
import dev.housecalendar.intake.IntakeResult
import dev.housecalendar.schedule.CalendarState
import kotlinx.coroutines.launch

/**
 * The import review (SPEC FR-50, FR-55; DESIGN.md "Import review"): ticked rows, what was skipped, and the
 * action, which is "Add 3 as holidays" when every item is all day.
 */
@Composable
fun ImportReviewScreen(graph: AppGraph, state: CalendarState, result: IntakeResult.Drafts, copies: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ticked = remember(result) { mutableStateListOf(*Array(result.drafts.size) { true }) }
    val chosen = result.drafts.filterIndexed { index, _ -> ticked.getOrElse(index) { false } }
    fun record(asHolidays: Boolean) {
        val now = graph.clock.now().toEpochMilli()
        val payloads = if (asHolidays) {
            IntakeModel.holidayPayloads(chosen) { CalendarIds.randomHoliday(now) }
        } else {
            IntakeModel.eventPayloads(chosen, state) { CalendarIds.randomEvent(now) }
        }
        scope.launch {
            graph.record(payloads)
            onDone()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(if (copies) R.string.import_phone_calendars_title else R.string.import_title), onDone, backLabel = stringResource(R.string.back))
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    if (copies) Text(stringResource(R.string.import_copy_note), style = Hc.type.caption, color = Hc.colors.inkMuted)
                    if (!IntakeModel.canBeHolidays(chosen)) Text(stringResource(R.string.import_everyone_note), style = Hc.type.caption, color = Hc.colors.inkMuted)
                }
            }
            itemsIndexed(result.drafts) { index, draft ->
                ReviewRow(draft, ticked.getOrElse(index) { false }, IntakeModel.alreadyOnCalendar(draft, state)) { ticked[index] = !ticked[index] }
                Rule(Modifier.padding(start = Space.gutter))
            }
            item {
                Column(Modifier.padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    if (result.skipped > 0) Text(pluralStringResource(R.plurals.import_skipped, result.skipped, result.skipped), style = Hc.type.caption, color = Hc.colors.inkMuted)
                    if (result.overridesSkipped > 0) Text(pluralStringResource(R.plurals.import_overrides, result.overridesSkipped, result.overridesSkipped), style = Hc.type.caption, color = Hc.colors.inkMuted)
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (IntakeModel.canBeHolidays(chosen)) {
                OutcomeButton(pluralStringResource(R.plurals.import_add_holidays, chosen.size, chosen.size), { record(asHolidays = true) })
                QuietButton(stringResource(R.string.import_as_events), { record(asHolidays = false) }, Modifier.fillMaxWidth())
            } else {
                OutcomeButton(pluralStringResource(R.plurals.import_add_events, chosen.size, chosen.size), { record(asHolidays = false) }, enabled = chosen.isNotEmpty())
            }
        }
    }
}

@Composable
private fun ReviewRow(draft: IncomingDraft, ticked: Boolean, already: Boolean, onToggle: () -> Unit) {
    val text = LocalDateText.current
    val day = draft.day
    val last = draft.lastDay
    val start = (draft.slot as? Slot.Timed)?.start
    val whenText = when {
        day == null -> ""
        draft.allDay && last != null && last != day -> stringResource(R.string.time_range, text.dayShort(day), text.dayShort(last))
        start != null -> stringResource(R.string.time_on_day, text.dayShort(day), Words.time(start))
        else -> text.dayShort(day)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Checkbox, onClick = onToggle).padding(horizontal = Space.gutter, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Checkbox(ticked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Hc.colors.action, checkmarkColor = Hc.colors.onAction, uncheckedColor = Hc.colors.inkMuted))
        androidx.compose.foundation.layout.Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { EventIcon(IconSuggester.suggest(draft.title), 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(draft.title, style = Hc.type.title, color = Hc.colors.ink)
            Text(whenText, style = Hc.type.caption, color = Hc.colors.inkMuted)
            if (already) Text(stringResource(R.string.import_already), style = Hc.type.caption, color = Hc.colors.ink)
            draft.unmappableRule?.let { Text(stringResource(R.string.import_unmappable_note, it), style = Hc.type.caption, color = Hc.colors.inkMuted) }
        }
    }
}
