package dev.housecalendar.app.model

import dev.housecalendar.app.TestFamily
import dev.housecalendar.app.date
import dev.housecalendar.app.london
import dev.housecalendar.app.time
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.DeviceUpsert
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.Owner
import dev.housecalendar.contracts.Reminder
import dev.housecalendar.schedule.OccurrenceRef
import dev.housecalendar.schedule.ReminderScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Event detail (SPEC FR-18, FR-34; DESIGN.md "Screens") and the reminder decision (SPEC FR-37, FR-38). */
class DetailAndRemindersTest {
    private val family = TestFamily()
    private val swim = family.swimming()

    @Test
    fun `the detail shows the occurrence, its repeat, its people and who added it`() {
        family.add(DeviceUpsert(family.phone, name = "Sam's phone", role = DeviceRole.Phone, owner = Owner.Member(family.sam)))
        val detail = requireNotNull(EventDetailModel.build(family.state(), OccurrenceRef(swim, date("2026-10-20")), self = family.phone))
        assertEquals("Swimming", detail.title)
        assertEquals(date("2026-10-20"), detail.day)
        assertEquals(time("17:00"), detail.start)
        assertEquals(time("18:00"), detail.end)
        assertTrue(detail.repeating)
        assertTrue(detail.termTimeOnly)
        assertEquals(listOf("Ada"), detail.people.map { it.name })
        assertEquals(TakingMark.Person(PersonMark(family.sam, "Sam", "S", 0)), detail.taking)
        assertEquals(DeviceLabel("Sam's phone", isSelf = true), detail.addedBy)
        assertEquals(DeviceLabel("Sam's phone", isSelf = true), detail.changedBy)
    }

    @Test
    fun `a date that is not an occurrence has no detail`() {
        assertEquals(null, EventDetailModel.build(family.state(), OccurrenceRef(swim, date("2026-10-21")), family.phone))
    }

    @Test
    fun `due reminders are posted, late ones are marked, and older than an hour are dropped`() {
        family.add(EventUpsert(swim, reminders = listOf(Reminder.MinutesBefore(30))))
        val state = family.state()
        val onTime = ReminderDecisions.decide(state, family.sam, ReminderScope.MINE, lastFired = london("2026-10-13T16:00"), now = london("2026-10-13T16:30"))
        assertEquals(listOf(london("2026-10-13T16:30")), onTime.post.map { it.due.at })
        assertEquals(listOf(false), onTime.post.map { it.late })
        assertEquals(london("2026-10-20T16:30"), onTime.nextAlarm)
        assertEquals(london("2026-10-13T16:30"), onTime.fired)

        val late = ReminderDecisions.decide(state, family.sam, ReminderScope.MINE, lastFired = london("2026-10-13T12:00"), now = london("2026-10-13T17:10"))
        assertEquals(listOf(true), late.post.map { it.late })

        val tooLate = ReminderDecisions.decide(state, family.sam, ReminderScope.MINE, lastFired = london("2026-10-13T12:00"), now = london("2026-10-13T17:31"))
        assertEquals(emptyList<Posting>(), tooLate.post)
    }

    @Test
    fun `nothing already fired is posted twice`() {
        family.add(EventUpsert(swim, reminders = listOf(Reminder.MinutesBefore(30))))
        val again = ReminderDecisions.decide(family.state(), family.sam, ReminderScope.MINE, lastFired = london("2026-10-13T16:30"), now = london("2026-10-13T16:40"))
        assertEquals(emptyList<Posting>(), again.post)
    }

    @Test
    fun `a display or a phone without reminders schedules nothing`() {
        val none = ReminderDecisions.decide(family.state(), null, ReminderScope.MINE, lastFired = null, now = Instant.parse("2026-10-13T00:00:00Z"))
        assertEquals(null, none.nextAlarm)
        assertTrue(none.post.isEmpty())
    }
}
