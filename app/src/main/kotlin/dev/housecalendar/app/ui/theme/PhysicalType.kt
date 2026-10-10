package dev.housecalendar.app.ui.theme

import java.time.LocalTime

/**
 * Display-mode type sized in millimetres (SPEC NFR-A11Y-4, DESIGN.md "Display type"): an x-height of 0.2°
 * of visual angle at 2 m for the glance tier and at 0.6 m for the detail tier, converted to pixels through
 * the screen's physical density and the bundled fonts' measured x-heights.
 */
object PhysicalType {
    /** 2000 mm × tan(0.2°). */
    const val GLANCE_X_HEIGHT_MM: Float = 6.98f

    /** 600 mm × tan(0.2°). */
    const val DETAIL_X_HEIGHT_MM: Float = 2.09f

    /** Measured from the bundled font files (OS/2 sxHeight ÷ unitsPerEm). */
    const val BRICOLAGE_X_HEIGHT: Float = 0.528f
    const val ATKINSON_X_HEIGHT: Float = 0.496f

    private const val MM_PER_INCH: Float = 25.4f
    private const val PERCENT: Float = 100f

    /** Reported densities outside this range are wrong (some tablets report 0 or a placeholder). */
    private val PLAUSIBLE_PPI = 90f..640f

    /** The screen's pixels per inch: its physical [xdpi] when plausible, else its density bucket. */
    fun ppi(xdpi: Float, densityDpi: Int): Float = if (xdpi in PLAUSIBLE_PPI) xdpi else densityDpi.toFloat()

    /** The font size in pixels whose x-height is [xHeightMm], scaled by the display's size check. */
    fun fontPx(xHeightMm: Float, xHeightRatio: Float, ppi: Float, sizePercent: Int): Float =
        xHeightMm / xHeightRatio / MM_PER_INCH * ppi * sizePercent / PERCENT
}

/** A display's waking hours (SPEC FR-31). A range whose end is before its start runs past midnight. */
data class DayHours(val starts: LocalTime, val ends: LocalTime) {
    fun isDay(time: LocalTime): Boolean = when {
        starts == ends -> true
        starts < ends -> time >= starts && time < ends
        else -> time >= starts || time < ends
    }
}
