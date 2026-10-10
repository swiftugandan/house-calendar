package dev.housecalendar.sync

import dev.housecalendar.contracts.DeviceRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC FR-44: exactly one side of every pair dials, and a phone always dials the always-listening display. */
class DialingTest {
    private val low = TestOps.device(1)
    private val high = TestOps.device(2)

    @Test
    fun `a phone dials a display, and a display never dials a phone`() {
        assertTrue(Dialing.shouldDial(high, DeviceRole.Phone, low, DeviceRole.Display))
        assertFalse(Dialing.shouldDial(low, DeviceRole.Display, high, DeviceRole.Phone))
    }

    @Test
    fun `between two devices of the same role, the lower id dials`() {
        listOf(DeviceRole.Phone, DeviceRole.Display).forEach { role ->
            assertTrue(Dialing.shouldDial(low, role, high, role))
            assertFalse(Dialing.shouldDial(high, role, low, role))
        }
    }

    @Test
    fun `exactly one side dials, for every pair of roles`() {
        val roles = listOf(DeviceRole.Phone, DeviceRole.Display, DeviceRole.Unrecognised("CHILD_PHONE"))
        roles.forEach { a ->
            roles.forEach { b ->
                val dials = listOf(Dialing.shouldDial(low, a, high, b), Dialing.shouldDial(high, b, low, a))
                assertEquals("$a vs $b", 1, dials.count { it })
            }
        }
    }

    @Test
    fun `a role this version does not know is treated as a phone`() {
        assertTrue(Dialing.shouldDial(high, DeviceRole.Unrecognised("CHILD_PHONE"), low, DeviceRole.Display))
    }

    @Test
    fun `a link peer carries its role`() {
        assertEquals(DeviceRole.Display, LinkPeer("10.0.0.2:1234", low, "Kitchen", DeviceRole.Display).role)
    }
}
