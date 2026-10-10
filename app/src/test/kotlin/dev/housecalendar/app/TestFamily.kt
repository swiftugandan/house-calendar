package dev.housecalendar.app

import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.FamilyCreated
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.HolidayId
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.IconKey
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
import dev.housecalendar.schedule.CalendarState
import dev.housecalendar.schedule.Projection
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

val LONDON: ZoneId = ZoneId.of("Europe/London")

fun date(text: String): LocalDate = LocalDate.parse(text)

fun time(text: String): LocalTime = LocalTime.parse(text)

fun london(local: String): Instant = LocalDateTime.parse(local).atZone(LONDON).toInstant()

fun timed(start: String, end: String): Slot.Timed = Slot.Timed(time(start), time(end), 0)

/** The spec's family (SPEC "Worked examples"), as ops a phone would have written. */
class TestFamily(seed: Long = 3) {
    private val random = java.util.Random(seed)
    val family = FamilyId(uuid())
    val phone = DeviceId(UUID.fromString("0aaaaaaa-0000-4000-8000-000000000001"))
    private val ops = mutableListOf<Op>()
    private var seq = 0L

    val sam: MemberId
    val alex: MemberId
    val ada: MemberId
    val tom: MemberId

    init {
        add(FamilyCreated("The Okellos", LONDON, DayOfWeek.MONDAY))
        sam = member("Sam", MemberKind.Adult, 0)
        alex = member("Alex", MemberKind.Adult, 1)
        ada = member("Ada", MemberKind.Child, 2)
        tom = member("Tom", MemberKind.Child, 3)
    }

    fun uuid(): UUID = UUID(random.nextLong(), random.nextLong())

    fun ops(): List<Op> = ops.toList()

    fun state(): CalendarState = Projection.fold(ops)

    fun add(payload: Payload): Op {
        seq += 1
        val (type, body) = OpCodec.encodePayload(payload)
        return Op(OpId(Uuids.v7(1_760_000_000_000L + seq)), family, phone, Seq(seq), Lamport(seq), Op.CURRENT_SCHEMA, type, body).also { ops += it }
    }

    fun member(name: String, kind: MemberKind, colorIndex: Int): MemberId =
        MemberId(uuid()).also { add(MemberUpsert(it, name = name, colorIndex = colorIndex, kind = kind, archived = false)) }

    fun event(
        title: String,
        start: String,
        slot: Slot,
        who: Who,
        repeat: Repeat = Repeat.Never,
        taking: Taking = Taking.NotNeeded,
        termTimeOnly: Boolean = false,
        place: String = "",
        icon: IconKey = IconKey.NONE,
    ): EventId = EventId(uuid()).also {
        add(EventUpsert(it, title = title, icon = icon, start = date(start), slot = slot, repeat = repeat, termTimeOnly = termTimeOnly, who = who, taking = taking, place = place, deleted = false))
    }

    fun holiday(name: String, first: String, last: String): HolidayId =
        HolidayId(uuid()).also { add(HolidayUpsert(it, name = name, first = date(first), last = date(last), deleted = false)) }

    fun swimming(): EventId = event(
        "Swimming", "2026-10-06", timed("17:00", "18:00"), Who.Members(setOf(ada)),
        repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY)), taking = Taking.Person(sam), termTimeOnly = true, place = "Leisure Centre",
        icon = IconKey("swim"),
    )
}
