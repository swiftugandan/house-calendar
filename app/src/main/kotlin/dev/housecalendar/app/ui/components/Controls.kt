package dev.housecalendar.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Depth
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.MemberColor
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.app.ui.theme.lifted

/** DESIGN.md "Motion": a pressed card or button settles to 97% while held, and springs back on release. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** DESIGN.md "Depth": a card resting on the ground, on [color] (a person's tint, `band`, or `surface`). */
@Composable
fun HcCard(
    modifier: Modifier = Modifier,
    color: Color = Hc.colors.surface,
    shape: Shape = Radius.large,
    depth: Depth? = Depth.RAISED,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .then(if (depth != null) Modifier.lifted(depth, shape) else Modifier)
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier),
        content = content,
    )
}

/** The rows of a [GroupedCard], in order; add one with [row], optionally inside an `if`. */
class GroupedRows internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** DESIGN.md "Grouped lists": related rows in one card with hairlines between them, as in iOS Settings. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, build: GroupedRows.() -> Unit) {
    val rows = GroupedRows().apply(build).rows
    HcCard(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Rule(Modifier.padding(start = GROUPED_RULE_INSET))
            row()
        }
    }
}

/** The one primary action on a screen: an `action` pill whose label says what will happen. */
@Composable
fun OutcomeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 56.dp, floating: Boolean = false) {
    val colors = Hc.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .pressScale(interaction)
            .then(if (floating && enabled) Modifier.lifted(Depth.FLOATING, Radius.full) else Modifier)
            .clip(Radius.full)
            .background(if (enabled) colors.action else colors.sunk)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.s),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (enabled) colors.onAction else colors.inkMuted
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = Hc.type.title, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A secondary action: a `surface` pill with a hairline, never competing with the [OutcomeButton]. */
@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val colors = Hc.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = Space.touch)
            .pressScale(interaction)
            .clip(Radius.full)
            .border(BorderStroke(1.dp, colors.rule), Radius.full)
            .background(colors.surface)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) colors.ink else colors.inkMuted
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = Hc.type.label, color = tint)
    }
}

/** An icon button with a 48 dp target (NFR-A11Y-1), optionally on a `surface` disc so it reads on any ground. */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Hc.colors.ink, filled: Boolean = false) {
    Box(
        modifier
            .size(Space.touch)
            .clip(Radius.full)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) Box(Modifier.size(40.dp).lifted(Depth.RAISED, Radius.full).clip(Radius.full).background(Hc.colors.surface))
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(if (filled) 20.dp else 24.dp))
    }
}

/**
 * DESIGN.md "Person chip" and option chips, one component:
 * - with [person], unselected is `surface` with a hairline, selected takes their tint and a border in their colour
 * - without, selected fills with `action` ink
 * A check confirms selection for people, so colour is never the only signal.
 */
@Composable
fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Checkbox,
    person: MemberColor? = null,
    minHeight: Dp = Space.touch,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = Hc.colors
    val interaction = remember { MutableInteractionSource() }
    val background = when {
        !selected -> colors.surface
        person != null -> person.tint
        else -> colors.action
    }
    val border = when {
        !selected -> BorderStroke(1.dp, colors.rule)
        person != null -> BorderStroke(2.dp, person.fill)
        else -> BorderStroke(1.dp, colors.action)
    }
    val text = if (selected && person == null) colors.onAction else colors.ink
    Row(
        modifier
            .heightIn(min = minHeight)
            .pressScale(interaction)
            .clip(Radius.full)
            .background(background)
            .border(border, Radius.full)
            .clickable(interaction, indication = null, role = role, onClick = onClick)
            .padding(start = if (leading != null) 6.dp else Space.l, end = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        leading?.invoke()
        Text(label, style = Hc.type.label, color = text, maxLines = 1)
        if (selected && person != null) Icon(HcIcons.Check, contentDescription = null, tint = person.fill, modifier = Modifier.size(16.dp))
    }
}

/**
 * DESIGN.md "Segmented control": a `sunk` track with a `surface` thumb that slides to the chosen option.
 * At a font scale of 1.5 or more it stacks into a full-width list rather than clip its labels (NFR-A11Y-2).
 */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = Space.touch) {
    val colors = Hc.colors
    val stacked = LocalDensity.current.fontScale >= STACK_AT_SCALE
    if (stacked) {
        Column(modifier.clip(Radius.medium).background(colors.sunk).padding(Space.xs).selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            options.forEach { (value, label) ->
                val chosen = value == selected
                Box(
                    Modifier.fillMaxWidth().heightIn(min = height).clip(Radius.small).background(if (chosen) colors.surface else Color.Transparent)
                        .selectable(chosen, role = Role.Tab) { onSelect(value) }.padding(Space.s),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = Hc.type.label, color = if (chosen) colors.ink else colors.inkMuted, maxLines = 2) }
            }
        }
        return
    }
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(modifier.clip(Radius.full).background(colors.sunk).padding(THUMB_INSET).height(height - THUMB_INSET * 2).selectableGroup()) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, tween(THUMB_MS), label = "thumb")
        Box(Modifier.offset(x = offset).width(segment).fillMaxHeight().lifted(Depth.RAISED, Radius.full).clip(Radius.full).background(colors.surface))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { (value, label) ->
                val chosen = value == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(Radius.full).selectable(chosen, role = Role.Tab) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = Hc.type.label, color = if (chosen) colors.ink else colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** A small section heading inside a sheet or settings page. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Hc.type.label, color = Hc.colors.inkMuted, modifier = modifier.semantics { heading() })
}

/** A hairline, for rows inside a grouped card and for the wall's grid. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = Hc.colors.rule) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A screen's top bar: back on a surface disc, a title, and up to two actions. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, backLabel: String = "", actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.s).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (onBack != null) IconAction(HcIcons.Back, backLabel, onBack, filled = true) else Spacer(Modifier.width(Space.s))
        Text(title, style = Hc.type.headline, color = Hc.colors.ink, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** A single-line text input in the input well style (DESIGN.md: `sunk`, `r-md`). */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    capitalize: Boolean = true,
    label: String? = null,
    secret: Boolean = false,
) {
    val colors = Hc.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        label = label?.let { { Text(it, style = Hc.type.caption) } },
        placeholder = { Text(placeholder, style = Hc.type.body, color = colors.inkMuted) },
        textStyle = Hc.type.body.copy(color = colors.ink),
        shape = Radius.medium,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.sunk, unfocusedContainerColor = colors.sunk,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.today, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
            focusedLabelColor = colors.inkMuted, unfocusedLabelColor = colors.inkMuted,
        ),
    )
}

/** A full-width row with a label, optional explanation, and a switch. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, explanation: String? = null) {
    val colors = Hc.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = Space.touch).selectable(checked, role = Role.Switch) { onChange(!checked) }.padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Hc.type.body, color = colors.ink)
            if (explanation != null) Text(explanation, style = Hc.type.caption, color = colors.inkMuted)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.action, checkedThumbColor = colors.onAction, checkedBorderColor = colors.action,
                uncheckedTrackColor = colors.sunk, uncheckedBorderColor = colors.sunk, uncheckedThumbColor = colors.surface,
            ),
        )
    }
}

/** A tappable choice: a card that takes a 2 dp ink border and a check when chosen. */
@Composable
fun ChoiceRow(label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, explanation: String? = null, leading: (@Composable () -> Unit)? = null) {
    val colors = Hc.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressScale(interaction)
            .lifted(Depth.RAISED, Radius.large)
            .clip(Radius.large)
            .background(colors.surface)
            .border(if (chosen) 2.dp else 0.dp, if (chosen) colors.action else Color.Transparent, Radius.large)
            .selectable(chosen, interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(label, style = Hc.type.title, color = colors.ink)
            if (explanation != null) Text(explanation, style = Hc.type.caption, color = colors.inkMuted)
        }
        if (chosen) Icon(HcIcons.Check, contentDescription = null, tint = colors.action, modifier = Modifier.size(22.dp))
    }
}

private const val STACK_AT_SCALE = 1.5f
private const val PRESSED_SCALE = 0.97f
private const val THUMB_MS = 220
private val THUMB_INSET = 4.dp
/** Hairlines start after the row glyph, as in iOS grouped lists. */
private val GROUPED_RULE_INSET = 50.dp
