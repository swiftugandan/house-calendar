package dev.housecalendar.schedule

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRemoved
import dev.housecalendar.contracts.DeviceUpsert
import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.FamilyCreated
import dev.housecalendar.contracts.FamilySettings
import dev.housecalendar.contracts.HolidayId
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.MalformedPayload
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.OpCodec
import dev.housecalendar.contracts.UnknownPayload
import java.time.LocalDate
import java.util.SortedMap
import java.util.TreeMap

/**
 * Ops in, calendar state out (SAD §3.2). Every record field is a register merged by `max(stamp)`, so the
 * result depends only on the op *set* (SPEC NFR-DET-1) and a batch can be applied to an earlier state
 * (SAD ADR-12). Unknown and unreadable payloads only increment diagnostics (SPEC FR-46, NFR-EXT-1).
 */
public object Projection {
    public fun fold(ops: List<Op>): CalendarState = apply(CalendarState.EMPTY, ops)

    internal fun apply(state: CalendarState, ops: List<Op>): CalendarState {
        if (ops.isEmpty()) return state
        val builder = Builder(state)
        ops.forEach(builder::apply)
        return builder.build()
    }

    /** Mutable copies of the state's maps; inner maps are copied the first time a batch writes to them. */
    private class Builder(state: CalendarState) {
        private var family = state.family
        private val members = HashMap<MemberId, MemberRecord>(state.members)
        private val devices = HashMap<DeviceId, DeviceRecord>(state.devices)
        private val events = HashMap<EventId, EventRecord>(state.events)
        private val changes = HashMap<EventId, SortedMap<LocalDate, ChangeRecord>>(state.changes)
        private val edits = HashMap<EventId, Map<LocalDate, EditRecord>>(state.edits)
        private val holidays = HashMap<HolidayId, HolidayRecord>(state.holidays)
        private val removedDevices = HashSet<DeviceId>(state.removedDevices)
        private val unknown = HashMap<String, Int>(state.diagnostics.unknownByType)
        private var newestSchema = state.diagnostics.newestSchema
        private var malformed = state.diagnostics.malformed
        private val freshChanges = HashMap<EventId, TreeMap<LocalDate, ChangeRecord>>()
        private val freshEdits = HashMap<EventId, HashMap<LocalDate, EditRecord>>()

        fun apply(op: Op) {
            val stamp = Stamp.of(op)
            newestSchema = maxOf(newestSchema, op.schemaVersion)
            when (val payload = OpCodec.decodePayload(op)) {
                is FamilyCreated -> family = family.merge(payload, stamp)
                is FamilySettings -> family = family.merge(payload, stamp)
                is DeviceUpsert -> devices[payload.deviceId] = (devices[payload.deviceId] ?: DeviceRecord()).merge(payload, stamp)
                is DeviceRemoved -> removedDevices += payload.deviceId
                is MemberUpsert -> members[payload.memberId] = (members[payload.memberId] ?: MemberRecord(stamp)).merge(payload, stamp)
                is EventUpsert -> events[payload.eventId] = (events[payload.eventId] ?: EventRecord(stamp, stamp)).merge(payload, stamp)
                is EventChange -> changesOf(payload.eventId).let { it[payload.from] = (it[payload.from] ?: ChangeRecord()).merge(payload, stamp) }
                is EventEdit -> editsOf(payload.eventId).let { it[payload.occurrence] = (it[payload.occurrence] ?: EditRecord()).merge(payload, stamp) }
                is HolidayUpsert -> holidays[payload.holidayId] = (holidays[payload.holidayId] ?: HolidayRecord()).merge(payload, stamp)
                is UnknownPayload -> unknown.merge(payload.type, 1, Int::plus)
                is MalformedPayload -> malformed += 1
            }
        }

        fun build(): CalendarState = CalendarState(
            family, members.toMap(), devices.toMap(), events.toMap(), changes.toMap(), edits.toMap(), holidays.toMap(),
            removedDevices.toSet(), Diagnostics(unknown.toMap(), newestSchema, malformed),
        )

        private fun changesOf(event: EventId): TreeMap<LocalDate, ChangeRecord> = freshChanges.getOrPut(event) {
            TreeMap<LocalDate, ChangeRecord>(changes[event] ?: emptyMap()).also { changes[event] = it }
        }

        private fun editsOf(event: EventId): HashMap<LocalDate, EditRecord> = freshEdits.getOrPut(event) {
            HashMap(edits[event] ?: emptyMap()).also { edits[event] = it }
        }
    }
}
