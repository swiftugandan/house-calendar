package dev.housecalendar.sync

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.Uuids

/**
 * Who opens the connection between two devices (SPEC FR-44, SAD ADR-9). The display is always listening, so
 * a phone dials it and it never dials a phone; that lets a phone in the background bring the kitchen up to
 * date. Between two devices of the same role the lower id dials, as in House Points, so two devices never
 * open sessions to each other at the same moment. A role this version does not know counts as a phone.
 */
public object Dialing {
    public fun shouldDial(self: DeviceId, selfRole: DeviceRole, peer: DeviceId, peerRole: DeviceRole): Boolean {
        val selfDisplay = selfRole == DeviceRole.Display
        val peerDisplay = peerRole == DeviceRole.Display
        return when {
            selfDisplay != peerDisplay -> peerDisplay
            else -> Uuids.compare(self.uuid, peer.uuid) < 0
        }
    }
}
