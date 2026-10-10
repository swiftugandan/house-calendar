package dev.housecalendar.app.ui

import dev.housecalendar.app.date
import dev.housecalendar.app.time
import dev.housecalendar.app.ui.format.DateText
import dev.housecalendar.app.ui.icons.IconSuggester
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.DayHours
import dev.housecalendar.app.ui.theme.PhysicalType
import dev.housecalendar.contracts.IconKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/** Formatting, icons, physical type and day hours (DESIGN.md). */
class PresentationTest {
    private val uk24 = DateText(Locale.UK, is24Hour = true, today = date("2026-10-10"))
    private val us12 = DateText(Locale.US, is24Hour = false, today = date("2026-10-10"))

    @Test
    fun `times follow the device's 12 or 24 hour setting`() {
        assertEquals("17:00", uk24.time(time("17:00")))
        assertEquals("09:05", uk24.time(time("09:05")))
        assertEquals("5:00 PM", us12.time(time("17:00")))
    }

    @Test
    fun `dates are written out, with the year only when it is not this year`() {
        assertEquals("Tue 20 Oct", uk24.dayShort(date("2026-10-20")))
        assertEquals("Wed 20 Oct 2027", uk24.dayShort(date("2027-10-20")))
        assertEquals("Saturday 10 October", uk24.dayLong(date("2026-10-10")))
        assertEquals("Tue", uk24.weekdayShort(date("2026-10-20")))
        assertEquals("October 2026", uk24.month(YearMonth.of(2026, 10)))
    }

    @Test
    fun `icons are suggested from English keywords, birthdays before parties`() {
        assertEquals(IconKey("swim"), IconSuggester.suggest("Swimming lessons"))
        assertEquals(IconKey("swim"), IconSuggester.suggest("POOL!"))
        assertEquals(IconKey("tooth"), IconSuggester.suggest("Dentist (Ada)"))
        assertEquals(IconKey("cake"), IconSuggester.suggest("Ada's birthday party"))
        assertEquals(IconKey("party"), IconSuggester.suggest("Party at Mia's"))
        assertEquals(IconKey("football"), IconSuggester.suggest("Football training"))
        assertEquals(IconKey.NONE, IconSuggester.suggest("Something else"))
    }

    @Test
    fun `every suggested icon exists in the catalogue, and the catalogue matches DESIGN dot md`() {
        val keys = HcIcons.catalogue.map { it.first }
        assertEquals(40, keys.size)
        assertEquals(keys.toSet().size, keys.size)
        IconSuggester.keywords.values.forEach { assertTrue("$it missing", it in keys) }
    }

    @Test
    fun `display type is sized in millimetres from the physical density`() {
        val ppi = PhysicalType.ppi(xdpi = 149.5f, densityDpi = 160)
        assertEquals(149.5f, ppi, 0.01f)
        assertEquals(24.9f, PhysicalType.fontPx(PhysicalType.DETAIL_X_HEIGHT_MM, PhysicalType.ATKINSON_X_HEIGHT, ppi, 100), 0.3f)
        assertEquals(77.8f, PhysicalType.fontPx(PhysicalType.GLANCE_X_HEIGHT_MM, PhysicalType.BRICOLAGE_X_HEIGHT, ppi, 100), 0.5f)
        assertEquals(29.9f, PhysicalType.fontPx(PhysicalType.DETAIL_X_HEIGHT_MM, PhysicalType.ATKINSON_X_HEIGHT, ppi, 120), 0.4f)
    }

    @Test
    fun `an implausible reported density falls back to the density bucket`() {
        assertEquals(160f, PhysicalType.ppi(xdpi = 0f, densityDpi = 160), 0.01f)
        assertEquals(213f, PhysicalType.ppi(xdpi = 2000f, densityDpi = 213), 0.01f)
    }

    @Test
    fun `day hours, including a range that spans midnight`() {
        val usual = DayHours(time("06:30"), time("22:00"))
        assertTrue(usual.isDay(time("06:30")))
        assertTrue(usual.isDay(time("21:59")))
        assertFalse(usual.isDay(time("22:00")))
        assertFalse(usual.isDay(time("03:00")))
        val late = DayHours(time("08:00"), time("01:00"))
        assertTrue(late.isDay(time("00:30")))
        assertFalse(late.isDay(time("03:00")))
        assertTrue(DayHours(time("07:00"), time("07:00")).isDay(time("03:00")))
    }
}
