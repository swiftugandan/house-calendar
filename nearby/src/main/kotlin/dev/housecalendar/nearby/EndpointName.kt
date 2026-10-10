package dev.housecalendar.nearby

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import java.util.UUID

/** The Nearby endpoint name, `role|deviceId|name` (SAD §3.7). The role decides who dials (SPEC FR-44). */
public object EndpointName {
    public const val MAX_NAME_CHARS: Int = 40
    private const val SEPARATOR = '|'
    private const val PHONE = "PHONE"
    private const val DISPLAY = "DISPLAY"

    public data class Parsed(val device: DeviceId, val name: String, val role: DeviceRole)

    public fun encode(role: DeviceRole, device: DeviceId, name: String): String =
        "${roleName(role)}$SEPARATOR$device$SEPARATOR${name.take(MAX_NAME_CHARS)}"

    public fun parse(endpointName: String): Parsed? {
        val parts = endpointName.split(SEPARATOR, limit = PARTS)
        if (parts.size != PARTS) return null
        val device = runCatching { DeviceId(UUID.fromString(parts[1])) }.getOrNull() ?: return null
        return Parsed(device, parts[2], roleOf(parts[0]))
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

    private const val PARTS = 3
}
