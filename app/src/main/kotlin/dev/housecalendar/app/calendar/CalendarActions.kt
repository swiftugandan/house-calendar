package dev.housecalendar.app.calendar

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.DeviceUpsert
import dev.housecalendar.contracts.FamilyCreated
import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.Owner
import dev.housecalendar.contracts.Payload
import dev.housecalendar.schedule.CalendarState
import java.time.DayOfWeek
import java.time.ZoneId

/** A member named during setup, before any of them is recorded. */
data class NewMember(val id: MemberId, val name: String, val kind: MemberKind)

/** The payloads for family and member actions, so the screens never build payloads themselves. */
object CalendarActions {
    /** SPEC FR-1, FR-5: the family, its first members in order with colours 0, 1, 2 …, and this device. */
    fun createFamily(
        name: String,
        zone: ZoneId,
        weekStart: DayOfWeek,
        members: List<NewMember>,
        device: DeviceId,
        deviceName: String,
        role: DeviceRole,
        owner: MemberId?,
    ): List<Payload> = buildList {
        add(FamilyCreated(name.trim(), zone, weekStart))
        members.forEachIndexed { index, member ->
            add(MemberUpsert(member.id, member.name.trim(), index % Limits.MEMBER_COLOURS, member.kind, archived = false))
        }
        add(nameDevice(device, deviceName, role, owner))
    }

    /** SPEC FR-7: the next colour in order after every member ever added, archived ones included. */
    fun addMember(state: CalendarState, id: MemberId, name: String, kind: MemberKind): MemberUpsert =
        MemberUpsert(id, name.trim(), state.allMembers().size % Limits.MEMBER_COLOURS, kind, archived = false)

    /** SPEC FR-5: a device's name, role and owner. A display has no owner. */
    fun nameDevice(device: DeviceId, name: String, role: DeviceRole, owner: MemberId?): DeviceUpsert =
        DeviceUpsert(device, name.trim(), role, if (role == DeviceRole.Display || owner == null) Owner.NoOne else Owner.Member(owner))
}
