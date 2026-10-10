package dev.housecalendar.schedule

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.FamilyCreated
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.HolidayId
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.Lamport
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.OpCodec
import dev.housecalendar.contracts.OpId
import dev.housecalendar.contracts.Payload
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Seq
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Uuids
import dev.housecalendar.contracts.Who
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

val LONDON: ZoneId = ZoneId.of("Europe/London")

fun date(text: String): LocalDate = LocalDate.parse(text)

fun time(text: String): LocalTime = LocalTime.parse(text)

fun timed(start: String, end: String, endDayOffset: Int = 0): Slot.Timed = Slot.Timed(time(start), time(end), endDayOffset)

/**
 * Builds op logs the way devices would write them. Each device numbers its own ops; the Lamport clock is a
 * shared counter unless a test sets one explicitly to model devices that have not synced.
 */
class CalendarLog(seed: Long = 1) {
    private val random = java.util.Random(seed)
    val family = FamilyId(uuid())
    val phoneA = DeviceId(UUID.fromString("0aaaaaaa-0000-4000-8000-000000000001"))
    val phoneB = DeviceId(UUID.fromString("0bbbbbbb-0000-4000-8000-000000000002"))
    val display = DeviceId(UUID.fromString("0ddddddd-0000-4000-8000-000000000003"))

    private val ops = mutableListOf<Op>()
    private val seqs = mutableMapOf<DeviceId, Long>()
    private var clock = 0L

    fun uuid(): UUID = UUID(random.nextLong(), random.nextLong())

    fun ops(): List<Op> = ops.toList()

    fun state(): CalendarState = Projection.fold(ops)

    fun add(payload: Payload, device: DeviceId = phoneA, lamport: Long? = null): Op {
        val seq = (seqs[device] ?: 0L) + 1
        seqs[device] = seq
        clock = if (lamport == null) clock + 1 else maxOf(clock, lamport)
        val (type, body) = OpCodec.encodePayload(payload)
        val op = Op(
            OpId(Uuids.v7(1_700_000_000_000L + ops.size, random.nextInt(), random.nextLong())), family, device,
            Seq(seq), Lamport(lamport ?: clock), Op.CURRENT_SCHEMA, type, body,
        )
        ops += op
        return op
    }

    fun raw(type: String, body: String, device: DeviceId = phoneA, schema: Int = Op.CURRENT_SCHEMA): Op {
        val seq = (seqs[device] ?: 0L) + 1
        seqs[device] = seq
        clock += 1
        val op = Op(OpId(Uuids.v7(1_700_000_000_000L + ops.size)), family, device, Seq(seq), Lamport(clock), schema, type, body)
        ops += op
        return op
    }

    fun createFamily(zone: ZoneId = LONDON): CalendarLog = apply { add(FamilyCreated("The Okellos", zone, DayOfWeek.MONDAY)) }

    fun member(name: String, kind: MemberKind, colorIndex: Int, device: DeviceId = phoneA): MemberId {
        val id = MemberId(uuid())
        add(MemberUpsert(id, name = name, colorIndex = colorIndex, kind = kind, archived = false), device)
        return id
    }

    fun event(
        title: String,
        start: LocalDate,
        slot: Slot,
        who: Who,
        repeat: Repeat = Repeat.Never,
        taking: Taking = Taking.NotNeeded,
        termTimeOnly: Boolean = false,
        device: DeviceId = phoneA,
    ): EventId {
        val id = EventId(uuid())
        add(EventUpsert(id, title = title, start = start, slot = slot, repeat = repeat, termTimeOnly = termTimeOnly, who = who, taking = taking), device)
        return id
    }

    fun holiday(name: String, first: LocalDate, last: LocalDate, device: DeviceId = phoneA): HolidayId {
        val id = HolidayId(uuid())
        add(HolidayUpsert(id, name = name, first = first, last = last, deleted = false), device)
        return id
    }

    fun change(payload: EventChange, device: DeviceId = phoneA, lamport: Long? = null): Op = add(payload, device, lamport)

    fun edit(payload: EventEdit, device: DeviceId = phoneA, lamport: Long? = null): Op = add(payload, device, lamport)

    fun upsert(payload: EventUpsert, device: DeviceId = phoneA, lamport: Long? = null): Op = add(payload, device, lamport)
}

/**
 * The spec's common setup (SPEC "Worked examples"): Europe/London; Sam and Alex (adults), Ada and Tom
 * (children); Half term 26 to 30 Oct 2026; Swimming on Tuesdays from 6 Oct, 17:00 to 18:00, term time only,
 * for Ada, Sam taking.
 */
class SpecFamily(withHalfTerm: Boolean = true) {
    val log = CalendarLog().createFamily()
    val sam = log.member("Sam", MemberKind.Adult, 0)
    val alex = log.member("Alex", MemberKind.Adult, 1)
    val ada = log.member("Ada", MemberKind.Child, 2)
    val tom = log.member("Tom", MemberKind.Child, 3)
    val halfTerm: HolidayId? = if (withHalfTerm) log.holiday("Half term", date("2026-10-26"), date("2026-10-30")) else null
    val swimming = log.event(
        "Swimming", date("2026-10-06"), timed("17:00", "18:00"), Who.Members(setOf(ada)),
        repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY)), taking = Taking.Person(sam), termTimeOnly = true,
    )

    fun agenda(from: String, to: String): List<AgendaEntry> = Agenda.resolve(log.state(), DateWindow(date(from), date(to)))

    fun swims(from: String, to: String): List<AgendaEntry> = agenda(from, to).filter { it.event == swimming }
}
