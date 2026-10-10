package dev.housecalendar.lan

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.FamilyId
import java.util.UUID

/**
 * The TXT attributes a device advertises with its `_housecal._tcp` service (SAD §3.6): the family tag (a
 * hash, never the id), its device id, its name and its role. The role decides who dials (SPEC FR-44).
 */
public object LanRecord {
    public const val FAMILY: String = "f"
    public const val DEVICE: String = "d"
    public const val NAME: String = "n"
    public const val ROLE: String = "r"
    public const val MAX_NAME_CHARS: Int = 40

    private const val PHONE = "PHONE"
    private const val DISPLAY = "DISPLAY"

    public data class Parsed(val device: DeviceId, val name: String, val role: DeviceRole)

    public fun attributes(family: FamilyId, device: DeviceId, name: String, role: DeviceRole): Map<String, String> = mapOf(
        FAMILY to LanAddresses.familyTag(family),
        DEVICE to device.toString(),
        NAME to name.take(MAX_NAME_CHARS),
        ROLE to roleName(role),
    )

    /** A peer of [family] other than [self], or null for anything else. A record without a role is a phone. */
    public fun parse(attributes: Map<String, ByteArray?>, family: FamilyId, self: DeviceId): Parsed? {
        fun text(key: String): String? = attributes[key]?.toString(Charsets.UTF_8)
        if (text(FAMILY) != LanAddresses.familyTag(family)) return null
        val device = text(DEVICE)?.let { raw -> runCatching { DeviceId(UUID.fromString(raw)) }.getOrNull() } ?: return null
        if (device == self) return null
        return Parsed(device, text(NAME).orEmpty(), text(ROLE)?.let(::roleOf) ?: DeviceRole.Phone)
    }

    private fun roleName(role: DeviceRole): String = when (role) {
        DeviceRole.Phone -> PHONE
        DeviceRole.Display -> DISPLAY
        is DeviceRole.Unrecognised -> role.raw
    }

    private fun roleOf(name: String): DeviceRole = when (name) {
        PHONE -> DeviceRole.Phone
        DISPLAY -> DeviceRole.Display
        else -> DeviceRole.Unrecognised(name)
    }
}
