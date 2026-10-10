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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.R
import dev.housecalendar.app.model.BoardEntry
import dev.housecalendar.app.model.BoardRow
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
import dev.housecalendar.app.ui.components.colourRail
import dev.housecalendar.app.ui.components.railColors
import dev.housecalendar.app.ui.components.entryFill
import dev.housecalendar.app.ui.theme.Depth
import dev.housecalendar.app.ui.theme.lifted
import dev.housecalendar.app.model.PersonMark
import androidx.compose.ui.draw.clip
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.components.HcCard
import dev.housecalendar.app.model.Marks
import dev.housecalendar.schedule.Agenda
import dev.housecalendar.schedule.DateWindow
import java.time.temporal.TemporalAdjusters
import androidx.compose.foundation.layout.PaddingValues
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.OccurrenceRef
import java.time.Instant
import java.time.YearMonth

/** The family organiser (SPEC FR-31, FR-32; DESIGN.md "Organiser grid"): people across, days down. */
@Composable
fun OrganiserView(state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit) {
    val organiser = remember(state, now.epochSecond / SECONDS_PER_MINUTE) { OrganiserModel.build(state, now) }
    val type = LocalDisplayType.current
    val dayColumn = em(type.detailSize) * DAY_COLUMN_EMS
    Column(Modifier.fillMaxSize()) {
        if (organiser.layout == OrganiserLayout.COLUMNS) PeopleHeader(organiser, dayColumn)
        organiser.days.forEach { day ->
            val today = day.word == DayWord.TODAY
            val weight = rowWeight(day, today, organiser.layout)
            // DESIGN.md "Organiser grid": the day you're in lifts off the wall as a card.
            val row = if (today) {
                Modifier.fillMaxWidth().weight(weight).padding(vertical = TODAY_LIFT).lifted(Depth.RAISED, Radius.large).clip(Radius.large).background(Hc.colors.surface)
            } else {
                Modifier.fillMaxWidth().weight(weight)
            }
            Row(row) {
                DayLabel(day, dayColumn)
                Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = Space.xs)) {
                    if (organiser.layout == OrganiserLayout.COLUMNS) {
                        if (day.everyone.isNotEmpty()) EveryoneBand(day.everyone, onOpen)
                        Row(Modifier.weight(1f)) {
                            day.cells.forEachIndexed { index, cell -> Cell(cell, organiser.columns.getOrNull(index), Modifier.weight(1f).fillMaxHeight(), onOpen) }
                        }
                    } else {
                        Cell(day.list, null, Modifier.fillMaxSize(), onOpen)
                    }
                }
            }
            if (!today && day != organiser.days.lastOrNull()) Rule(Modifier.padding(start = dayColumn))
        }
    }
}

/**
 * DESIGN.md "Organiser grid": rows share the height by what they hold. An empty day is a slim row, each entry
 * past the first in the busiest cell adds most of a row, and an everyone band adds its own line, so busy days
 * get room and the week still fits without scrolling. Today is never slimmer than [TODAY_WEIGHT].
 */
private fun rowWeight(day: OrganiserDay, today: Boolean, layout: OrganiserLayout): Float {
    val busiest = if (layout == OrganiserLayout.COLUMNS) day.cells.maxOfOrNull { it.size } ?: 0 else day.list.size
    val band = layout == OrganiserLayout.COLUMNS && day.everyone.isNotEmpty()
    val entries = when (busiest) {
        0 -> if (band) 0f else EMPTY_WEIGHT
        else -> 1f + (busiest - 1).coerceAtMost(EXTRA_ENTRIES_COUNTED) * EXTRA_ENTRY_WEIGHT
    }
    val content = entries + if (band) BAND_WEIGHT else 0f
    return if (today) maxOf(content, TODAY_WEIGHT) else maxOf(content, EMPTY_WEIGHT)
}

@Composable
private fun PeopleHeader(organiser: Organiser, dayColumn: Dp) {
    val type = LocalDisplayType.current
    val disc = em(type.detailSize) * DISC_EMS
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(dayColumn))
        organiser.columns.forEach { person ->
            Box(Modifier.weight(1f).padding(horizontal = Space.xs)) {
                Row(
                    Modifier.lifted(Depth.RAISED, Radius.full).clip(Radius.full).background(Hc.colors.surface).padding(start = 4.dp, end = Space.l, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    PersonDisc(person, disc)
                    Text(person.name, style = type.detailStrong, color = Hc.colors.member(person.colorIndex).accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
    BoxWithConstraints(Modifier.width(width).fillMaxHeight()) {
        val labelHeight = maxHeight
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
                // The ringed number takes the room under the weekday, up to its designed size, so a short
                // today row (a week with several everyone bands) never draws the ring over the weekday.
                val designed = em(type.glanceDisplay.fontSize) * PEN_RING_EMS
                val room = labelHeight - Space.s * 2 - with(LocalDensity.current) { type.detail.lineHeight.toDp() }
                val scale = (room / designed).coerceIn(MIN_RING_SCALE, 1f)
                val number = type.glanceDisplay.copy(
                    fontSize = type.glanceDisplay.fontSize * DAY_NUMBER_SCALE * scale,
                    lineHeight = type.glanceDisplay.lineHeight * DAY_NUMBER_SCALE * scale,
                )
                Text(LocalDateText.current.weekdayShort(day.date), style = type.detail, color = Hc.colors.inkMuted)
                Box(contentAlignment = Alignment.Center) {
                    Text(day.date.dayOfMonth.toString(), style = number, color = Hc.colors.ink)
                    PenRing(designed * scale)
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
        Modifier.fillMaxWidth().padding(horizontal = Space.xs).padding(bottom = 3.dp).clip(Radius.medium).background(Hc.colors.band)
            .colourRail(railColors(emptyList(), everyone = true), width = 3.dp, start = 6.dp, inset = 6.dp).padding(start = Space.l, end = Space.s, top = Space.xs, bottom = Space.xs),
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
 * One person's occurrences on one day. It shows as many entries as fit at the detail tier and ends with
 * "+N more" when the rest don't (DESIGN.md "+N more"). Entries are measured as drawn, icons and discs
 * included, so nothing is ever cut through.
 */
@Composable
private fun Cell(entries: List<CellEntry>, owner: PersonMark?, modifier: Modifier, onOpen: (OccurrenceRef) -> Unit) {
    val type = LocalDisplayType.current
    val oneLineWidth = em(type.detailSize) * ONE_LINE_EMS
    val moreText = @Composable { hidden: Int -> Text(stringResource(R.string.more_count, hidden), style = type.detailCaption, color = Hc.colors.inkMuted) }
    SubcomposeLayout(modifier.padding(horizontal = Space.xs, vertical = 2.dp).clipToBounds()) { constraints ->
        // Unbounded height, so an entry reports its true height rather than one capped at the cell's.
        val loose = Constraints(maxWidth = constraints.maxWidth)
        fun measured(oneLine: Boolean): List<List<Placeable>> = entries.mapIndexed { index, entry ->
            subcompose(EntrySlot(index, oneLine)) { EntryLine(entry, owner, oneLine, onOpen) }.map { it.measure(loose) }
        }
        fun List<Placeable>.height() = sumOf { it.height }
        // Two lines (time above title) only in a narrow cell, and only when every entry fits that way; an entry
        // with a shortened title beats "+1 more" (DESIGN.md "Entry lines in narrow columns").
        val twoLine = if (constraints.maxWidth > oneLineWidth.roundToPx()) null else measured(oneLine = false).takeIf { rows -> rows.sumOf { it.height() } <= constraints.maxHeight }
        val rows = twoLine ?: measured(oneLine = true)
        val heights = rows.map { it.height() }
        val fit = if (heights.sum() <= constraints.maxHeight) {
            entries.size
        } else {
            val moreHeight = subcompose(MoreSlot.PROBE) { moreText(entries.size) }.sumOf { it.measure(loose).height }
            val room = constraints.maxHeight - moreHeight
            heights.runningFold(0) { total, height -> total + height }.drop(1).count { it <= room }
        }
        val more = if (fit < entries.size) subcompose(MoreSlot.SHOWN) { moreText(entries.size - fit) }.map { it.measure(loose) } else emptyList()
        layout(constraints.maxWidth, constraints.maxHeight) {
            var y = 0
            (rows.take(fit).flatten() + more).forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}

private data class EntrySlot(val index: Int, val oneLine: Boolean)

private enum class MoreSlot { PROBE, SHOWN }

@Composable
private fun EntryLine(entry: CellEntry, owner: PersonMark?, oneLine: Boolean, onOpen: (OccurrenceRef) -> Unit) {
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
    // DESIGN.md "Organiser grid": a compact entry card on the column owner's tint, with their rail.
    val fill = entryFill(listOfNotNull(owner), everyone = false)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 2.dp).alpha(if (entry.ended) ENDED_ALPHA else 1f).clip(Radius.small).background(fill)
            .colourRail(railColors(listOfNotNull(owner), everyone = false), width = 3.dp, start = 4.dp, inset = 5.dp)
            .clickable(role = Role.Button) { onOpen(entry.key) }.padding(start = 12.dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
    ) {
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

/**
 * The Today board (DESIGN.md "Today board"): a card for each person, on their tint, with their Now in glance
 * type and their Next. It's read at a glance, so everyone shares the screen at once: one card across for one
 * person, two for up to four, three for five or six. A larger family scrolls a list.
 */
@Composable
fun TodayBoardView(state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit) {
    val rows = remember(state, now.epochSecond / SECONDS_PER_MINUTE) { TodayBoardModel.build(state, now) }
    val rowKey = { row: BoardRow -> row.person?.id?.toString() ?: "everyone" }
    if (rows.size > BOARD_FITTED_ROWS) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.m), contentPadding = PaddingValues(vertical = Space.s)) {
            items(rows, key = rowKey) { row -> BoardCard(row, state, now, onOpen, Modifier.fillMaxWidth()) }
        }
        return
    }
    val across = when (rows.size) {
        1 -> 1
        in 2..BOARD_TWO_ACROSS -> 2
        else -> BOARD_THREE_ACROSS
    }
    Column(Modifier.fillMaxSize().padding(vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        rows.chunked(across).forEach { line ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                line.forEach { row -> key(rowKey(row)) { BoardCard(row, state, now, onOpen, Modifier.weight(1f).fillMaxHeight()) } }
                repeat(across - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BoardCard(row: BoardRow, state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit, modifier: Modifier) {
    val type = LocalDisplayType.current
    val person = row.person
    val fill = entryFill(listOfNotNull(person), everyone = person == null)
    val accent = person?.let { Hc.colors.member(it.colorIndex).accent } ?: Hc.colors.ink
    val disc = em(type.glanceSize) * BOARD_DISC_EMS
    HcCard(modifier, color = fill) {
        Column(
            Modifier.fillMaxSize().colourRail(railColors(listOfNotNull(person), everyone = person == null), width = 5.dp, start = 12.dp, inset = 18.dp)
                .padding(start = 32.dp, end = Space.xl, top = Space.l, bottom = Space.l).clipToBounds(),
        ) {
            val current = row.now
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                if (person != null) PersonDisc(person, disc, ring = fill) else GroupDisc(disc, stringResource(R.string.group_disc))
                Text(person?.name ?: stringResource(R.string.everyone), style = type.detailStrong, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                current?.end?.let { Text(stringResource(R.string.until_time, Words.time(it)), style = type.detailTime, color = Hc.colors.inkMuted, maxLines = 1) }
            }
            if (current != null) {
                Text(current.title, style = type.glanceText, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpen(current.key) })
            } else {
                // Nothing now: a quiet line at the detail tier, leaving the room to Next.
                Text(stringResource(R.string.nothing_now_spoken), style = type.detail, color = Hc.colors.inkMuted, modifier = Modifier.padding(top = Space.s))
            }
            Spacer(Modifier.weight(1f))
            row.next?.let { next -> NextLine(next, person, state, now, onOpen) }
        }
    }
}

@Composable
private fun NextLine(next: BoardEntry, person: PersonMark?, state: CalendarState, now: Instant, onOpen: (OccurrenceRef) -> Unit) {
    val type = LocalDisplayType.current
    val today = now.atZone(state.zone).toLocalDate()
    Column(Modifier.clickable(role = Role.Button) { onOpen(next.key) }) {
        val day = if (next.date != today) Words.day(next.date, if (next.date == today.plusDays(1)) DayWord.TOMORROW else DayWord.NONE) else null
        val time = next.start?.let { Words.time(it) }
        val whenText = when {
            day != null && time != null -> stringResource(R.string.time_on_day, day, time)
            else -> day ?: time ?: stringResource(R.string.all_day)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(stringResource(R.string.next), style = type.detailCaption, color = Hc.colors.inkMuted)
            Text(whenText, style = type.detailTime, color = Hc.colors.ink)
        }
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
        Agenda.resolve(state, DateWindow(grid.weeks.first().first().date, grid.weeks.last().last().date))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            grid.weekdays.forEach { weekday ->
                Text(text.weekdayShort(today.with(TemporalAdjusters.nextOrSame(weekday))), style = type.detailCaption, color = Hc.colors.inkMuted, modifier = Modifier.weight(1f).padding(Space.xs))
            }
        }
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = MONTH_GAP / 2), horizontalArrangement = Arrangement.spacedBy(MONTH_GAP)) {
                week.forEach { cell ->
                    val tile = when {
                        cell.today -> Modifier.lifted(Depth.RAISED, Radius.medium).clip(Radius.medium).background(Hc.colors.surface)
                        cell.holiday -> Modifier.clip(Radius.medium).background(Hc.colors.band)
                        else -> Modifier.clip(Radius.medium).background(Hc.colors.surface.copy(alpha = if (cell.inMonth) MONTH_TILE else MONTH_TILE_OUT))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().then(tile).padding(Space.xs).clipToBounds(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(cell.date.dayOfMonth.toString(), style = type.detailStrong, color = if (cell.inMonth) Hc.colors.ink else Hc.colors.inkMuted)
                            if (cell.today) PenRing(em(type.detailSize) * MONTH_RING_EMS)
                        }
                        entries.filter { cell.date in it.day..it.lastDay }.take(MONTH_TITLES).forEach { entry ->
                            val people = entry.people.mapNotNull { id -> state.allMembers().firstOrNull { it.id == id }?.let(Marks::mark) }
                            Box(
                                Modifier.fillMaxWidth().clip(Radius.small).background(entryFill(people, entry.everyone))
                                    .colourRail(railColors(people, entry.everyone), width = 3.dp, start = 3.dp, inset = 4.dp)
                                    .clickable(role = Role.Button) { onOpen(entry.key) }.padding(start = 10.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
                            ) { Text(entry.title, style = type.detailCaption, color = Hc.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
private val TODAY_LIFT = 3.dp
/** About one detail line plus the band's padding, as a share of an ordinary day's row. */
private const val BAND_WEIGHT = 0.75f
/** A slim row still holds its day label: one detail line and its padding. */
private const val EMPTY_WEIGHT = 0.8f
private const val EXTRA_ENTRY_WEIGHT = 0.8f
private const val EXTRA_ENTRIES_COUNTED = 3
private const val DAY_COLUMN_EMS = 4.6f
private const val DISC_EMS = 1.6f
private const val SMALL_DISC_EMS = 0.9f
private const val BOARD_DISC_EMS = 0.6f
private const val ONE_LINE_EMS = 13f
private const val HATCH_GAP = 6
private const val DAY_NUMBER_SCALE = 0.55f
private const val PEN_RING_EMS = 0.85f
/** The number stays at or above the detail tier's size however short today's row gets. */
private const val MIN_RING_SCALE = 0.7f
private const val MONTH_RING_EMS = 1.9f
private const val MONTH_TITLES = 2
private val MONTH_GAP = 6.dp
private const val MONTH_TILE = 0.7f
private const val MONTH_TILE_OUT = 0.3f
private const val NOW_WEIGHT = 1.4f
/** Rows that share the 10" screen and stay at the glance tier; more than this, and the board scrolls. */
private const val BOARD_FITTED_ROWS = 6
private const val BOARD_TWO_ACROSS = 4
private const val BOARD_THREE_ACROSS = 3
private const val ENDED_ALPHA = 0.55f
