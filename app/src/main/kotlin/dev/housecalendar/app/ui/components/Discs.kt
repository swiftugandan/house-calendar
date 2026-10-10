package dev.housecalendar.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.housecalendar.app.model.PersonMark
import dev.housecalendar.app.ui.theme.Fonts
import dev.housecalendar.app.ui.theme.Hc
import androidx.compose.ui.text.font.FontWeight

/** DESIGN.md "Person disc": the member's colour behind their initial, the only place their colour fills. */
@Composable
fun PersonDisc(person: PersonMark, size: Dp, modifier: Modifier = Modifier, describe: Boolean = true) {
    val color = Hc.colors.member(person.colorIndex)
    val initialSize = with(LocalDensity.current) { (size * INITIAL_RATIO).toSp() / fontScale }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.fill)
            .then(if (describe) Modifier.semantics { contentDescription = person.name } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            person.initial,
            style = TextStyle(fontFamily = Fonts.bricolage, fontWeight = FontWeight(700), fontSize = initialSize, textAlign = TextAlign.Center),
            color = color.onFill,
        )
    }
}

/**
 * DESIGN.md "The empty disc": a dashed ring where a person would be, for a lift that needs someone. When
 * [filledBy] arrives the dashes close and it fills with that person's colour (the one moment of delight).
 */
@Composable
fun EmptyDisc(size: Dp, modifier: Modifier = Modifier, filledBy: PersonMark? = null, description: String? = null) {
    val muted = Hc.colors.inkMuted
    val closed by animateFloatAsState(if (filledBy != null) 1f else 0f, tween(CLOSE_MS), label = "close")
    Box(modifier.size(size).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        if (filledBy != null && closed >= 1f) {
            PersonDisc(filledBy, size, describe = false)
        } else {
            Canvas(Modifier.size(size)) {
                val stroke = 2.dp.toPx()
                val dash = (this.size.minDimension * Math.PI.toFloat() / DASHES) * (1f - closed * 0.5f)
                drawCircle(
                    color = muted,
                    radius = this.size.minDimension / 2 - stroke,
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * (1 + closed), dash * (1 - closed))), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** DESIGN.md "Group disc": an ink ring with "All", for everyone. */
@Composable
fun GroupDisc(size: Dp, label: String, modifier: Modifier = Modifier) {
    val ink = Hc.colors.ink
    val textSize = with(LocalDensity.current) { (size * GROUP_RATIO).toSp() / fontScale }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 2.dp.toPx()
            drawCircle(ink, radius = this.size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        }
        Text(label, style = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = textSize), color = ink)
    }
}

/** A row of small discs, overlapping slightly, for the people of an entry. */
@Composable
fun PeopleStack(people: List<PersonMark>, size: Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-(size / OVERLAP))) {
        people.forEach { PersonDisc(it, size) }
    }
}

/**
 * DESIGN.md "The pen ring": today's date circled in vermilion, slightly irregular (rotated 6°, 3% oval),
 * the one hand-drawn gesture in the app. [progress] draws it as a stroke on a new day.
 */
@Composable
fun PenRing(size: Dp, modifier: Modifier = Modifier, progress: Float = 1f) {
    val color = Hc.colors.today
    Canvas(modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val width = this.size.width - stroke * 2
        val height = width * OVAL
        rotate(TILT_DEGREES) {
            drawArc(
                color = color,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP * progress,
                useCenter = false,
                topLeft = Offset(stroke, (this.size.height - height) / 2),
                size = Size(width, height),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private const val INITIAL_RATIO = 0.45f
private const val GROUP_RATIO = 0.3f
private const val DASHES = 8f
private const val CLOSE_MS = 320
private const val OVERLAP = 5
private const val OVAL = 0.97f
private const val TILT_DEGREES = -6f
private const val START_ANGLE = -100f
private const val SWEEP = 372f
