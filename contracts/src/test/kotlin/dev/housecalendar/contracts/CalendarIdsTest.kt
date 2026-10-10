package dev.housecalendar.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class CalendarIdsTest {
    @Test
    fun `the calendar namespace is its own, never House Points'`() {
        assertEquals(UUID.fromString("0d1ccd49-f501-4b47-9bb0-3e4dfc92f9bc"), CalendarIds.NAMESPACE)
        assertNotEquals(UUID.fromString("6f1d3c2a-8b47-4f0e-9a51-2c7e5d9b4a13"), CalendarIds.NAMESPACE)
    }

    @Test
    fun `an imported event's id is UUIDv5 of ext plus its UID (SPEC FR-54)`() {
        assertEquals(EventId(UUID.fromString("5693ea7d-db56-507c-bb9b-c5ab7ef6a36d")), CalendarIds.externalEvent("inset-1@school"))
    }

    @Test
    fun `an imported holiday's id is UUIDv5 of holiday plus its UID`() {
        assertEquals(HolidayId(UUID.fromString("fc98b892-845e-5fce-af86-9b5ba089bd5a")), CalendarIds.externalHoliday("hols-1@school"))
    }

    @Test
    fun `the same UID gives the same id on every device, and events and holidays never collide`() {
        assertEquals(CalendarIds.externalEvent("x@y"), CalendarIds.externalEvent("x@y"))
        assertNotEquals(CalendarIds.externalEvent("x@y").uuid, CalendarIds.externalHoliday("x@y").uuid)
    }

    @Test
    fun `random event ids are time-ordered v7`() {
        val earlier = CalendarIds.randomEvent(1_000L)
        val later = CalendarIds.randomEvent(2_000L)
        assertEquals(7, later.uuid.version())
        assertEquals(-1, Integer.signum(earlier.compareTo(later)))
    }
}
