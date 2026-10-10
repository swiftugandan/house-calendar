package dev.housecalendar.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Radius
import dev.housecalendar.app.ui.theme.Space

/** The one primary action on a screen; its label says what will happen (DESIGN.md "Add sheet" 7). */
@Composable
fun OutcomeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 56.dp) {
    val colors = Hc.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(Radius.medium)
            .background(if (enabled) colors.action else colors.sunk)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.s),
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

/** A secondary action: outlined, never competing with the [OutcomeButton]. */
@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val colors = Hc.colors
    Row(
        modifier = modifier
            .heightIn(min = Space.touch)
            .clip(Radius.medium)
            .border(BorderStroke(1.dp, colors.rule), Radius.medium)
            .background(colors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Space.s),
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

/** An icon button with a 48 dp target (NFR-A11Y-1). */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Hc.colors.ink) {
    Box(
        modifier
            .size(Space.touch)
            .clip(Radius.full)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/**
 * DESIGN.md segmented control: at a font scale of 1.5 or more it stacks into a full-width list rather
 * than clip its labels (SPEC NFR-A11Y-2).
 */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = Hc.colors
    val stacked = LocalDensity.current.fontScale >= STACK_AT_SCALE
    val content: @Composable (RowScope?) -> Unit = { row ->
        options.forEach { (value, label) ->
            val chosen = value == selected
            val weight = row?.run { Modifier.weight(1f) } ?: Modifier.fillMaxWidth()
            Box(
                weight
                    .heightIn(min = Space.touch)
                    .clip(Radius.small)
                    .background(if (chosen) colors.surface else Color.Transparent)
                    .then(if (chosen) Modifier.border(1.dp, colors.rule, Radius.small) else Modifier)
                    .selectable(chosen, role = Role.Tab) { onSelect(value) }
                    .padding(horizontal = Space.s, vertical = Space.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Hc.type.label, color = if (chosen) colors.ink else colors.inkMuted, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val frame = modifier.clip(Radius.small).background(colors.sunk).padding(Space.xs).selectableGroup()
    if (stacked) Column(frame, verticalArrangement = Arrangement.spacedBy(Space.xs)) { content(null) } else Row(frame) { content(this) }
}

/** A small section heading inside a sheet or settings page. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Hc.type.label, color = Hc.colors.inkMuted, modifier = modifier.semantics { heading() })
}

/** A thin rule: separation by line, not by cards (DESIGN.md "No cards"). */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = Hc.colors.rule) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A screen's top bar: back, a title, and up to two actions. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, backLabel: String = "", actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.xs).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconAction(dev.housecalendar.app.ui.icons.HcIcons.Back, backLabel, onBack) else Spacer(Modifier.width(Space.m))
        Text(title, style = Hc.type.headline, color = Hc.colors.ink, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** A single-line text input in the input well style (DESIGN.md: `sunk`, `r-sm`). */
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
        shape = Radius.small,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password) else KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.sunk, unfocusedContainerColor = colors.sunk,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.action, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
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
            colors = SwitchDefaults.colors(checkedTrackColor = colors.action, checkedThumbColor = colors.onAction, uncheckedTrackColor = colors.sunk, uncheckedBorderColor = colors.rule, uncheckedThumbColor = colors.inkMuted),
        )
    }
}

/** A tappable choice row: a radio-like option in a list, with an optional explanation. */
@Composable
fun ChoiceRow(label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, explanation: String? = null, leading: (@Composable () -> Unit)? = null) {
    val colors = Hc.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Radius.medium)
            .background(if (chosen) colors.surface else Color.Transparent)
            .border(1.dp, if (chosen) colors.action else colors.rule, Radius.medium)
            .selectable(chosen, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(label, style = Hc.type.title, color = colors.ink)
            if (explanation != null) Text(explanation, style = Hc.type.caption, color = colors.inkMuted)
        }
        if (chosen) Icon(dev.housecalendar.app.ui.icons.HcIcons.Check, contentDescription = null, tint = colors.action, modifier = Modifier.size(22.dp))
    }
}

private const val STACK_AT_SCALE = 1.5f
