package dev.housecalendar.sync

import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.Payload
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Who
import dev.housecalendar.schedule.Agenda
import dev.housecalendar.schedule.DateWindow
import dev.housecalendar.schedule.Projection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Random
import java.util.UUID

/**
 * PLAN.md §3, SPEC NFR-DET-2: three devices recording calendar edits and syncing pairwise in any order end
 * with identical op sets, identical calendar state and identical agendas.
 */
class CalendarConvergenceTest {
    private val origin = LocalDate.of(2026, 10, 5)

    @Test
    fun `three devices converge on the same calendar`() = runBlocking(Dispatchers.Default) {
        repeat(60) { iteration ->
            val seed = 0xC0DEL + iteration
            val random = Random(seed)
            val key = FamilyKey.generate()
            val devices = (1..3).map { SimDevice(TestOps.device(it), key) }
            val events = List(4) { EventId(UUID(random.nextLong(), random.nextLong())) }
            events.forEachIndexed { index, id -> devices[index % 3].factory.record(listOf(newEvent(id, random))) }

            repeat(30) {
                if (random.nextInt(3) == 0) {
                    val x = random.nextInt(3)
                    SimDevice.sync(devices[x], devices[(x + 1 + random.nextInt(2)) % 3])
                } else {
                    devices[random.nextInt(3)].factory.record(listOf(randomEdit(events[random.nextInt(events.size)], random)))
                }
            }
            SimDevice.sync(devices[0], devices[1])
            SimDevice.sync(devices[1], devices[2])
            SimDevice.sync(devices[0], devices[1])

            val window = DateWindow(origin, origin.plusDays(90))
            val states = devices.map { Projection.fold(it.log.all()) }
            states.forEach { state ->
                assertEquals("seed $seed: states differ", states[0], state)
                assertEquals("seed $seed: agendas differ", Agenda.resolve(states[0], window), Agenda.resolve(state, window))
            }
        }
    }

    private fun day(random: Random): LocalDate = origin.plusDays(random.nextInt(60).toLong())

    private fun newEvent(id: EventId, random: Random): Payload {
        val start = day(random)
        return EventUpsert(
            id, title = "E", start = start, slot = Slot.Timed(LocalTime.of(17, 0), LocalTime.of(18, 0), 0),
            repeat = Repeat.Weekly(1, setOf(start.dayOfWeek)), who = Who.Everyone,
        )
    }

    private fun randomEdit(id: EventId, random: Random): Payload = when (random.nextInt(4)) {
        0 -> EventUpsert(id, title = "T${random.nextInt(5)}")
        1 -> EventChange(id, from = day(random), place = "P${random.nextInt(5)}")
        2 -> EventEdit(id, occurrence = day(random), movedTo = day(random), cancelled = random.nextInt(4) == 0)
        else -> EventChange(id, from = day(random), repeat = Repeat.Weekly(1, setOf(DayOfWeek.of(1 + random.nextInt(7)))))
    }
}
