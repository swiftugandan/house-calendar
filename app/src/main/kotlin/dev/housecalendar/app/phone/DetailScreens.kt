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
import dev.housecalendar.app.ui.components.HcCard
import dev.housecalendar.app.ui.components.GroupedCard
import dev.housecalendar.app.ui.components.Pill
import dev.housecalendar.app.ui.components.IconTile
import dev.housecalendar.app.ui.components.tileColor
import dev.housecalendar.app.ui.components.entryFill
import dev.housecalendar.app.ui.components.railColors
import dev.housecalendar.app.ui.components.colourRail
import dev.housecalendar.schedule.Agenda
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.PaddingValues
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
    val fill = entryFill(detail.people, detail.everyone)
    val rail = railColors(detail.people, detail.everyone)
    Column(Modifier.fillMaxSize()) {
        TopBar("", onBack, backLabel = stringResource(R.string.back)) {
            IconAction(HcIcons.Edit, stringResource(R.string.edit), onEdit, filled = true)
            IconAction(HcIcons.Delete, stringResource(R.string.delete_event, detail.title), {
                if (detail.repeating) deleting = true else onPlan(EditPlanner.delete(state, ref, Scope.ALL), detail.title)
            }, filled = true)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).navigationBarsPadding().padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            // DESIGN.md "Event detail": time, place and taking win the screen, on the event's own tint.
            HcCard(Modifier.fillMaxWidth(), color = fill) {
                Column(Modifier.fillMaxWidth().colourRail(rail, inset = 18.dp).padding(start = 28.dp, end = Space.l, top = Space.xl, bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    IconTile(detail.icon, tileColor(detail.people, detail.everyone, fill), size = 52.dp)
                    Text(detail.title, style = Hc.type.largeTitle, color = Hc.colors.ink)
                    Column {
                        Text(text.dayLong(detail.day), style = Hc.type.title, color = Hc.colors.ink)
                        Text(
                            if (detail.start != null && detail.end != null) Words.timeRange(detail.start, detail.end, detail.lastDay > detail.day, detail.lastDay)
                            else if (detail.lastDay > detail.day) stringResource(R.string.time_range, text.dayShort(detail.day), text.dayShort(detail.lastDay)) else stringResource(R.string.all_day),
                            style = Hc.type.timeStrong, color = Hc.colors.ink,
                        )
                        Words.repeatSummary(detail.repeat, detail.termTimeOnly, detail.day)?.let { Text(it, style = Hc.type.caption, color = Hc.colors.inkMuted) }
                        if (detail.moved) Text(stringResource(R.string.moved_note), style = Hc.type.caption, color = Hc.colors.inkMuted)
                    }
                }
            }
            GroupedCard {
                row {
                    Line(HcIcons.Taking) {
                        when (val mark = detail.taking) {
                            is TakingMark.Person -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                PersonDisc(mark.person, 28.dp)
                                Text(stringResource(R.string.taking_person, mark.person.name), style = Hc.type.body, color = Hc.colors.ink)
                            }
                            TakingMark.NeedsSomeone -> Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                    EmptyDisc(28.dp)
                                    Text(stringResource(R.string.needs_someone), style = Hc.type.body, color = Hc.colors.ink)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
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
                row {
                    Line(HcIcons.People) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            if (detail.everyone) GroupDisc(28.dp, stringResource(R.string.group_disc)) else PeopleStack(detail.people, 28.dp, ring = Hc.colors.surface)
                            Text(Words.people(detail.people, detail.everyone), style = Hc.type.body, color = Hc.colors.ink)
                        }
                    }
                }
                if (detail.place.isNotBlank()) row { Line(HcIcons.Place) { Text(detail.place, style = Hc.type.body, color = Hc.colors.ink) } }
            }
            if (detail.clashes.isNotEmpty()) {
                HcCard(Modifier.fillMaxWidth(), color = Hc.colors.band, depth = null) {
                    Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) { detail.clashes.forEach { ClashLine(it) } }
                }
            }
            GroupedCard {
                row {
                    Line(HcIcons.Bell) {
                        if (detail.reminders.isEmpty()) Text(stringResource(R.string.reminders_none_set), style = Hc.type.body, color = Hc.colors.inkMuted)
                        detail.reminders.forEach { Text(Words.reminder(it), style = Hc.type.body, color = Hc.colors.ink) }
                    }
                }
                if (detail.notes.isNotBlank()) row { Line(HcIcons.Notes) { Text(detail.notes, style = Hc.type.body, color = Hc.colors.ink) } }
            }
            Column(Modifier.padding(horizontal = Space.s)) {
                Text(stringResource(R.string.added_by, Words.device(detail.addedBy)), style = Hc.type.caption, color = Hc.colors.inkMuted)
                if (detail.changedBy != detail.addedBy) Text(stringResource(R.string.changed_by, Words.device(detail.changedBy)), style = Hc.type.caption, color = Hc.colors.inkMuted)
            }
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

/** A row of a grouped card: a muted glyph, then the content. */
@Composable
private fun Line(icon: ImageVector, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Icon(icon, contentDescription = null, tint = Hc.colors.inkMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) { content() }
    }
}

/** "Alex takes": a person pill, so the choice carries the person's colour. */
@Composable
private fun TakeButton(person: PersonMark, onClick: () -> Unit) {
    Pill(stringResource(R.string.take_as, person.name), selected = false, onClick = onClick, role = Role.Button, person = Hc.colors.member(person.colorIndex)) {
        PersonDisc(person, 32.dp, describe = false)
    }
}

/** SPEC FR-35: the next fortnight's lifts that still need someone, each with one-tap "I'll take". */
@OptIn(ExperimentalLayoutApi::class)
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
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            items(rows, key = { "${it.key.event}${it.key.occurrence}" }) { row ->
                val fill = entryFill(row.people, false)
                HcCard(Modifier.fillMaxWidth(), color = fill, onClick = { onOpen(row.key) }) {
                    Column(Modifier.fillMaxWidth().colourRail(railColors(row.people, false)).padding(start = 22.dp, end = Space.l, top = Space.l, bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            IconTile(row.icon, tileColor(row.people, false, fill))
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = Hc.type.title, color = Hc.colors.ink)
                                Text(
                                    row.start?.let { stringResource(R.string.time_on_day, LocalDateText.current.dayShort(row.date), Words.time(it)) } ?: LocalDateText.current.dayShort(row.date),
                                    style = Hc.type.caption.copy(fontFeatureSettings = "tnum"), color = Hc.colors.inkMuted,
                                )
                            }
                            PeopleStack(row.people, 26.dp, ring = fill)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            ordered.forEach { adult ->
                                Pill(
                                    if (adult.id == me) stringResource(R.string.ill_take) else stringResource(R.string.take_as, adult.name),
                                    selected = false, role = Role.Button, person = Hc.colors.member(adult.colorIndex),
                                    onClick = {
                                        val repeating = Agenda.occurrence(state, row.key)?.repeating == true
                                        if (repeating) asking = row.key to adult else onPlan(EditPlanner.change(state, row.key, Scope.ALL, Changes(taking = Taking.Person(adult.id))))
                                    },
                                ) { PersonDisc(adult, 32.dp, describe = false) }
                            }
                        }
                    }
                }
            }
        }
    }
    asking?.let { (ref, person) ->
        val title = Agenda.occurrence(state, ref)?.title.orEmpty()
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
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            items(deleted, key = { it.event.toString() }) { item ->
                HcCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = Hc.type.title, color = Hc.colors.ink)
                            val by = if (item.by == graph.deviceId) stringResource(R.string.this_device) else state.devices[item.by]?.name?.value?.ifBlank { null } ?: stringResource(R.string.a_device)
                            Text(stringResource(R.string.deleted_by, by), style = Hc.type.caption, color = Hc.colors.inkMuted)
                        }
                        QuietButton(stringResource(R.string.restore), { scope.launch { graph.record(listOf(EditPlanner.restore(item.event))) } }, icon = HcIcons.Undo)
                    }
                }
            }
        }
    }
}
