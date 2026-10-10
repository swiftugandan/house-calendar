package dev.housecalendar.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * DESIGN.md colour tokens ("Kitchen Light"). The palette is fixed (dynamic colour is off) so a person's colour
 * is identical on every device of the family. Every pairing is checked by FoundationsTest against WCAG 2.2.
 */
@Immutable
data class HcColors(
    val ground: Color,
    val surface: Color,
    val sunk: Color,
    val band: Color,
    val rule: Color,
    val ink: Color,
    val inkMuted: Color,
    val action: Color,
    val onAction: Color,
    val today: Color,
    val onToday: Color,
    val scrim: Color,
    val shadow: Color,
    val members: List<MemberColor>,
    val isDark: Boolean,
) {
    fun member(index: Int): MemberColor = members[Math.floorMod(index, members.size)]
}

/**
 * A member's identity colour (DESIGN.md "Member colours"): [fill] behind [onFill] initials and in the colour
 * rail; [tint], the fill blended over the card surface, behind their entries; [accent] for their name.
 */
@Immutable
data class MemberColor(val fill: Color, val tint: Color, val accent: Color, val onFill: Color)

private val LIGHT_MEMBERS = listOf(0xFF2456A6, 0xFF0E6E73, 0xFF7B3F8C, 0xFF5C6B12, 0xFF4A44A8, 0xFFA63A62, 0xFF7A5A00, 0xFF3D5A73)
private val DARK_MEMBERS = listOf(0xFF8DB4F2, 0xFF6FCFD3, 0xFFD3A2E0, 0xFFBFD06A, 0xFFADA9F5, 0xFFF2A0BF, 0xFFE3B55B, 0xFF9DB8CF)
private const val LIGHT_TINT = 0.13f
private const val DARK_TINT = 0.16f
private const val LIGHT_SURFACE = 0xFFFFFFFF
private const val DARK_SURFACE = 0xFF161C22
private const val DARK_GROUND = 0xFF0B0F13

private fun members(fills: List<Long>, surface: Long, tint: Float, onFill: Color) = fills.map { argb ->
    val fill = Color(argb)
    MemberColor(fill = fill, tint = fill.copy(alpha = tint).compositeOver(Color(surface)), accent = fill, onFill = onFill)
}

val LightColors = HcColors(
    ground = Color(0xFFF2F4F6),
    surface = Color(LIGHT_SURFACE),
    sunk = Color(0xFFE8ECEF),
    band = Color(0xFFEEF1F3),
    rule = Color(0xFFDCE2E6),
    ink = Color(0xFF0E161D),
    inkMuted = Color(0xFF55616C),
    action = Color(0xFF0E161D),
    onAction = Color(0xFFFFFFFF),
    today = Color(0xFFC2351B),
    onToday = Color(0xFFFFFFFF),
    scrim = Color(0x730E161D),
    shadow = Color(0xFF0E161D),
    members = members(LIGHT_MEMBERS, LIGHT_SURFACE, LIGHT_TINT, Color.White),
    isDark = false,
)

val DarkColors = HcColors(
    ground = Color(DARK_GROUND),
    surface = Color(DARK_SURFACE),
    sunk = Color(0xFF1F272E),
    band = Color(0xFF1A2128),
    rule = Color(0xFF28313A),
    ink = Color(0xFFEEF2F4),
    inkMuted = Color(0xFF9EAAB4),
    action = Color(0xFFEEF2F4),
    onAction = Color(DARK_GROUND),
    today = Color(0xFFFF7B5C),
    onToday = Color(0xFF1A0B06),
    scrim = Color(0xB3000000),
    shadow = Color.Black,
    members = members(DARK_MEMBERS, DARK_SURFACE, DARK_TINT, Color(DARK_GROUND)),
    isDark = true,
)
