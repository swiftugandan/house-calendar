package dev.housecalendar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.housecalendar.app.R

@OptIn(ExperimentalTextApi::class)
private fun variable(resource: Int, weight: Int, vararg extra: FontVariation.Setting) = Font(
    resId = resource,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

/** DESIGN.md "Typography": three OFL families, bundled (SAD ADR-7). */
object Fonts {
    val bricolage = FontFamily(
        variable(R.font.bricolage, 600, FontVariation.Setting("opsz", 36f)),
        variable(R.font.bricolage, 700, FontVariation.Setting("opsz", 48f)),
    )
    val atkinson = FontFamily(variable(R.font.atkinson_next, 400), variable(R.font.atkinson_next, 700))
    val mono = FontFamily(variable(R.font.atkinson_mono, 400), variable(R.font.atkinson_mono, 600))
}

/** DESIGN.md phone type scale (sp). Nothing smaller than 14 sp anywhere. */
@Immutable
data class HcType(
    val largeTitle: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val time: TextStyle,
    val timeStrong: TextStyle,
    val initials: TextStyle,
)

val PhoneType = HcType(
    largeTitle = TextStyle(fontFamily = Fonts.bricolage, fontWeight = FontWeight(700), fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em),
    headline = TextStyle(fontFamily = Fonts.bricolage, fontWeight = FontWeight(700), fontSize = 24.sp, lineHeight = 30.sp),
    title = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = 17.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    label = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    time = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
    timeStrong = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
    initials = TextStyle(fontFamily = Fonts.bricolage, fontWeight = FontWeight(700), fontSize = 18.sp, lineHeight = 20.sp),
)

/** Display-mode type (DESIGN.md "Display type"), built from physical sizes by the display root. */
@Immutable
data class DisplayType(
    val glanceDisplay: TextStyle,
    val glanceText: TextStyle,
    val glanceTime: TextStyle,
    val detail: TextStyle,
    val detailStrong: TextStyle,
    val detailTime: TextStyle,
    val detailCaption: TextStyle,
    val glanceSize: TextUnit,
    val detailSize: TextUnit,
) {
    companion object {
        /** [glance] and [detail] are the Atkinson sizes; Bricolage's is derived from the x-height ratio. */
        fun of(glanceBricolage: TextUnit, glance: TextUnit, detail: TextUnit): DisplayType {
            val glanceLine = (glance.value * GLANCE_LINE).sp
            val detailLine = (detail.value * DETAIL_LINE).sp
            return DisplayType(
                glanceDisplay = TextStyle(fontFamily = Fonts.bricolage, fontWeight = FontWeight(700), fontSize = glanceBricolage, lineHeight = (glanceBricolage.value * GLANCE_LINE).sp),
                glanceText = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = glance, lineHeight = glanceLine),
                glanceTime = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = glance, lineHeight = glanceLine, fontFeatureSettings = "tnum"),
                detail = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(400), fontSize = detail, lineHeight = detailLine),
                detailStrong = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(700), fontSize = detail, lineHeight = detailLine),
                detailTime = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(400), fontSize = detail, lineHeight = detailLine, fontFeatureSettings = "tnum"),
                detailCaption = TextStyle(fontFamily = Fonts.atkinson, fontWeight = FontWeight(400), fontSize = detail, lineHeight = detailLine),
                glanceSize = glance,
                detailSize = detail,
            )
        }

        private const val GLANCE_LINE = 1.1f
        private const val DETAIL_LINE = 1.2f
    }
}

/** DESIGN.md spacing scale (dp). */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val gutter = 16.dp
    val displayGutter = 24.dp

    /** NFR-A11Y-1: no touch target smaller than this. */
    val touch = 48.dp

    /** DESIGN.md: display sheets use larger targets. */
    val displayTouch = 64.dp
}

/** DESIGN.md radius tokens: nothing else is used. */
object Radius {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val full = CircleShape
}

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalType = staticCompositionLocalOf { PhoneType }

object Hc {
    val colors: HcColors @Composable get() = LocalColors.current
    val type: HcType @Composable get() = LocalType.current
}

@Composable
fun HouseCalendarTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.action, onPrimary = colors.onAction, background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, onSurfaceVariant = colors.inkMuted, surfaceVariant = colors.sunk,
            outline = colors.rule, outlineVariant = colors.rule, error = colors.today,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.sunk, scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.action, onPrimary = colors.onAction, background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, onSurfaceVariant = colors.inkMuted, surfaceVariant = colors.sunk,
            outline = colors.rule, outlineVariant = colors.rule, error = colors.today,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.sunk, scrim = colors.scrim,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides PhoneType) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(extraSmall = Radius.small, small = Radius.small, medium = Radius.medium, large = Radius.large, extraLarge = Radius.large),
            content = content,
        )
    }
}
