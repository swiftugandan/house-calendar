package dev.housecalendar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.R
import dev.housecalendar.app.model.AgendaDay
import dev.housecalendar.app.model.ClashMark
import dev.housecalendar.app.model.DayWord
import dev.housecalendar.app.model.EntryRow
import dev.housecalendar.app.model.TakingMark
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.IconKey
import java.time.LocalTime

/** DESIGN.md "Agenda row": time column, people, then the entry line. Ended rows are muted, never hidden. */
@Composable
fun AgendaRowView(row: EntryRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Hc.colors
    val type = Hc.type
    val describe = listOfNotNull(
        row.start?.let { Words.time(it) } ?: stringResource(R.string.all_day),
        row.title,
        Words.people(row.people, row.everyone),
        Words.taking(row.taking),
        row.place.ifBlank { null },
    ).joinToString(", ")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.s)
            .alpha(if (row.ended) ENDED_ALPHA else 1f)
            .semantics(mergeDescendants = true) { contentDescription = describe },
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(TIME_COLUMN)) {
            if (row.start != null) {
                Text(Words.time(row.start), style = type.time, color = colors.ink)
                if (row.end != null) Text(Words.time(row.end), style = type.caption.copy(fontFamily = type.time.fontFamily), color = colors.inkMuted)
            } else {
                Text(stringResource(R.string.all_day), style = type.caption, color = colors.inkMuted)
            }
        }
        Box(Modifier.padding(top = 2.dp, end = Space.m)) {
            when {
                row.everyone -> GroupDisc(22.dp, stringResource(R.string.group_disc))
                else -> PeopleStack(row.people, 22.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventIcon(row.icon, 20.dp)
                Text(row.title, style = type.title, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (row.repeating) {
                    Spacer(Modifier.width(Space.xs))
                    Icon(HcIcons.Repeat, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
                }
            }
            DetailLine(row.place, row.taking)
            row.clashes.forEach { ClashLine(it) }
        }
    }
}

/** The second line of an entry: place, then who's taking them (DESIGN.md "Entry line"). */
@Composable
fun DetailLine(place: String, taking: TakingMark) {
    val colors = Hc.colors
    val takingText = Words.taking(taking)
    if (place.isBlank() && takingText == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (place.isNotBlank()) Text(place, style = Hc.type.caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (place.isNotBlank() && takingText != null) Text("·", style = Hc.type.caption, color = colors.inkMuted)
        when (taking) {
            is TakingMark.Person -> PersonDisc(taking.person, 18.dp, describe = false)
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
fun EventIcon(key: IconKey, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val vector = HcIcons.of(key) ?: return
    Icon(vector, contentDescription = null, tint = Hc.colors.ink, modifier = modifier.padding(end = Space.s).size(size))
}

/** DESIGN.md: the now line, a 2 dp `today` rule with the time at its left. */
@Composable
fun NowLine(now: LocalTime, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
        Text(Words.time(now), style = Hc.type.label.copy(fontFamily = Hc.type.time.fontFamily), color = Hc.colors.today, modifier = Modifier.widthIn(min = TIME_COLUMN))
        Box(Modifier.weight(1f).height(2.dp).background(Hc.colors.today, Radius.small))
    }
}

/** A sticky day heading: `headline` for today and tomorrow, `title` later; the holiday beside the date. */
@Composable
fun DayHeading(day: AgendaDay, modifier: Modifier = Modifier) {
    val colors = Hc.colors
    val near = day.word != DayWord.NONE
    Row(
        modifier.fillMaxWidth().background(colors.paper).padding(start = Space.gutter, end = Space.gutter, top = Space.l, bottom = Space.xs).semantics { heading() },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(Words.day(day.date, day.word), style = if (near) Hc.type.headline else Hc.type.title, color = colors.ink)
        if (near) Text(LocalDateText.current.dayShort(day.date), style = Hc.type.caption, color = colors.inkMuted, modifier = Modifier.padding(bottom = 4.dp))
        day.holidays.firstOrNull()?.let { holiday ->
            Spacer(Modifier.weight(1f))
            Icon(HcIcons.Holiday, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp).padding(bottom = 2.dp))
            Text(holiday, style = Hc.type.caption, color = colors.inkMuted, maxLines = 1)
        }
    }
}

private val TIME_COLUMN = 64.dp
private const val ENDED_ALPHA = 0.55f
