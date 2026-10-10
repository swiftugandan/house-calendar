package dev.housecalendar.app.phone

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.R
import dev.housecalendar.app.model.EventDetailModel
import dev.housecalendar.app.model.NeedsSomeoneModel
import dev.housecalendar.app.model.PersonMark
import dev.housecalendar.app.model.TakingMark
import dev.housecalendar.app.ui.components.ClashLine
import dev.housecalendar.app.ui.components.EmptyDisc
import dev.housecalendar.app.ui.components.EventIcon
import dev.housecalendar.app.ui.components.GroupDisc
import dev.housecalendar.app.ui.components.IconAction
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.components.PeopleStack
import dev.housecalendar.app.ui.components.PersonDisc
import dev.housecalendar.app.ui.components.QuietButton
import dev.housecalendar.app.ui.components.Rule
import dev.housecalendar.app.ui.components.TopBar
import dev.housecalendar.app.ui.components.Words
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.Taking
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Changes
import dev.housecalendar.schedule.EditPlanner
import dev.housecalendar.schedule.OccurrenceRef
import dev.housecalendar.schedule.Plan
import dev.housecalendar.schedule.Scope
import kotlinx.coroutines.launch
import java.time.Instant

/** Event detail: time, place and taking as one block win the screen (DESIGN.md "Screens"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventDetailScreen(
    graph: AppGraph,
    state: CalendarState,
    ref: OccurrenceRef,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPlan: (Plan, String?) -> Unit,
) {
    val detail = remember(state, ref) { EventDetailModel.build(state, ref, graph.deviceId) }
    var deleting by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf<Taking?>(null) }
    if (detail == null) {
        TopBar("", onBack, backLabel = stringResource(R.string.back))
        return
    }
    val text = LocalDateText.current
    Column(Modifier.fillMaxSize()) {
        TopBar("", onBack, backLabel = stringResource(R.string.back)) {
            IconAction(HcIcons.Edit, stringResource(R.string.edit), onEdit)
            IconAction(HcIcons.Delete, stringResource(R.string.delete_event, detail.title), {
                if (detail.repeating) deleting = true else onPlan(EditPlanner.delete(state, ref, Scope.ALL), detail.title)
            })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventIcon(detail.icon, 32.dp)
                Text(detail.title, style = Hc.type.headline, color = Hc.colors.ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Line(HcIcons.Time) {
                    Text(text.dayLong(detail.day), style = Hc.type.title, color = Hc.colors.ink)
                    Text(
                        if (detail.start != null && detail.end != null) Words.timeRange(detail.start, detail.end, detail.lastDay > detail.day, detail.lastDay)
                        else if (detail.lastDay > detail.day) stringResource(R.string.time_range, text.dayShort(detail.day), text.dayShort(detail.lastDay)) else stringResource(R.string.all_day),
                        style = Hc.type.timeStrong, color = Hc.colors.ink,
                    )
                    Words.repeatSummary(detail.repeat, detail.termTimeOnly, detail.day)?.let { Text(it, style = Hc.type.caption, color = Hc.colors.inkMuted) }
                    if (detail.moved) Text(stringResource(R.string.moved_note), style = Hc.type.caption, color = Hc.colors.inkMuted)
                }
                if (detail.place.isNotBlank()) Line(HcIcons.Place) { Text(detail.place, style = Hc.type.body, color = Hc.colors.ink) }
                Line(HcIcons.Taking) {
                    when (val mark = detail.taking) {
                        is TakingMark.Person -> Row(verticalAlignment = Alignment.CenterVertically) {
                            PersonDisc(mark.person, 24.dp)
                            Text(" " + stringResource(R.string.taking_person, mark.person.name), style = Hc.type.body, color = Hc.colors.ink)
                        }
                        TakingMark.NeedsSomeone -> Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EmptyDisc(24.dp)
                                Text(" " + stringResource(R.string.needs_someone), style = Hc.type.body, color = Hc.colors.ink)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                NeedsSomeoneModel.adults(state).forEach { adult ->
                                    TakeButton(adult) {
                                        val chosen = Taking.Person(adult.id)
                                        if (detail.repeating) taking = chosen else onPlan(EditPlanner.change(state, ref, Scope.ALL, Changes(taking = chosen)), null)
                                    }
                                }
                            }
                        }
                        TakingMark.None -> Text(stringResource(R.string.taking_not_needed), style = Hc.type.body, color = Hc.colors.inkMuted)
                    }
                }
            }
            Rule()
            Line(HcIcons.People) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (detail.everyone) GroupDisc(24.dp, stringResource(R.string.group_disc)) else PeopleStack(detail.people, 24.dp)
                    Text(Words.people(detail.people, detail.everyone), style = Hc.type.body, color = Hc.colors.ink)
                }
            }
            detail.clashes.forEach { ClashLine(it) }
            Line(HcIcons.Bell) {
                if (detail.reminders.isEmpty()) Text(stringResource(R.string.reminders_none_set), style = Hc.type.body, color = Hc.colors.inkMuted)
                detail.reminders.forEach { Text(Words.reminder(it), style = Hc.type.body, color = Hc.colors.ink) }
            }
            if (detail.notes.isNotBlank()) Line(HcIcons.Notes) { Text(detail.notes, style = Hc.type.body, color = Hc.colors.ink) }
            Rule()
            Text(stringResource(R.string.added_by, Words.device(detail.addedBy)), style = Hc.type.caption, color = Hc.colors.inkMuted)
            if (detail.changedBy != detail.addedBy) Text(stringResource(R.string.changed_by, Words.device(detail.changedBy)), style = Hc.type.caption, color = Hc.colors.inkMuted)
        }
    }
    if (deleting) {
        ScopeQuestion(state, ref, detail.title, listOf(Scope.ONLY, Scope.FOLLOWING, Scope.ALL), delete = true, onChoose = {
            deleting = false
            onPlan(EditPlanner.delete(state, ref, it), detail.title)
        }, onDismiss = { deleting = false })
    }
    taking?.let { chosen ->
        ScopeQuestion(state, ref, detail.title, listOf(Scope.ONLY, Scope.FOLLOWING, Scope.ALL), delete = false, onChoose = {
            taking = null
            onPlan(EditPlanner.change(state, ref, it, Changes(taking = chosen)), null)
        }, onDismiss = { taking = null })
    }
}

@Composable
private fun Line(icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Icon(icon, contentDescription = null, tint = Hc.colors.inkMuted, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun TakeButton(person: PersonMark, onClick: () -> Unit) {
    QuietButton(stringResource(R.string.take_as, person.name), onClick)
}

/** SPEC FR-35: the next fortnight's lifts that still need someone, each with one-tap "I'll take". */
@Composable
fun NeedsSomeoneScreen(graph: AppGraph, state: CalendarState, now: Instant, onBack: () -> Unit, onOpen: (OccurrenceRef) -> Unit, onPlan: (Plan) -> Unit) {
    val rows = remember(state, now) { NeedsSomeoneModel.build(state, now) }
    val adults = remember(state) { NeedsSomeoneModel.adults(state) }
    val me = graph.ownerMember()
    val ordered = adults.sortedBy { if (it.id == me) 0 else 1 }
    var asking by remember { mutableStateOf<Pair<OccurrenceRef, PersonMark>?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.needs_someone_title), onBack, backLabel = stringResource(R.string.back))
        if (rows.isEmpty()) Text(stringResource(R.string.needs_someone_empty), style = Hc.type.body, color = Hc.colors.inkMuted, modifier = Modifier.padding(Space.gutter))
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { "${it.key.event}${it.key.occurrence}" }) { row ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(row.key) }.padding(horizontal = Space.gutter, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text(listOfNotNull(LocalDateText.current.dayShort(row.date), row.start?.let { Words.time(it) }).joinToString(" "), style = Hc.type.time, color = Hc.colors.ink)
                        EventIcon(row.icon, 20.dp)
                        Text(row.title, style = Hc.type.title, color = Hc.colors.ink, modifier = Modifier.weight(1f))
                        PeopleStack(row.people, 22.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                        EmptyDisc(22.dp)
                        ordered.forEach { adult ->
                            Row(
                                Modifier.heightIn(min = Space.touch).clickable(role = Role.Button) {
                                    val repeating = dev.housecalendar.schedule.Agenda.occurrence(state, row.key)?.repeating == true
                                    if (repeating) asking = row.key to adult else onPlan(EditPlanner.change(state, row.key, Scope.ALL, Changes(taking = Taking.Person(adult.id))))
                                }.padding(horizontal = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PersonDisc(adult, 32.dp, describe = false)
                                Text(" " + if (adult.id == me) stringResource(R.string.ill_take) else stringResource(R.string.take_as, adult.name), style = Hc.type.label, color = Hc.colors.ink)
                            }
                        }
                    }
                }
                Rule(Modifier.padding(start = Space.gutter))
            }
        }
    }
    asking?.let { (ref, person) ->
        val title = dev.housecalendar.schedule.Agenda.occurrence(state, ref)?.title.orEmpty()
        ScopeQuestion(state, ref, title, listOf(Scope.ONLY, Scope.FOLLOWING, Scope.ALL), delete = false, onChoose = {
            asking = null
            onPlan(EditPlanner.change(state, ref, it, Changes(taking = Taking.Person(person.id))))
        }, onDismiss = { asking = null })
    }
}

/** SPEC FR-17: deleted events, newest first, each with Restore. */
@Composable
fun RecentlyDeletedScreen(graph: AppGraph, state: CalendarState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val deleted = remember(state) { state.recentlyDeleted() }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.recently_deleted), onBack, backLabel = stringResource(R.string.back))
        if (deleted.isEmpty()) Text(stringResource(R.string.recently_deleted_empty), style = Hc.type.body, color = Hc.colors.inkMuted, modifier = Modifier.padding(Space.gutter))
        LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
            items(deleted, key = { it.event.toString() }) { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = Hc.type.title, color = Hc.colors.ink)
                        val by = if (item.by == graph.deviceId) stringResource(R.string.this_device) else state.devices[item.by]?.name?.value?.ifBlank { null } ?: stringResource(R.string.a_device)
                        Text(stringResource(R.string.deleted_by, by), style = Hc.type.caption, color = Hc.colors.inkMuted)
                    }
                    QuietButton(stringResource(R.string.restore), { scope.launch { graph.record(listOf(EditPlanner.restore(item.event))) } }, icon = HcIcons.Undo)
                }
                Rule(Modifier.padding(start = Space.gutter))
            }
        }
    }
}
