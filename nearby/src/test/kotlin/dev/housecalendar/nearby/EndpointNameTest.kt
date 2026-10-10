package dev.housecalendar.nearby

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/** SAD §3.7: the Nearby endpoint name carries `role|deviceId|name`. */
class EndpointNameTest {
    private val device = DeviceId(UUID.fromString("00000000-0000-4000-8000-000000000002"))

    @Test
    fun `role, id and name round-trip, and names may contain the separator`() {
        val name = EndpointName.encode(DeviceRole.Display, device, "Kitchen | wall")
        assertEquals("DISPLAY|$device|Kitchen | wall", name)
        assertEquals(EndpointName.Parsed(device, "Kitchen | wall", DeviceRole.Display), EndpointName.parse(name))
    }

    @Test
    fun `an unknown role is kept as unrecognised`() {
        assertEquals(DeviceRole.Unrecognised("CHILD_PHONE"), EndpointName.parse("CHILD_PHONE|$device|Ada")?.role)
    }

    @Test
    fun `anything without a valid id is ignored`() {
        assertNull(EndpointName.parse("PHONE|not-a-uuid|Sam"))
        assertNull(EndpointName.parse("$device"))
        assertNull(EndpointName.parse(""))
    }

    @Test
    fun `names are cut to 40 characters`() {
        assertEquals(40, EndpointName.parse(EndpointName.encode(DeviceRole.Phone, device, "x".repeat(70)))?.name?.length)
    }
}
