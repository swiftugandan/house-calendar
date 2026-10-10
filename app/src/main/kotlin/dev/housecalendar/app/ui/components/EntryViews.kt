package dev.housecalendar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.R
import dev.housecalendar.app.model.AgendaDay
import dev.housecalendar.app.model.ClashMark
import dev.housecalendar.app.model.DayWord
import dev.housecalendar.app.model.EntryRow
import dev.housecalendar.app.model.PersonMark
import dev.housecalendar.app.model.TakingMark
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.IconKey
import java.time.LocalTime

/**
 * The family's active members, in order, for drawing things that belong to everyone (the colour rail of an
 * everyone entry). Provided once by the phone and display roots.
 */
val LocalMembers = compositionLocalOf { emptyList<PersonMark>() }

/** DESIGN.md "Colour rules": a person's tint behind their entries; `band` for everyone and for nobody. */
@Composable
fun entryFill(people: List<PersonMark>, everyone: Boolean): Color {
    val lead = people.firstOrNull()
    return if (everyone || lead == null) Hc.colors.band else Hc.colors.member(lead.colorIndex).tint
}

/** The fill colours of an entry's colour rail: its people, or every member for everyone. */
@Composable
fun railColors(people: List<PersonMark>, everyone: Boolean): List<Color> {
    val colors = Hc.colors
    val shown = if (everyone) LocalMembers.current else people
    return shown.map { colors.member(it.colorIndex).fill }.ifEmpty { listOf(colors.rule) }
}

/**
 * DESIGN.md "The colour rail": a vertical rail at the leading edge, one segment per person, drawn behind the
 * content so it always spans the card's full height whatever the content does.
 */
fun Modifier.colourRail(colors: List<Color>, width: Dp = 4.dp, start: Dp = 8.dp, inset: Dp = 10.dp, gap: Dp = 2.dp): Modifier = drawBehind {
    val railWidth = width.toPx()
    val top = inset.toPx()
    val gapPx = gap.toPx()
    val span = size.height - top * 2
    if (span <= 0f) return@drawBehind
    val segment = (span - gapPx * (colors.size - 1)) / colors.size
    colors.forEachIndexed { index, color ->
        drawRoundRect(
            color = color,
            topLeft = Offset(start.toPx(), top + index * (segment + gapPx)),
            size = Size(railWidth, segment),
            cornerRadius = CornerRadius(railWidth / 2),
        )
    }
}

/** An event's picture in a small tile of the card's own colour, a shade deeper (DESIGN.md "Iconography"). */
@Composable
fun IconTile(key: IconKey, tile: Color, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val vector = HcIcons.of(key) ?: return
    Box(modifier.size(size).clip(Radius.small).background(tile), contentAlignment = Alignment.Center) {
        Icon(vector, contentDescription = null, tint = Hc.colors.ink, modifier = Modifier.size(size * ICON_IN_TILE))
    }
}

/** The tile behind an icon: the lead person's colour a little deeper than the card, or ink for everyone. */
@Composable
fun tileColor(people: List<PersonMark>, everyone: Boolean, card: Color): Color {
    val lead = people.firstOrNull()
    val shade = if (everyone || lead == null) Hc.colors.ink.copy(alpha = NEUTRAL_TILE) else Hc.colors.member(lead.colorIndex).fill.copy(alpha = PERSON_TILE)
    return shade.compositeOver(card)
}

/**
 * DESIGN.md "Entry card": on the owner's tint with the colour rail, a measured time column, the icon tile,
 * the title and its details, and the people's discs. Ended entries fade; at large text the times go above.
 */
@Composable
fun AgendaRowView(row: EntryRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val describe = listOfNotNull(
        row.start?.let { Words.time(it) } ?: stringResource(R.string.all_day),
        row.title,
        Words.people(row.people, row.everyone),
        Words.taking(row.taking),
        row.place.ifBlank { null },
    ).joinToString(", ")
    val fill = entryFill(row.people, row.everyone)
    val rail = railColors(row.people, row.everyone)
    val timeColumn = timeColumnWidth()
    HcCard(
        modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 5.dp).alpha(if (row.ended) ENDED_ALPHA else 1f)
            .semantics(mergeDescendants = true) { contentDescription = describe },
        color = fill,
        onClick = onClick,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 72.dp).colourRail(rail).padding(start = 22.dp, end = Space.l, top = 14.dp, bottom = 14.dp)) {
            // WCAG 1.4.10 reflow: when large text makes the time column crowd out the title, times go above it.
            val stacked = timeColumn > maxWidth * STACK_FRACTION
            Column {
                if (stacked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.Bottom) { Times(row) }
                    Spacer(Modifier.height(Space.s))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!stacked) Column(Modifier.width(timeColumn)) { Times(row) }
                    EntryBody(row, fill)
                }
            }
        }
    }
}

@Composable
private fun Times(row: EntryRow) {
    val type = Hc.type
    if (row.start != null) {
        Text(Words.time(row.start), style = type.time, color = Hc.colors.ink, maxLines = 1, softWrap = false)
        if (row.end != null) Text(Words.time(row.end), style = type.caption.copy(fontFeatureSettings = "tnum"), color = Hc.colors.inkMuted, maxLines = 1, softWrap = false)
    } else {
        Text(stringResource(R.string.all_day), style = type.caption, color = Hc.colors.inkMuted)
    }
}

@Composable
private fun RowScope.EntryBody(row: EntryRow, fill: Color) {
    val colors = Hc.colors
    val type = Hc.type
    IconTile(row.icon, tileColor(row.people, row.everyone, fill), modifier = Modifier.padding(end = Space.m))
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, style = type.title, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (row.repeating) {
                Spacer(Modifier.width(Space.xs))
                Icon(HcIcons.Repeat, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
            }
        }
        DetailLine(row.place, row.taking, ring = fill)
        row.clashes.forEach { ClashLine(it) }
    }
    Spacer(Modifier.width(Space.s))
    when {
        row.everyone -> GroupDisc(26.dp, stringResource(R.string.group_disc))
        else -> PeopleStack(row.people, 26.dp, ring = fill)
    }
}

/** The second line of an entry: place, then who's taking them (DESIGN.md "Entry card"). */
@Composable
fun DetailLine(place: String, taking: TakingMark, ring: Color? = null) {
    val colors = Hc.colors
    val takingText = Words.taking(taking)
    if (place.isBlank() && takingText == null) return
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (place.isNotBlank()) Text(place, style = Hc.type.caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (place.isNotBlank() && takingText != null) Text("·", style = Hc.type.caption, color = colors.inkMuted)
        when (taking) {
            is TakingMark.Person -> PersonDisc(taking.person, 18.dp, describe = false, ring = ring)
            TakingMark.NeedsSomeone -> EmptyDisc(18.dp)
            TakingMark.None -> Unit
        }
        if (takingText != null) Text(takingText, style = Hc.type.caption, color = colors.inkMuted, maxLines = 1)
    }
}

/** DESIGN.md "Clash line": plain ink, a small overlap glyph, never a warning colour. */
@Composable
fun ClashLine(clash: ClashMark, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Icon(HcIcons.Clash, contentDescription = null, tint = Hc.colors.ink, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.clash_line, clash.person.name, clash.otherTitle, Words.time(clash.otherStart)), style = Hc.type.caption, color = Hc.colors.ink)
    }
}

/** An event's picture from the bundled set, or nothing for no icon. */
@Composable
fun EventIcon(key: IconKey, size: Dp, modifier: Modifier = Modifier) {
    val vector = HcIcons.of(key) ?: return
    Icon(vector, contentDescription = null, tint = Hc.colors.ink, modifier = modifier.padding(end = Space.s).size(size))
}

/** DESIGN.md: the now line, a 2 dp `today` rule starting with a dot, with the time at its left. */
@Composable
fun NowLine(now: LocalTime, modifier: Modifier = Modifier) {
    val today = Hc.colors.today
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(Words.time(now), style = Hc.type.time, color = today, modifier = Modifier.widthIn(min = timeColumnWidth()))
        Box(Modifier.size(NOW_DOT).clip(Radius.full).background(today))
        Box(Modifier.weight(1f).height(2.dp).background(today, Radius.full))
    }
}

/** A sticky day heading: `headline` for today and tomorrow, `title` later; the holiday beside the date. */
@Composable
fun DayHeading(day: AgendaDay, modifier: Modifier = Modifier) {
    val colors = Hc.colors
    val near = day.word != DayWord.NONE
    Row(
        modifier.fillMaxWidth().background(colors.ground).padding(start = Space.gutter + Space.xs, end = Space.gutter, top = Space.xl, bottom = Space.s).semantics { heading() },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(Words.day(day.date, day.word), style = if (near) Hc.type.headline else Hc.type.title, color = colors.ink)
        if (near) Text(LocalDateText.current.dayShort(day.date), style = Hc.type.caption, color = colors.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
        day.holidays.firstOrNull()?.let { holiday ->
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(Radius.full).background(colors.band).padding(horizontal = Space.s, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Icon(HcIcons.Holiday, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
                Text(holiday, style = Hc.type.caption, color = colors.inkMuted, maxLines = 1)
            }
        }
    }
}

/**
 * The time column fits the widest time in the device's clock format and font scale, measured rather than
 * fixed, so "10:00 PM" at 200% text is never clipped (DESIGN.md "Entry card").
 */
@Composable
private fun timeColumnWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = Hc.type.time
    val density = LocalDensity.current
    val samples = WIDEST_TIMES.map { Words.time(it) }
    val widest = remember(samples, style, density) { samples.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } }
    return with(density) { widest.toDp() } + Space.m
}

private val WIDEST_TIMES = listOf(LocalTime.of(10, 0), LocalTime.of(22, 0))
private val NOW_DOT = 8.dp
private const val STACK_FRACTION = 0.3f
private const val ENDED_ALPHA = 0.55f
private const val ICON_IN_TILE = 0.58f
private const val PERSON_TILE = 0.16f
private const val NEUTRAL_TILE = 0.06f
