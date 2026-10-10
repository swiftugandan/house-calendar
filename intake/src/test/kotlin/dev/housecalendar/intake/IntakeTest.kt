package dev.housecalendar.intake

import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** SPEC FR-49 to FR-56 and Examples 13 and 14: untrusted input from other apps becomes drafts. */
class IntakeTest {
    private val london = ZoneId.of("Europe/London")

    private fun ics(vararg lines: String): ByteArray = (listOf("BEGIN:VCALENDAR", "VERSION:2.0") + lines + "END:VCALENDAR").joinToString("\r\n").toByteArray()

    private fun drafts(bytes: ByteArray): IntakeResult.Drafts = IcsReader.read(bytes, london) as IntakeResult.Drafts

    @Test
    fun `example 13 - a school file of all-day items, with exclusive DTEND`() {
        val result = drafts(
            ics(
                "BEGIN:VEVENT", "UID:inset-1@school", "DTSTART;VALUE=DATE:20261102", "SUMMARY:INSET day", "END:VEVENT",
                "BEGIN:VEVENT", "UID:hols-1@school", "DTSTART;VALUE=DATE:20261221", "DTEND;VALUE=DATE:20270104", "SUMMARY:Christmas holidays", "END:VEVENT",
            ),
        )
        assertEquals(listOf("INSET day", "Christmas holidays"), result.drafts.map { it.title })
        assertEquals(LocalDate.of(2026, 11, 2), result.drafts[0].day)
        assertEquals(Slot.AllDay(1), result.drafts[0].slot)
        assertEquals(Slot.AllDay(14), result.drafts[1].slot)
        assertEquals(LocalDate.of(2027, 1, 3), result.drafts[1].lastDay)
        assertEquals("inset-1@school", result.drafts[0].uid)
        assertTrue(result.drafts.all { it.allDay })
    }

    @Test
    fun `example 14 - an invite in another timezone becomes family time`() {
        val draft = drafts(
            ics("BEGIN:VEVENT", "DTSTART;TZID=America/New_York:20261103T120000", "DTEND;TZID=America/New_York:20261103T130000", "SUMMARY:Call with Grandpa", "END:VEVENT"),
        ).drafts.single()
        assertEquals(LocalDate.of(2026, 11, 3), draft.day)
        assertEquals(Slot.Timed(LocalTime.of(17, 0), LocalTime.of(18, 0), 0), draft.slot)
    }

    @Test
    fun `UTC and floating times`() {
        val utc = drafts(ics("BEGIN:VEVENT", "DTSTART:20260706T090000Z", "DURATION:PT1H30M", "SUMMARY:Flight", "END:VEVENT")).drafts.single()
        assertEquals(Slot.Timed(LocalTime.of(10, 0), LocalTime.of(11, 30), 0), utc.slot)
        val floating = drafts(ics("BEGIN:VEVENT", "DTSTART:20261010T193000", "SUMMARY:Film", "END:VEVENT")).drafts.single()
        assertEquals(Slot.Timed(LocalTime.of(19, 30), LocalTime.of(20, 30), 0), floating.slot)
    }

    @Test
    fun `an unknown TZID is read as floating time`() {
        val draft = drafts(ics("BEGIN:VEVENT", "DTSTART;TZID=GMT Standard Time:20261010T090000", "DTEND;TZID=GMT Standard Time:20261010T100000", "SUMMARY:Meeting", "END:VEVENT")).drafts.single()
        assertEquals(Slot.Timed(LocalTime.of(9, 0), LocalTime.of(10, 0), 0), draft.slot)
    }

    @Test
    fun `lines are unfolded and text is unescaped (RFC 5545 3_1 and 3_3_11)`() {
        val draft = drafts(
            ics("BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261010", "SUMMARY:Bake sale\\, cakes", "DESCRIPTION:Line one\\nLine two \\; and", "  a folded end", "LOCATION:Hall\\\\B", "END:VEVENT"),
        ).drafts.single()
        assertEquals("Bake sale, cakes", draft.title)
        assertEquals("Line one\nLine two ; and a folded end", draft.notes)
        assertEquals("Hall\\B", draft.place)
    }

    @Test
    fun `repeat rules that map are kept, COUNT is handed over for conversion`() {
        val weekly = drafts(ics("BEGIN:VEVENT", "DTSTART:20261006T170000", "RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261215T235959Z", "SUMMARY:Swim", "END:VEVENT")).drafts.single()
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), until = LocalDate.of(2026, 12, 15)), weekly.repeat)
        val counted = drafts(ics("BEGIN:VEVENT", "DTSTART:20261006T170000", "RRULE:FREQ=WEEKLY;COUNT=10", "SUMMARY:Swim", "END:VEVENT")).drafts.single()
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.TUESDAY)), counted.repeat)
        assertEquals(10, counted.count)
        val lastFriday = drafts(ics("BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261030", "RRULE:FREQ=MONTHLY;BYDAY=-1FR", "SUMMARY:Book club", "END:VEVENT")).drafts.single()
        assertEquals(Repeat.MonthlyOnWeekday(1, -1, DayOfWeek.FRIDAY), lastFriday.repeat)
        val fortnightly = drafts(ics("BEGIN:VEVENT", "DTSTART:20261006T170000", "RRULE:FREQ=WEEKLY;INTERVAL=2", "SUMMARY:Club", "END:VEVENT")).drafts.single()
        assertEquals(Repeat.Weekly(2, setOf(DayOfWeek.TUESDAY)), fortnightly.repeat)
        val yearly = drafts(ics("BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261010", "RRULE:FREQ=YEARLY", "SUMMARY:Birthday", "END:VEVENT")).drafts.single()
        assertEquals(Repeat.Yearly(1), yearly.repeat)
        val monthly = drafts(ics("BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261013", "RRULE:FREQ=MONTHLY;BYMONTHDAY=13", "SUMMARY:Bins", "END:VEVENT")).drafts.single()
        assertEquals(Repeat.MonthlyOnDay(1, 13), monthly.repeat)
    }

    @Test
    fun `a rule that does not map becomes a one-off with the rule kept for a note (SPEC FR-52)`() {
        val draft = drafts(ics("BEGIN:VEVENT", "DTSTART:20261006T170000", "RRULE:FREQ=MONTHLY;BYDAY=TU,TH;BYSETPOS=2", "SUMMARY:Odd", "END:VEVENT")).drafts.single()
        assertEquals(Repeat.Never, draft.repeat)
        assertEquals("FREQ=MONTHLY;BYDAY=TU,TH;BYSETPOS=2", draft.unmappableRule)
    }

    @Test
    fun `EXDATE becomes skipped dates and RECURRENCE-ID overrides are counted, not imported`() {
        val result = drafts(
            ics(
                "BEGIN:VEVENT", "UID:a", "DTSTART:20261006T170000", "RRULE:FREQ=WEEKLY", "EXDATE:20261013T170000,20261020T170000", "SUMMARY:Swim", "END:VEVENT",
                "BEGIN:VEVENT", "UID:a", "RECURRENCE-ID:20261027T170000", "DTSTART:20261027T180000", "SUMMARY:Swim (late)", "END:VEVENT",
            ),
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 20)), result.drafts.single().exdates)
        assertEquals(1, result.overridesSkipped)
    }

    @Test
    fun `fields are cut to the spec's limits`() {
        val draft = drafts(ics("BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261010", "SUMMARY:" + "x".repeat(200), "LOCATION:" + "y".repeat(300), "END:VEVENT")).drafts.single()
        assertEquals(80, draft.title.length)
        assertEquals(120, draft.place.length)
    }

    @Test
    fun `size and count bounds refuse the file (SPEC FR-56)`() {
        assertEquals(IntakeResult.Refused(Refusal.TOO_LARGE), IcsReader.read(ByteArray(IcsReader.MAX_BYTES + 1) { 'A'.code.toByte() }, london))
        val many = (1..501).flatMap { listOf("BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261010", "SUMMARY:E$it", "END:VEVENT") }
        assertEquals(IntakeResult.Refused(Refusal.TOO_MANY_EVENTS), IcsReader.read(ics(*many.toTypedArray()), london))
        assertEquals(IntakeResult.Refused(Refusal.NOT_A_CALENDAR), IcsReader.read("hello".toByteArray(), london))
    }

    @Test
    fun `events it cannot read are skipped and counted`() {
        val result = drafts(
            ics(
                "BEGIN:VEVENT", "SUMMARY:No start", "END:VEVENT",
                "BEGIN:VEVENT", "DTSTART:2026BAD", "SUMMARY:Bad start", "END:VEVENT",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20260230", "SUMMARY:No such day", "END:VEVENT",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261010", "SUMMARY:Fine", "END:VEVENT",
            ),
        )
        assertEquals(listOf("Fine"), result.drafts.map { it.title })
        assertEquals(3, result.skipped)
    }

    @Test
    fun `every prefix of a real file parses without throwing`() {
        val file = ics(
            "BEGIN:VEVENT", "UID:x@y", "DTSTART;TZID=Europe/London:20261006T170000", "DTEND;TZID=Europe/London:20261006T180000",
            "RRULE:FREQ=WEEKLY;BYDAY=TU;COUNT=10", "EXDATE;TZID=Europe/London:20261027T170000", "SUMMARY:Swimming\\, lessons",
            "LOCATION:Leisure Centre", "DESCRIPTION:Goggles", "BEGIN:VALARM", "TRIGGER:-PT30M", "END:VALARM", "END:VEVENT",
        )
        for (length in 0..file.size) IcsReader.read(file.copyOf(length), london)
    }

    @Test
    fun `an insert intent's extras become a draft (SPEC FR-49, FR-53)`() {
        val timed = InsertIntent.draft(
            InsertExtras(title = "Dentist", beginMillis = 1_791_651_600_000, endMillis = 1_791_654_000_000, allDay = false, location = "Smile Dental", description = "Bring card", rrule = null),
            london,
        )
        assertEquals("Dentist", timed?.title)
        assertEquals(LocalDate.of(2026, 10, 10), timed?.day)
        assertEquals(Slot.Timed(LocalTime.of(18, 0), LocalTime.of(18, 40), 0), timed?.slot)
        assertEquals("Smile Dental", timed?.place)
        val allDay = InsertIntent.draft(InsertExtras("Trip", 1_791_590_400_000, 1_791_763_200_000, true, null, null, "FREQ=YEARLY"), london)
        assertEquals(LocalDate.of(2026, 10, 10), allDay?.day)
        assertEquals(Slot.AllDay(2), allDay?.slot)
        assertEquals(Repeat.Yearly(1), allDay?.repeat)
        assertNull(InsertIntent.draft(InsertExtras(null, null, null, false, null, null, null), london)?.day)
    }

    @Test
    fun `shared text gives a title, notes and, when it says so, a day and time (SPEC FR-51)`() {
        val now = LocalDateTime.of(2026, 10, 10, 12, 0) // a Saturday
        val party = SharedText.draft("Party at Mia's, Sat 3pm\nBring a present", now)
        assertEquals("Party at Mia's, Sat 3pm", party.title)
        assertEquals("Party at Mia's, Sat 3pm\nBring a present", party.notes)
        assertEquals(LocalDate.of(2026, 10, 10), party.day)
        assertEquals(Slot.Timed(LocalTime.of(15, 0), LocalTime.of(16, 0), 0), party.slot)
        val plain = SharedText.draft("Remember the forms", now)
        assertNull(plain.day)
        assertNull(plain.slot)
    }

    @Test
    fun `the date and time hints table`() {
        val now = LocalDateTime.of(2026, 10, 10, 12, 0) // Saturday 10 October
        val cases = mapOf(
            "tomorrow at 10:30" to (LocalDate.of(2026, 10, 11) to LocalTime.of(10, 30)),
            "today 18:00" to (LocalDate.of(2026, 10, 10) to LocalTime.of(18, 0)),
            "tonight" to (LocalDate.of(2026, 10, 10) to null),
            "Tuesday" to (LocalDate.of(2026, 10, 13) to null),
            "on Sat at 9am" to (LocalDate.of(2026, 10, 10) to LocalTime.of(9, 0)),
            "12 Oct 2pm" to (LocalDate.of(2026, 10, 12) to LocalTime.of(14, 0)),
            "Oct 20" to (LocalDate.of(2026, 10, 20) to null),
            "3 March" to (LocalDate.of(2027, 3, 3) to null),
            "at 7.30pm" to (null to LocalTime.of(19, 30)),
            "12pm" to (null to LocalTime.of(12, 0)),
            "12am" to (null to LocalTime.of(0, 0)),
        )
        cases.forEach { (text, expected) ->
            val hint = DateTimeHints.find(text, now)
            assertEquals(text, expected, hint.date to hint.time)
        }
        val nothing = DateTimeHints.find("see you there 25 people", now)
        assertEquals(null to null, nothing.date to nothing.time)
    }
}
