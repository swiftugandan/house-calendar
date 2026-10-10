package dev.housecalendar.app.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DESIGN.md "Depth". In light, cards rest on the ground with a soft, cool-tinted shadow; floating things
 * (sheets, the Add pill, the segmented thumb) lift further. Shadows vanish on a dark ground, so in dark
 * the same elements are lifted by tone and a hairline instead.
 */
enum class Depth(internal val elevation: Dp, internal val ambient: Float, internal val spot: Float) {
    RAISED(elevation = 4.dp, ambient = 0.05f, spot = 0.07f),
    FLOATING(elevation = 14.dp, ambient = 0.08f, spot = 0.14f),
}

/** Lifts this element by [depth], clipped to [shape]; apply before its background. */
@Composable
fun Modifier.lifted(depth: Depth, shape: Shape): Modifier {
    val colors = Hc.colors
    return if (colors.isDark) {
        border(1.dp, colors.rule, shape)
    } else {
        shadow(depth.elevation, shape, clip = false, ambientColor = colors.shadow.copy(alpha = depth.ambient), spotColor = colors.shadow.copy(alpha = depth.spot))
    }
}
