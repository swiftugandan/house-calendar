package dev.housecalendar.schedule

import dev.housecalendar.contracts.IconKey
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.util.UUID

/** SPEC FR-19: the checks on the recording device. */
class RecordingTest {
    private val family = SpecFamily()

    private fun draft(
        title: String = "Dentist",
        start: String = "2026-10-15",
        slot: Slot = timed("16:20", "17:00"),
        repeat: Repeat = Repeat.Never,
        who: Who = Who.Members(setOf(family.ada)),
        taking: Taking = Taking.Person(family.sam),
        place: String = "",
        notes: String = "",
        reminders: List<Reminder> = listOf(Reminder.MinutesBefore(30)),
    ) = EventDraft(title, IconKey.NONE, date(start), slot, repeat, termTimeOnly = false, who, taking, place, notes, reminders)

    private fun problems(draft: EventDraft): Set<Problem> = when (val verdict = Recording.check(family.log.state(), draft)) {
        Verdict.Ok -> emptySet()
        is Verdict.Problems -> verdict.problems
    }

    @Test
    fun `a well-formed event passes`() {
        assertEquals(emptySet<Problem>(), problems(draft()))
        assertEquals(emptySet<Problem>(), problems(draft(slot = Slot.AllDay(31), who = Who.Everyone, taking = Taking.NeedsSomeone)))
        assertEquals(emptySet<Problem>(), problems(draft(start = "2026-10-06", repeat = Repeat.Weekly(2, setOf(TUESDAY)))))
    }

    @Test
    fun `text limits`() {
        assertEquals(setOf(Problem.BLANK_TITLE), problems(draft(title = "  ")))
        assertEquals(setOf(Problem.TITLE_TOO_LONG), problems(draft(title = "x".repeat(81))))
        assertEquals(setOf(Problem.PLACE_TOO_LONG), problems(draft(place = "x".repeat(121))))
        assertEquals(setOf(Problem.NOTES_TOO_LONG), problems(draft(notes = "x".repeat(2001))))
    }

    @Test
    fun `slots must end after they start and span at most 31 days`() {
        assertEquals(setOf(Problem.BAD_SLOT), problems(draft(slot = timed("17:00", "17:00"))))
        assertEquals(setOf(Problem.BAD_SLOT), problems(draft(slot = timed("17:00", "16:00"))))
        assertEquals(emptySet<Problem>(), problems(draft(slot = timed("22:00", "06:00", endDayOffset = 1))))
        assertEquals(setOf(Problem.BAD_SLOT), problems(draft(slot = Slot.AllDay(0))))
        assertEquals(setOf(Problem.BAD_SLOT), problems(draft(slot = Slot.AllDay(32))))
        assertEquals(setOf(Problem.BAD_SLOT), problems(draft(slot = timed("09:00", "10:00", endDayOffset = 31))))
    }

    @Test
    fun `repeat rules must be well formed, end after they start, and produce the start date`() {
        assertEquals(setOf(Problem.BAD_REPEAT), problems(draft(start = "2026-10-06", repeat = Repeat.Weekly(0, setOf(TUESDAY)))))
        assertEquals(setOf(Problem.BAD_REPEAT), problems(draft(start = "2026-10-06", repeat = Repeat.Weekly(1, emptySet()))))
        assertEquals(setOf(Problem.UNTIL_BEFORE_START),
            problems(draft(start = "2026-10-06", repeat = Repeat.Weekly(1, setOf(TUESDAY), until = date("2026-10-05")))))
        assertEquals(setOf(Problem.START_NOT_IN_RULE), problems(draft(start = "2026-10-06", repeat = Repeat.Weekly(1, setOf(WEDNESDAY)))))
    }

    @Test
    fun `people must exist and an explicit list must not be empty`() {
        val stranger = MemberId(UUID.fromString("99999999-9999-4999-8999-999999999999"))
        assertEquals(setOf(Problem.NO_PEOPLE), problems(draft(who = Who.Members(emptySet()))))
        assertEquals(setOf(Problem.UNKNOWN_MEMBER), problems(draft(who = Who.Members(setOf(stranger)))))
        assertEquals(setOf(Problem.UNKNOWN_MEMBER), problems(draft(taking = Taking.Person(stranger))))
    }

    @Test
    fun `at most 3 reminders, each in range`() {
        val four = List(4) { Reminder.MinutesBefore(it * 10) }
        assertEquals(setOf(Problem.BAD_REMINDERS), problems(draft(reminders = four)))
        assertEquals(setOf(Problem.BAD_REMINDERS), problems(draft(reminders = listOf(Reminder.MinutesBefore(10_081)))))
        assertEquals(setOf(Problem.BAD_REMINDERS), problems(draft(reminders = listOf(Reminder.DaysBeforeAt(8, time("09:00"))))))
    }

    @Test
    fun `the draft becomes one event upsert with every field written`() {
        val id = dev.housecalendar.contracts.EventId(UUID.fromString("12345678-1234-4234-8234-123456789012"))
        val payload = Recording.newEvent(id, draft(place = "Smile Dental"))
        assertEquals(id, payload.eventId)
        assertEquals("Dentist", payload.title)
        assertEquals("Smile Dental", payload.place)
        assertEquals(false, payload.deleted)
        assertEquals(Repeat.Never, payload.repeat)
    }

    @Test
    fun `untilForCount gives up rather than looping when the rule never produces enough`() {
        assertEquals(null, Recording.untilForCount(family.log.state(), date("2026-10-06"), Repeat.Weekly(1, emptySet()), false, 3))
        assertEquals(date("2026-10-06"), Recording.untilForCount(family.log.state(), date("2026-10-06"), Repeat.Never, false, 1))
    }
}
