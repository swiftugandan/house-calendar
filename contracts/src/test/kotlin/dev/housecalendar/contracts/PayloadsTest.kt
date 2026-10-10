package dev.housecalendar.contracts

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class PayloadsTest {
    private val family = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
    private val device = DeviceId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
    private val ada = MemberId(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"))
    private val sam = MemberId(UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"))
    private val swim = EventId(UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"))
    private val halfTerm = HolidayId(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"))
    private val oct6 = LocalDate.of(2026, 10, 6)

    private fun opOf(payload: Payload, seq: Long = 1): Op {
        val (type, body) = OpCodec.encodePayload(payload)
        return Op(OpId(Uuids.v7(seq)), family, device, Seq(seq), Lamport(seq), Op.CURRENT_SCHEMA, type, body)
    }

    private fun bodyOf(payload: Payload): String = OpCodec.encodePayload(payload).second

    private val samples: List<Payload> = listOf(
        FamilyCreated("The Okellos", ZoneId.of("Europe/London"), DayOfWeek.MONDAY),
        FamilySettings(name = "Okello family"),
        FamilySettings(zone = ZoneId.of("Africa/Kampala"), weekStart = DayOfWeek.SUNDAY),
        DeviceUpsert(device, name = "Kitchen", role = DeviceRole.Display, owner = Owner.NoOne),
        DeviceUpsert(device, role = DeviceRole.Phone, owner = Owner.Member(sam)),
        DeviceRemoved(device),
        MemberUpsert(ada, name = "Ada", colorIndex = 2, kind = MemberKind.Child, archived = false),
        EventUpsert(
            swim, title = "Swimming", icon = IconKey("swim"), start = oct6,
            slot = Slot.Timed(LocalTime.of(17, 0), LocalTime.of(18, 0), 0),
            repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY), until = LocalDate.of(2026, 12, 15)),
            termTimeOnly = true, who = Who.Members(setOf(ada)), taking = Taking.Person(sam),
            place = "Leisure Centre", notes = "Bring goggles",
            reminders = listOf(Reminder.MinutesBefore(30), Reminder.DaysBeforeAt(1, LocalTime.of(19, 0))),
            deleted = false,
        ),
        EventUpsert(swim, slot = Slot.AllDay(3), repeat = Repeat.Never, who = Who.Everyone, taking = Taking.NeedsSomeone),
        EventUpsert(swim, repeat = Repeat.Daily(2, until = null), taking = Taking.NotNeeded),
        EventUpsert(swim, repeat = Repeat.MonthlyOnDay(1, 31, until = null)),
        EventUpsert(swim, repeat = Repeat.MonthlyOnWeekday(1, -1, DayOfWeek.FRIDAY, until = null)),
        EventUpsert(swim, repeat = Repeat.Yearly(1, until = null)),
        EventUpsert(swim, reminders = emptyList(), place = "", notes = "", icon = IconKey.NONE),
        EventChange(swim, from = LocalDate.of(2026, 10, 20), slot = Slot.Timed(LocalTime.of(17, 30), LocalTime.of(18, 30), 0)),
        EventChange(swim, from = LocalDate.of(2026, 11, 10), who = Who.Members(setOf(ada, sam)), ended = false, termTimeOnly = false),
        EventEdit(swim, occurrence = LocalDate.of(2026, 10, 20), movedTo = LocalDate.of(2026, 10, 21), cancelled = false),
        EventEdit(swim, occurrence = LocalDate.of(2026, 10, 27), cancelled = true),
        HolidayUpsert(halfTerm, name = "Half term", first = LocalDate.of(2026, 10, 26), last = LocalDate.of(2026, 10, 30), deleted = false),
    )

    @Test
    fun `every payload survives encode and decode`() {
        samples.forEach { payload -> assertEquals(payload, OpCodec.decodePayload(opOf(payload))) }
    }

    @Test
    fun `every payload op survives the envelope`() {
        samples.forEachIndexed { index, payload ->
            val op = opOf(payload, seq = index + 1L)
            assertEquals(DecodeResult.Decoded(op), OpCodec.decode(OpCodec.encode(op)))
        }
    }

    @Test
    fun `payload type strings are the frozen ones`() {
        val expected = listOf(
            "family.created", "family.settings", "family.settings", "device.upsert", "device.upsert", "device.removed",
            "member.upsert", "event.upsert", "event.upsert", "event.upsert", "event.upsert", "event.upsert", "event.upsert",
            "event.upsert", "event.change", "event.change", "event.edit", "event.edit", "holiday.upsert",
        )
        assertEquals(expected, samples.map { OpCodec.encodePayload(it).first })
    }

    @Test
    fun `an event upsert has the frozen JSON shape`() {
        val body = bodyOf(
            EventUpsert(
                swim, title = "Swimming", start = oct6,
                slot = Slot.Timed(LocalTime.of(17, 0), LocalTime.of(18, 0), 0),
                repeat = Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY), until = null),
                termTimeOnly = true, who = Who.Members(setOf(ada)), taking = Taking.Person(sam),
                reminders = listOf(Reminder.MinutesBefore(30)),
            ),
        )
        val expected = """{"eventId":"$swim","title":"Swimming","start":"2026-10-06",""" +
            """"slot":{"type":"timed","start":"17:00","end":"18:00","endDayOffset":0},""" +
            """"repeat":{"type":"weekly","interval":1,"days":["TUESDAY"]},"termTimeOnly":true,""" +
            """"who":{"type":"members","members":["$ada"]},"taking":{"type":"person","member":"$sam"},""" +
            """"reminders":[{"type":"minutesBefore","minutes":30}]}"""
        assertEquals(expected, body)
    }

    @Test
    fun `objects in tagged unions carry only their tag`() {
        assertEquals("""{"eventId":"$swim","repeat":{"type":"never"},"who":{"type":"everyone"},"taking":{"type":"needsSomeone"}}""",
            bodyOf(EventUpsert(swim, repeat = Repeat.Never, who = Who.Everyone, taking = Taking.NeedsSomeone)))
        assertEquals("""{"deviceId":"$device","owner":{"type":"none"}}""", bodyOf(DeviceUpsert(device, owner = Owner.NoOne)))
        assertEquals("""{"eventId":"$swim","taking":{"type":"notNeeded"}}""", bodyOf(EventUpsert(swim, taking = Taking.NotNeeded)))
    }

    @Test
    fun `an occurrence edit is keyed by the original date and moves with movedTo`() {
        assertEquals("""{"eventId":"$swim","occurrence":"2026-10-20","movedTo":"2026-10-22","slot":{"type":"allDay","days":1}}""",
            bodyOf(EventEdit(swim, LocalDate.of(2026, 10, 20), movedTo = LocalDate.of(2026, 10, 22), slot = Slot.AllDay(1))))
    }

    @Test
    fun `a change from is keyed by its from date`() {
        assertEquals("""{"eventId":"$swim","from":"2026-11-10","ended":true}""",
            bodyOf(EventChange(swim, from = LocalDate.of(2026, 11, 10), ended = true)))
    }

    @Test
    fun `upserts omit the fields they do not write`() {
        assertEquals("""{"eventId":"$swim","title":"Swim club"}""", bodyOf(EventUpsert(swim, title = "Swim club")))
        assertEquals("""{"memberId":"$ada","archived":true}""", bodyOf(MemberUpsert(ada, archived = true)))
    }

    @Test
    fun `clearing a text field writes the empty string, which is distinct from not writing it`() {
        assertEquals("""{"eventId":"$swim","icon":"","place":"","notes":""}""",
            bodyOf(EventUpsert(swim, icon = IconKey.NONE, place = "", notes = "")))
        val decoded = OpCodec.decodePayload(opOf(EventUpsert(swim, place = ""))) as EventUpsert
        assertEquals("", decoded.place)
        assertEquals(null, decoded.notes)
    }

    @Test
    fun `times are written to the minute`() {
        val body = bodyOf(EventUpsert(swim, slot = Slot.Timed(LocalTime.of(9, 5, 30), LocalTime.of(10, 0), 1)))
        assertEquals("""{"eventId":"$swim","slot":{"type":"timed","start":"09:05","end":"10:00","endDayOffset":1}}""", body)
    }

    @Test
    fun `an unknown variant of a union field is kept as unrecognised, and the rest of the payload still reads`() {
        val body = """{"eventId":"$swim","title":"Swimming","repeat":{"type":"hourly","interval":3},"taking":{"type":"carpool","group":"x"}}"""
        val decoded = OpCodec.decodePayload(opOf(EventUpsert(swim)).copy(body = body))
        val expected = EventUpsert(
            swim, title = "Swimming",
            repeat = Repeat.Unrecognised(buildJsonObject { put("type", "hourly"); put("interval", 3) }),
            taking = Taking.Unrecognised(buildJsonObject { put("type", "carpool"); put("group", "x") }),
        )
        assertEquals(expected, decoded)
    }

    @Test
    fun `an unrecognised variant re-encodes to the JSON it came from`() {
        val raw = buildJsonObject { put("type", "fortnightlyByTerm"); put("x", JsonPrimitive(1)) }
        assertEquals("""{"eventId":"$swim","repeat":{"type":"fortnightlyByTerm","x":1}}""",
            bodyOf(EventUpsert(swim, repeat = Repeat.Unrecognised(raw))))
    }

    @Test
    fun `unknown enum values read as unrecognised instead of breaking the payload`() {
        val body = """{"deviceId":"$device","name":"Ada's phone","role":"CHILD_PHONE"}"""
        val decoded = OpCodec.decodePayload(opOf(DeviceRemoved(device)).copy(type = "device.upsert", body = body))
        assertEquals(DeviceUpsert(device, name = "Ada's phone", role = DeviceRole.Unrecognised("CHILD_PHONE")), decoded)
        val member = """{"memberId":"$ada","kind":"PET"}"""
        assertEquals(MemberUpsert(ada, kind = MemberKind.Unrecognised("PET")),
            OpCodec.decodePayload(opOf(DeviceRemoved(device)).copy(type = "member.upsert", body = member)))
    }

    @Test
    fun `fields added by a newer version of a known type are ignored on read`() {
        val op = opOf(HolidayUpsert(halfTerm, name = "Half term")).let { it.copy(body = it.body.dropLast(1) + ""","source":"school"}""") }
        assertEquals(HolidayUpsert(halfTerm, name = "Half term"), OpCodec.decodePayload(op))
    }

    @Test
    fun `a broken body of a known type decodes to MalformedPayload instead of throwing`() {
        val broken = listOf(
            """{"eventId":"not-a-uuid"}""",
            """{"eventId":"$swim","start":"2026-02-30"}""",
            """{"eventId":"$swim","slot":{"type":"timed","start":"25:00","end":"26:00","endDayOffset":0}}""",
            """{"eventId":"$swim","slot":{"type":"timed","start":"17:00"}}""",
            """{"eventId":"$swim","slot":"timed"}""",
            """{"eventId":"$swim","repeat":{"interval":1}}""",
            """[]""",
        )
        broken.forEach { body ->
            val decoded = OpCodec.decodePayload(opOf(EventUpsert(swim)).copy(body = body))
            assertTrue("$body should be malformed but was $decoded", decoded is MalformedPayload)
        }
    }

    @Test
    fun `future payload types are unknown, never malformed`() {
        listOf("task.upsert", "meal.set", "list.item").forEach { type ->
            val op = opOf(EventUpsert(swim)).copy(type = type, body = """{"anything":true}""")
            assertEquals(UnknownPayload(type, """{"anything":true}"""), OpCodec.decodePayload(op))
        }
    }
}
