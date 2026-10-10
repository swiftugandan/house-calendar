package dev.housecalendar.schedule

import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.DeviceUpsert
import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.FamilyCreated
import dev.housecalendar.contracts.FamilySettings
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.Owner
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The family settings (SPEC FR-1, FR-2). `family.created` counts as the first writes. */
public data class FamilyRecord(
    val name: Reg<String>? = null,
    val zone: Reg<ZoneId>? = null,
    val weekStart: Reg<DayOfWeek>? = null,
    val created: Boolean = false,
) {
    internal fun merge(payload: FamilyCreated, stamp: Stamp): FamilyRecord = copy(
        name = name.write(payload.name, stamp),
        zone = zone.write(payload.zone, stamp),
        weekStart = weekStart.write(payload.weekStart, stamp),
        created = true,
    )

    internal fun merge(payload: FamilySettings, stamp: Stamp): FamilyRecord = copy(
        name = name.write(payload.name, stamp),
        zone = zone.write(payload.zone, stamp),
        weekStart = weekStart.write(payload.weekStart, stamp),
    )
}

public data class DeviceRecord(
    val name: Reg<String>? = null,
    val role: Reg<DeviceRole>? = null,
    val owner: Reg<Owner>? = null,
) {
    internal fun merge(payload: DeviceUpsert, stamp: Stamp): DeviceRecord = copy(
        name = name.write(payload.name, stamp),
        role = role.write(payload.role, stamp),
        owner = owner.write(payload.owner, stamp),
    )
}

/** [created] orders members everywhere they are listed: the order they were added (DESIGN.md "Organiser"). */
public data class MemberRecord(
    val created: Stamp,
    val name: Reg<String>? = null,
    val colorIndex: Reg<Int>? = null,
    val kind: Reg<MemberKind>? = null,
    val archived: Reg<Boolean>? = null,
) {
    internal fun merge(payload: MemberUpsert, stamp: Stamp): MemberRecord = copy(
        created = minOf(created, stamp),
        name = name.write(payload.name, stamp),
        colorIndex = colorIndex.write(payload.colorIndex, stamp),
        kind = kind.write(payload.kind, stamp),
        archived = archived.write(payload.archived, stamp),
    )
}

/** An event's own registers (SPEC FR-8). [created] and [changed] are the first and last writes (FR-18). */
public data class EventRecord(
    val created: Stamp,
    val changed: Stamp,
    val title: Reg<String>? = null,
    val icon: Reg<IconKey>? = null,
    val start: Reg<LocalDate>? = null,
    val slot: Reg<Slot>? = null,
    val repeat: Reg<Repeat>? = null,
    val termTimeOnly: Reg<Boolean>? = null,
    val who: Reg<Who>? = null,
    val taking: Reg<Taking>? = null,
    val place: Reg<String>? = null,
    val notes: Reg<String>? = null,
    val reminders: Reg<List<Reminder>>? = null,
    val deleted: Reg<Boolean>? = null,
) {
    internal fun merge(payload: EventUpsert, stamp: Stamp): EventRecord = copy(
        created = minOf(created, stamp),
        changed = maxOf(changed, stamp),
        title = title.write(payload.title, stamp),
        icon = icon.write(payload.icon, stamp),
        start = start.write(payload.start, stamp),
        slot = slot.write(payload.slot, stamp),
        repeat = repeat.write(payload.repeat, stamp),
        termTimeOnly = termTimeOnly.write(payload.termTimeOnly, stamp),
        who = who.write(payload.who, stamp),
        taking = taking.write(payload.taking, stamp),
        place = place.write(payload.place, stamp),
        notes = notes.write(payload.notes, stamp),
        reminders = reminders.write(payload.reminders, stamp),
        deleted = deleted.write(payload.deleted, stamp),
    )
}

/** "This and following" registers for one `(eventId, from)` (SPEC FR-23). */
public data class ChangeRecord(
    val title: Reg<String>? = null,
    val icon: Reg<IconKey>? = null,
    val slot: Reg<Slot>? = null,
    val repeat: Reg<Repeat>? = null,
    val termTimeOnly: Reg<Boolean>? = null,
    val who: Reg<Who>? = null,
    val taking: Reg<Taking>? = null,
    val place: Reg<String>? = null,
    val notes: Reg<String>? = null,
    val reminders: Reg<List<Reminder>>? = null,
    val ended: Reg<Boolean>? = null,
) {
    internal fun merge(payload: EventChange, stamp: Stamp): ChangeRecord = copy(
        title = title.write(payload.title, stamp),
        icon = icon.write(payload.icon, stamp),
        slot = slot.write(payload.slot, stamp),
        repeat = repeat.write(payload.repeat, stamp),
        termTimeOnly = termTimeOnly.write(payload.termTimeOnly, stamp),
        who = who.write(payload.who, stamp),
        taking = taking.write(payload.taking, stamp),
        place = place.write(payload.place, stamp),
        notes = notes.write(payload.notes, stamp),
        reminders = reminders.write(payload.reminders, stamp),
        ended = ended.write(payload.ended, stamp),
    )
}

/** "Only this one" registers for one `(eventId, occurrence)` (SPEC FR-15). */
public data class EditRecord(
    val movedTo: Reg<LocalDate>? = null,
    val slot: Reg<Slot>? = null,
    val title: Reg<String>? = null,
    val icon: Reg<IconKey>? = null,
    val who: Reg<Who>? = null,
    val taking: Reg<Taking>? = null,
    val place: Reg<String>? = null,
    val notes: Reg<String>? = null,
    val cancelled: Reg<Boolean>? = null,
) {
    internal fun merge(payload: EventEdit, stamp: Stamp): EditRecord = copy(
        movedTo = movedTo.write(payload.movedTo, stamp),
        slot = slot.write(payload.slot, stamp),
        title = title.write(payload.title, stamp),
        icon = icon.write(payload.icon, stamp),
        who = who.write(payload.who, stamp),
        taking = taking.write(payload.taking, stamp),
        place = place.write(payload.place, stamp),
        notes = notes.write(payload.notes, stamp),
        cancelled = cancelled.write(payload.cancelled, stamp),
    )
}

public data class HolidayRecord(
    val name: Reg<String>? = null,
    val first: Reg<LocalDate>? = null,
    val last: Reg<LocalDate>? = null,
    val deleted: Reg<Boolean>? = null,
) {
    internal fun merge(payload: HolidayUpsert, stamp: Stamp): HolidayRecord = copy(
        name = name.write(payload.name, stamp),
        first = first.write(payload.first, stamp),
        last = last.write(payload.last, stamp),
        deleted = deleted.write(payload.deleted, stamp),
    )
}
