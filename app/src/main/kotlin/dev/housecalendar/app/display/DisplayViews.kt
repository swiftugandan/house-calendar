package dev.housecalendar.app.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.R
import dev.housecalendar.app.model.BoardEntry
import dev.housecalendar.app.model.CellEntry
import dev.housecalendar.app.model.CellRole
import dev.housecalendar.app.model.DayWord
import dev.housecalendar.app.model.MonthModel
import dev.housecalendar.app.model.Organiser
import dev.housecalendar.app.model.OrganiserDay
import dev.housecalendar.app.model.OrganiserLayout
import dev.housecalendar.app.model.OrganiserModel
import dev.housecalendar.app.model.TakingMark
import dev.housecalendar.app.model.TodayBoardModel
import dev.housecalendar.app.ui.components.EmptyDisc
import dev.housecalendar.app.ui.components.EventIcon
import dev.housecalendar.app.ui.components.GroupDisc
import dev.housecalendar.app.ui.components.LocalDateText
import dev.housecalendar.app.ui.components.PenRing
import dev.housecalendar.app.ui.components.PersonDisc
import dev.housecalendar.app.ui.components.Rule
import dev.housecalendar.app.ui.components.Words
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.OccurrenceRef
import java.time.Instant
import java.time.YearMonth
import kotlin.math.floor

/** The family organiser (SPEC FR-31, FR-32; DESIGN.md "Organiser grid"): people across, days down. */
@Composable
fun OrganiserView(state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit) {
    val organiser = remember(state, now.epochSecond / SECONDS_PER_MINUTE) { OrganiserModel.build(state, now) }
    val type = LocalDisplayType.current
    val dayColumn = em(type.detailSize) * DAY_COLUMN_EMS
    Column(Modifier.fillMaxSize()) {
        if (organiser.layout == OrganiserLayout.COLUMNS) PeopleHeader(organiser, dayColumn)
        Rule()
        organiser.days.forEach { day ->
            val today = day.word == DayWord.TODAY
            Row(
                Modifier.fillMaxWidth().weight(if (today) TODAY_WEIGHT else 1f).background(if (today) Hc.colors.surface else Hc.colors.paper),
            ) {
                DayLabel(day, dayColumn)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (organiser.layout == OrganiserLayout.COLUMNS) {
                        if (day.everyone.isNotEmpty()) EveryoneBand(day.everyone, onOpen)
                        Row(Modifier.weight(1f)) {
                            day.cells.forEach { cell -> Cell(cell, Modifier.weight(1f).fillMaxHeight(), onOpen) }
                        }
                    } else {
                        Cell(day.list, Modifier.fillMaxSize(), onOpen)
                    }
                }
            }
            Rule()
        }
    }
}

@Composable
private fun PeopleHeader(organiser: Organiser, dayColumn: Dp) {
    val type = LocalDisplayType.current
    val disc = em(type.detailSize) * DISC_EMS
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(dayColumn))
        organiser.columns.forEach { person ->
            Row(Modifier.weight(1f).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PersonDisc(person, disc)
                Text(person.name, style = type.detailStrong, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The day's label: weekday and number, today's number in the pen ring, a holiday hatched (DESIGN.md). */
@Composable
private fun DayLabel(day: OrganiserDay, width: Dp) {
    val type = LocalDisplayType.current
    val rule = Hc.colors.rule
    val holiday = day.holidays.firstOrNull()
    Box(Modifier.width(width).fillMaxHeight()) {
        if (holiday != null) {
            Canvas(Modifier.matchParentSize().clipToBounds()) {
                val gap = HATCH_GAP.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(rule, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                    x += gap
                }
            }
        }
        Column(Modifier.padding(Space.s)) {
            if (day.word == DayWord.TODAY) {
                Text(LocalDateText.current.weekdayShort(day.date), style = type.detail, color = Hc.colors.inkMuted)
                Box(contentAlignment = Alignment.Center) {
                    Text(day.date.dayOfMonth.toString(), style = type.glanceDisplay.copy(fontSize = type.glanceDisplay.fontSize * DAY_NUMBER_SCALE, lineHeight = type.glanceDisplay.lineHeight * DAY_NUMBER_SCALE), color = Hc.colors.ink)
                    PenRing(em(type.glanceDisplay.fontSize) * PEN_RING_EMS)
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(LocalDateText.current.weekdayShort(day.date), style = type.detail, color = Hc.colors.inkMuted)
                    Text(day.date.dayOfMonth.toString(), style = type.detailStrong, color = Hc.colors.ink)
                }
            }
            if (holiday != null) Text(holiday, style = type.detailCaption, color = Hc.colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Everyone's occurrences, drawn once across every column on the `band` tone. */
@Composable
private fun EveryoneBand(entries: List<CellEntry>, onOpen: (OccurrenceRef) -> Unit) {
    val type = LocalDisplayType.current
    Row(
        Modifier.fillMaxWidth().background(Hc.colors.band).padding(horizontal = Space.s, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        GroupDisc(em(type.detailSize) * SMALL_DISC_EMS, stringResource(R.string.group_disc))
        entries.forEach { entry ->
            Row(Modifier.clickable(role = Role.Button) { onOpen(entry.key) }, verticalAlignment = Alignment.CenterVertically) {
                entry.start?.let { Text(Words.time(it) + " ", style = type.detailTime, color = Hc.colors.inkMuted) }
                Text(entry.title, style = type.detailStrong, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One person's occurrences on one day. It shows as many entry lines as fit at the detail tier and ends with
 * "+N more" when the rest don't (DESIGN.md "+N more").
 */
@Composable
private fun Cell(entries: List<CellEntry>, modifier: Modifier, onOpen: (OccurrenceRef) -> Unit) {
    val type = LocalDisplayType.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.padding(horizontal = Space.xs, vertical = 2.dp).clipToBounds()) {
        val lineHeight = with(density) { type.detail.lineHeight.toDp() }
        val lines = floor(maxHeight / lineHeight).toInt().coerceAtLeast(1)
        // Two lines (time above title) only when every entry fits that way; an entry shown with a shortened
        // title beats "+1 more" (DESIGN.md "Entry lines in narrow columns").
        val oneLine = maxWidth > em(type.detailSize) * ONE_LINE_EMS || entries.size * 2 > lines
        val fit = if (entries.size <= lines) entries.size else (lines - 1).coerceAtLeast(0)
        Column {
            entries.take(fit).forEach { entry -> EntryLine(entry, oneLine, onOpen) }
            if (fit < entries.size) Text(stringResource(R.string.more_count, entries.size - fit), style = type.detailCaption, color = Hc.colors.inkMuted)
        }
    }
}

@Composable
private fun EntryLine(entry: CellEntry, oneLine: Boolean, onOpen: (OccurrenceRef) -> Unit) {
    val type = LocalDisplayType.current
    val small = em(type.detailSize) * SMALL_DISC_EMS
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (entry.role == CellRole.TAKING) Icon(HcIcons.Taking, contentDescription = null, tint = Hc.colors.inkMuted, modifier = Modifier.size(small).padding(end = 2.dp))
        EventIcon(entry.icon, small)
        Text(
            entry.title, style = if (entry.now) type.detailStrong else type.detail, color = Hc.colors.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (entry.taking == TakingMark.NeedsSomeone) EmptyDisc(small, Modifier.padding(start = 2.dp))
        entry.others.forEach { PersonDisc(it, small, Modifier.padding(start = 2.dp)) }
    }
    val time: @Composable () -> Unit = {
        Text(entry.start?.let { Words.time(it) } ?: "", style = type.detailTime, color = if (entry.now) Hc.colors.today else Hc.colors.inkMuted)
    }
    Column(Modifier.fillMaxWidth().alpha(if (entry.ended) ENDED_ALPHA else 1f).clickable(role = Role.Button) { onOpen(entry.key) }) {
        if (oneLine) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (entry.start != null) time()
                content(this)
            }
        } else {
            if (entry.start != null) time()
            Row(verticalAlignment = Alignment.CenterVertically) { content(this) }
        }
    }
}

/** The Today board (DESIGN.md "Today board"): each person's Now in glance type, then Next. */
@Composable
fun TodayBoardView(state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit) {
    val rows = remember(state, now.epochSecond / SECONDS_PER_MINUTE) { TodayBoardModel.build(state, now) }
    val type = LocalDisplayType.current
    val disc = em(type.glanceSize) * BOARD_DISC_EMS
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.person?.id?.toString() ?: "everyone" }) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                if (row.person != null) PersonDisc(row.person, disc) else GroupDisc(disc, stringResource(R.string.group_disc))
                Column(Modifier.weight(NOW_WEIGHT)) {
                    Text(row.person?.name ?: stringResource(R.string.everyone), style = type.detailStrong, color = Hc.colors.inkMuted)
                    val current = row.now
                    if (current != null) {
                        Text(current.title, style = type.glanceText, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpen(current.key) })
                        current.end?.let { Text(stringResource(R.string.until_time, Words.time(it)), style = type.detail, color = Hc.colors.inkMuted) }
                    } else {
                        Text("—", style = type.glanceText, color = Hc.colors.rule)
                    }
                }
                Column(Modifier.weight(1f)) {
                    row.next?.let { next ->
                        Text(stringResource(R.string.next), style = type.detailCaption, color = Hc.colors.inkMuted)
                        NextLine(next, row.person, state, now, onOpen)
                    }
                }
            }
            Rule()
        }
    }
}

@Composable
private fun NextLine(next: BoardEntry, person: dev.housecalendar.app.model.PersonMark?, state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit) {
    val type = LocalDisplayType.current
    val today = now.atZone(state.zone).toLocalDate()
    Column(Modifier.clickable(role = Role.Button) { onOpen(next.key) }) {
        val whenText = listOfNotNull(
            if (next.date != today) Words.day(next.date, if (next.date == today.plusDays(1)) DayWord.TOMORROW else DayWord.NONE) else null,
            next.start?.let { Words.time(it) },
        ).joinToString(" ")
        Text(whenText, style = type.detailTime, color = Hc.colors.ink)
        Text(next.title, style = type.detailStrong, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val taker = (next.taking as? TakingMark.Person)?.person
        val line = if (person != null && taker?.id == person.id && next.people.none { it.id == person.id }) {
            stringResource(R.string.taking_them, Words.join(next.people.map { it.name }))
        } else {
            Words.taking(next.taking)
        }
        line?.let { Text(it, style = type.detailCaption, color = Hc.colors.inkMuted) }
    }
}

/** The month on the wall: day numbers, today ringed, up to two titles a day and "+N". */
@Composable
fun DisplayMonthView(state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit) {
    val today = now.atZone(state.zone).toLocalDate()
    val grid = remember(state, today) { MonthModel.build(state, YearMonth.from(today), today) }
    val type = LocalDisplayType.current
    val text = LocalDateText.current
    val entries = remember(state, grid) {
        dev.housecalendar.schedule.Agenda.resolve(state, dev.housecalendar.schedule.DateWindow(grid.weeks.first().first().date, grid.weeks.last().last().date))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            grid.weekdays.forEach { weekday ->
                Text(text.weekdayShort(today.with(java.time.temporal.TemporalAdjusters.nextOrSame(weekday))), style = type.detailCaption, color = Hc.colors.inkMuted, modifier = Modifier.weight(1f).padding(Space.xs))
            }
        }
        grid.weeks.forEach { week ->
            Rule()
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { cell ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().background(if (cell.today) Hc.colors.surface else if (cell.holiday) Hc.colors.band else Hc.colors.paper).padding(Space.xs).clipToBounds(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(cell.date.dayOfMonth.toString(), style = type.detailStrong, color = if (cell.inMonth) Hc.colors.ink else Hc.colors.inkMuted)
                            if (cell.today) PenRing(em(type.detailSize) * MONTH_RING_EMS)
                        }
                        entries.filter { cell.date in it.day..it.lastDay }.take(MONTH_TITLES).forEach { entry ->
                            Row(Modifier.clickable(role = Role.Button) { onOpen(entry.key) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                val colour = entry.people.firstOrNull()?.let { id -> state.allMembers().firstOrNull { it.id == id }?.colorIndex }
                                Box(Modifier.size(width = 4.dp, height = em(type.detailSize)).background(if (entry.everyone || colour == null) Hc.colors.ink else Hc.colors.member(colour).accent))
                                Text(entry.title, style = type.detailCaption, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (cell.titles.size + cell.more > MONTH_TITLES) Text(stringResource(R.string.more_count, cell.titles.size + cell.more - MONTH_TITLES), style = type.detailCaption, color = Hc.colors.inkMuted)
                    }
                }
            }
        }
    }
}

/** One em of [size] as a [Dp], so layout scales with the physical type. */
@Composable
private fun em(size: TextUnit): Dp = with(LocalDensity.current) { size.toDp() }

private const val SECONDS_PER_MINUTE = 60L
private const val TODAY_WEIGHT = 1.6f
private const val DAY_COLUMN_EMS = 4.6f
private const val DISC_EMS = 1.6f
private const val SMALL_DISC_EMS = 0.9f
private const val BOARD_DISC_EMS = 1.0f
private const val ONE_LINE_EMS = 13f
private const val HATCH_GAP = 6
private const val DAY_NUMBER_SCALE = 0.55f
private const val PEN_RING_EMS = 0.85f
private const val MONTH_RING_EMS = 1.9f
private const val MONTH_TITLES = 2
private const val NOW_WEIGHT = 1.4f
private const val ENDED_ALPHA = 0.55f
