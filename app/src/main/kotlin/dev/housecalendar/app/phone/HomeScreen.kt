package dev.housecalendar.app.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.R
import dev.housecalendar.app.model.AgendaDay
import dev.housecalendar.app.model.AgendaModel
import dev.housecalendar.app.model.Marks
import dev.housecalendar.app.model.MonthModel
import dev.housecalendar.app.model.NeedsSomeoneModel
import dev.housecalendar.app.model.PersonMark
import dev.housecalendar.app.sync.SyncState
import dev.housecalendar.app.ui.components.AgendaRowView
import dev.housecalendar.app.ui.components.DayHeading
import dev.housecalendar.app.ui.components.EmptyDisc
import dev.housecalendar.app.ui.components.GroupDisc
import dev.housecalendar.app.ui.components.IconAction
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.components.NowLine
import dev.housecalendar.app.ui.components.OutcomeButton
import dev.housecalendar.app.ui.components.PenRing
import dev.housecalendar.app.ui.components.PersonDisc
import dev.housecalendar.app.ui.components.Rule
import dev.housecalendar.app.ui.components.Segmented
import dev.housecalendar.app.ui.components.Words
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.data.SyncHistoryEntry
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.OccurrenceRef
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class PhoneView { AGENDA, WEEK, MONTH }

/** The phone's home: the agenda wins, and Add is the one action (DESIGN.md "Screens"). */
@Composable
fun HomeScreen(
    graph: AppGraph,
    state: CalendarState,
    now: Instant,
    onOpen: (OccurrenceRef) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onNeedsSomeone: () -> Unit,
    onSettings: () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(PhoneView.AGENDA) }
    var filter by remember { mutableStateOf<MemberId?>(null) }
    val today = now.atZone(state.zone).toLocalDate()
    var selectedDay by remember { mutableStateOf(today) }
    Column(Modifier.fillMaxSize()) {
        Header(graph, state, now, onSettings)
        Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Segmented(
                listOf(PhoneView.AGENDA to stringResource(R.string.view_agenda), PhoneView.WEEK to stringResource(R.string.view_week), PhoneView.MONTH to stringResource(R.string.view_month)),
                view, { view = it },
            )
            FilterRow(state, filter) { filter = it }
        }
        val needs = remember(state, now.epochSecond / SECONDS_PER_MINUTE) { NeedsSomeoneModel.build(state, now) }
        if (needs.isNotEmpty()) NeedsSomeoneLink(needs.size, onNeedsSomeone)
        Box(Modifier.weight(1f)) {
            when (view) {
                PhoneView.AGENDA -> AgendaList(state, now, filter, onOpen)
                PhoneView.WEEK -> WeekList(state, now, filter, onOpen)
                PhoneView.MONTH -> MonthPane(state, now, filter, selectedDay, { selectedDay = it }, onOpen)
            }
        }
        OutcomeButton(
            stringResource(R.string.add), { onAdd(if (view == PhoneView.MONTH) selectedDay else today) },
            Modifier.navigationBarsPadding().padding(horizontal = Space.gutter, vertical = Space.m), icon = HcIcons.Plus,
        )
    }
}

/** Today's date with the pen ring, the family's name, and the sync line (DESIGN.md "Sync line"). */
@Composable
private fun Header(graph: AppGraph, state: CalendarState, now: Instant, onSettings: () -> Unit) {
    val today = now.atZone(state.zone).toLocalDate()
    val history by graph.history.collectAsState()
    val syncing by graph.sync.state.collectAsState()
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.gutter, end = Space.xs, top = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Text(today.dayOfMonth.toString(), style = Hc.type.headline, color = Hc.colors.ink)
            PenRing(52.dp)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(LocalDateText.current.dayLong(today), style = Hc.type.title, color = Hc.colors.ink)
            Text(syncLine(syncing, history, state, now), style = Hc.type.caption, color = Hc.colors.inkMuted, maxLines = 1)
        }
        IconAction(HcIcons.Settings, stringResource(R.string.settings), onSettings)
    }
}

@Composable
internal fun syncLine(syncing: SyncState, history: List<SyncHistoryEntry>, state: CalendarState, now: Instant): String {
    if (syncing is SyncState.Syncing) return stringResource(R.string.sync_running, syncing.peerName)
    val last = history.firstOrNull { it.result == SyncHistoryEntry.Result.COMPLETED } ?: return stringResource(R.string.sync_never)
    val name = last.peer?.let { state.devices[it]?.name?.value }?.ifBlank { null } ?: stringResource(R.string.a_device)
    return stringResource(R.string.sync_up_to_date_with, name, Words.relative(last.endedAt.toInstant(), now))
}

/** SPEC FR-30: show one person's day; "Everyone" clears the filter. */
@Composable
private fun FilterRow(state: CalendarState, filter: MemberId?, onFilter: (MemberId?) -> Unit) {
    val people = state.activeMembers().map(Marks::mark)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s), contentPadding = PaddingValues(vertical = Space.xs)) {
        item {
            FilterChip(selected = filter == null, label = stringResource(R.string.filter_everyone), onClick = { onFilter(null) }) {
                GroupDisc(28.dp, stringResource(R.string.group_disc))
            }
        }
        items(people, key = { it.id.toString() }) { person ->
            FilterChip(selected = filter == person.id, label = person.name, onClick = { onFilter(if (filter == person.id) null else person.id) }) {
                PersonDisc(person, 28.dp, describe = false)
            }
        }
    }
}

@Composable
private fun FilterChip(selected: Boolean, label: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val colors = Hc.colors
    Row(
        Modifier
            .heightIn(min = Space.touch)
            .clip(Radius.full)
            .background(if (selected) colors.surface else colors.paper)
            .border(1.dp, if (selected) colors.ink else colors.rule, Radius.full)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(start = Space.xs, end = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        leading()
        Text(label, style = Hc.type.label, color = colors.ink)
    }
}

/** DESIGN.md: a quiet link, not a badge: the empty disc and the count. */
@Composable
private fun NeedsSomeoneLink(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        EmptyDisc(22.dp)
        Text(pluralStringResource(R.plurals.need_someone_count, count, count), style = Hc.type.body, color = Hc.colors.ink, modifier = Modifier.weight(1f))
        Icon(HcIcons.Forward, contentDescription = null, tint = Hc.colors.inkMuted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgendaList(state: CalendarState, now: Instant, filter: MemberId?, onOpen: (OccurrenceRef) -> Unit) {
    val today = now.atZone(state.zone).toLocalDate()
    var days by remember { mutableIntStateOf(PAGE_DAYS) }
    val agenda = remember(state, today, filter, days, now.epochSecond / SECONDS_PER_MINUTE) { AgendaModel.build(state, today, days, now, filter) }
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - LOAD_AHEAD } ?: false } }
    LaunchedEffect(nearEnd) { if (nearEnd && days < MAX_DAYS) days += PAGE_DAYS }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.l)) {
        agenda.forEach { day ->
            stickyHeader(key = "h${day.date}") { DayHeading(day) }
            dayRows(day, now, state, onOpen)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.dayRows(day: AgendaDay, now: Instant, state: CalendarState, onOpen: (OccurrenceRef) -> Unit) {
    if (day.rows.isEmpty()) {
        item(key = "e${day.date}") {
            Text(
                stringResource(if (day.nowLineBefore != null || day.date == now.atZone(state.zone).toLocalDate()) R.string.nothing_on_today else R.string.nothing_on),
                style = Hc.type.body, color = Hc.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.m),
            )
        }
    }
    day.rows.forEachIndexed { index, row ->
        if (day.nowLineBefore == index) item(key = "n${day.date}") { NowLine(now.atZone(state.zone).toLocalTime()) }
        item(key = "r${day.date}${row.key.event}${row.key.occurrence}") {
            AgendaRowView(row, onClick = { onOpen(row.key) })
            Rule(Modifier.padding(start = Space.gutter))
        }
    }
    if (day.nowLineBefore == day.rows.size && day.rows.isNotEmpty()) item(key = "n${day.date}") { NowLine(now.atZone(state.zone).toLocalTime()) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekList(state: CalendarState, now: Instant, filter: MemberId?, onOpen: (OccurrenceRef) -> Unit) {
    val today = now.atZone(state.zone).toLocalDate()
    var weekStart by remember { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(state.weekStart))) }
    val days = remember(state, weekStart, filter, now.epochSecond / SECONDS_PER_MINUTE) {
        val built = AgendaModel.build(state, weekStart, DAYS_PER_WEEK, now, filter).associateBy { it.date }
        (0 until DAYS_PER_WEEK).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            built[date] ?: AgendaDay(date, Marks.word(date, today), emptyList(), emptyList(), null)
        }
    }
    Column(Modifier.fillMaxSize()) {
        PagerBar(
            label = if (weekStart <= today && today < weekStart.plusDays(DAYS_PER_WEEK.toLong())) stringResource(R.string.this_week) else LocalDateText.current.dayShort(weekStart),
            previous = stringResource(R.string.previous_week), next = stringResource(R.string.next_week),
            onPrevious = { weekStart = weekStart.minusWeeks(1) }, onNext = { weekStart = weekStart.plusWeeks(1) },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.l)) {
            days.forEach { day ->
                stickyHeader(key = "h${day.date}") { DayHeading(day) }
                dayRows(day, now, state, onOpen)
            }
        }
    }
}

@Composable
private fun PagerBar(label: String, previous: String, next: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        IconAction(HcIcons.Back, previous, onPrevious)
        Text(label, style = Hc.type.title, color = Hc.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconAction(HcIcons.Forward, next, onNext)
    }
}

/** The month: a grid of days with a mark per person; choosing a day lists it below (DESIGN.md "Month"). */
@Composable
private fun MonthPane(state: CalendarState, now: Instant, filter: MemberId?, selected: LocalDate, onSelect: (LocalDate) -> Unit, onOpen: (OccurrenceRef) -> Unit) {
    val today = now.atZone(state.zone).toLocalDate()
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val grid = remember(state, month, today) { MonthModel.build(state, month, today) }
    val dayAgenda = remember(state, selected, filter, now.epochSecond / SECONDS_PER_MINUTE) { AgendaModel.build(state, selected, 1, now, filter).firstOrNull() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.l)) {
        item {
            PagerBar(
                LocalDateText.current.month(month), stringResource(R.string.previous_month), stringResource(R.string.next_month),
                onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.s)) {
                grid.weekdays.forEach { weekday ->
                    Text(
                        LocalDateText.current.weekdayShort(today.with(TemporalAdjusters.nextOrSame(weekday))).take(2),
                        style = Hc.type.caption, color = Hc.colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            grid.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.s)) {
                    week.forEach { cell ->
                        val people = if (filter == null) cell.people else cell.people.filter { it.id == filter }
                        MonthCellView(cell.date, cell.inMonth, cell.today, cell.holiday, selected == cell.date, people, cell.everyone, Modifier.weight(1f)) { onSelect(cell.date) }
                    }
                }
            }
            Rule(Modifier.padding(top = Space.s))
        }
        if (dayAgenda != null) {
            item { DayHeading(dayAgenda) }
            dayAgenda.rows.forEach { row ->
                item(key = "m${row.key.event}${row.key.occurrence}") {
                    AgendaRowView(row, onClick = { onOpen(row.key) })
                    Rule(Modifier.padding(start = Space.gutter))
                }
            }
        }
    }
}

@Composable
private fun MonthCellView(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    holiday: Boolean,
    selected: Boolean,
    people: List<PersonMark>,
    everyone: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Hc.colors
    val description = listOfNotNull(LocalDateText.current.dayShort(date), Words.people(people, everyone).ifBlank { null }).joinToString(", ")
    Column(
        modifier
            .aspectRatio(CELL_ASPECT)
            .clip(Radius.small)
            .background(if (selected) colors.surface else if (holiday) colors.band else colors.paper)
            .then(if (selected) Modifier.border(1.dp, colors.ink, Radius.small) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(), style = Hc.type.label.copy(fontFamily = Hc.type.time.fontFamily), color = if (inMonth) colors.ink else colors.inkMuted)
            if (isToday) PenRing(28.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (everyone) Box(Modifier.size(6.dp).clip(Radius.full).background(colors.ink))
            people.take(MAX_DOTS).forEach { person -> Box(Modifier.size(6.dp).clip(Radius.full).background(colors.member(person.colorIndex).accent)) }
        }
    }
}

private const val PAGE_DAYS = 42
private const val MAX_DAYS = 400
private const val LOAD_AHEAD = 6
private const val DAYS_PER_WEEK = 7
private const val SECONDS_PER_MINUTE = 60
private const val MAX_DOTS = 4
private const val CELL_ASPECT = 0.82f
