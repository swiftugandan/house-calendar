package dev.housecalendar.lan

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.FamilyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/** SAD §3.6: what a device advertises over mDNS, and what a peer accepts back. */
class LanRecordTest {
    private val family = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
    private val self = DeviceId(UUID.fromString("00000000-0000-4000-8000-000000000001"))
    private val other = DeviceId(UUID.fromString("00000000-0000-4000-8000-000000000002"))

    private fun bytes(attributes: Map<String, String>): Map<String, ByteArray?> = attributes.mapValues { it.value.toByteArray(Charsets.UTF_8) }

    @Test
    fun `a device advertises its family tag, id, name and role`() {
        val attributes = LanRecord.attributes(family, other, "Kitchen", DeviceRole.Display)
        assertEquals(LanAddresses.familyTag(family), attributes["f"])
        assertEquals(other.toString(), attributes["d"])
        assertEquals("Kitchen", attributes["n"])
        assertEquals("DISPLAY", attributes["r"])
    }

    @Test
    fun `a record from the same family is read back with its role`() {
        val record = LanRecord.parse(bytes(LanRecord.attributes(family, other, "Kitchen", DeviceRole.Display)), family, self)
        assertEquals(LanRecord.Parsed(other, "Kitchen", DeviceRole.Display), record)
    }

    @Test
    fun `records from another family, from this device, or without an id are ignored`() {
        val otherFamily = FamilyId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
        assertNull(LanRecord.parse(bytes(LanRecord.attributes(otherFamily, other, "x", DeviceRole.Phone)), family, self))
        assertNull(LanRecord.parse(bytes(LanRecord.attributes(family, self, "x", DeviceRole.Phone)), family, self))
        assertNull(LanRecord.parse(bytes(mapOf("f" to LanAddresses.familyTag(family), "d" to "not-a-uuid")), family, self))
    }

    @Test
    fun `a record without a role, or with one this version does not know, reads as a phone or unrecognised`() {
        val noRole = bytes(mapOf("f" to LanAddresses.familyTag(family), "d" to other.toString(), "n" to "Old"))
        assertEquals(DeviceRole.Phone, LanRecord.parse(noRole, family, self)?.role)
        val future = bytes(mapOf("f" to LanAddresses.familyTag(family), "d" to other.toString(), "r" to "CHILD_PHONE"))
        assertEquals(DeviceRole.Unrecognised("CHILD_PHONE"), LanRecord.parse(future, family, self)?.role)
    }

    @Test
    fun `names are cut to 40 characters`() {
        assertEquals(40, LanRecord.attributes(family, other, "x".repeat(60), DeviceRole.Phone).getValue("n").length)
    }
}
