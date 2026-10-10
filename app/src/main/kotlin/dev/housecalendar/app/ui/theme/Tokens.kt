package dev.housecalendar.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * DESIGN.md colour tokens. The palette is fixed (dynamic colour is off) so a person's colour is identical
 * on every device of the family. Every pairing is checked by FoundationsTest against WCAG 2.2.
 */
@Immutable
data class HcColors(
    val paper: Color,
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
    val members: List<MemberColor>,
    val isDark: Boolean,
) {
    fun member(index: Int): MemberColor = members[Math.floorMod(index, members.size)]
}

/** A member's identity colour: [fill] behind [onFill] initials; [accent] for marks and text on paper. */
@Immutable
data class MemberColor(val fill: Color, val accent: Color, val onFill: Color)

private val LIGHT_MEMBERS = listOf(0xFF2456A6, 0xFF0E6E73, 0xFF7B3F8C, 0xFF5C6B12, 0xFF4A44A8, 0xFFA63A62, 0xFF7A5A00, 0xFF3D5A73)
private val DARK_MEMBERS = listOf(0xFF8DB4F2, 0xFF6FCFD3, 0xFFD3A2E0, 0xFFBFD06A, 0xFFADA9F5, 0xFFF2A0BF, 0xFFE3B55B, 0xFF9DB8CF)
private const val DARK_GROUND = 0xFF121A1F

val LightColors = HcColors(
    paper = Color(0xFFF5F7F6),
    surface = Color(0xFFFFFFFF),
    sunk = Color(0xFFE9EEEC),
    band = Color(0xFFE4EAE7),
    rule = Color(0xFFD3DAD7),
    ink = Color(0xFF18232B),
    inkMuted = Color(0xFF52606A),
    action = Color(0xFF1E6B4E),
    onAction = Color(0xFFFFFFFF),
    today = Color(0xFFB3361C),
    onToday = Color(0xFFFFFFFF),
    scrim = Color(0x8C18232B),
    members = LIGHT_MEMBERS.map { MemberColor(Color(it), Color(it), Color.White) },
    isDark = false,
)

val DarkColors = HcColors(
    paper = Color(DARK_GROUND),
    surface = Color(0xFF1B252B),
    sunk = Color(0xFF243037),
    band = Color(0xFF22303A),
    rule = Color(0xFF2C3940),
    ink = Color(0xFFE6EDEA),
    inkMuted = Color(0xFFA3B1AB),
    action = Color(0xFF6CC9A0),
    onAction = Color(0xFF0D1A14),
    today = Color(0xFFFF8F6B),
    onToday = Color(0xFF1A0D08),
    scrim = Color(0xB3000000),
    members = DARK_MEMBERS.map { MemberColor(Color(it), Color(it), Color(DARK_GROUND)) },
    isDark = true,
)
