package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.HolidayUpsert
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Taking
import dev.housecalendar.contracts.Who
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Random

/**
 * NFR-DET-1, NFR-DET-4, NFR-EXT-1 and ADR-12 over random logs: the calendar depends on the op *set*, the
 * projection is incremental, windows can be split, and unknown future types change nothing.
 */
class PropertiesTest {
    private val iterations = 150
    private val origin: LocalDate = date("2026-09-01")

    @Test
    fun `any permutation or duplication of the log gives the same state`() = forEachSeed { random, log ->
        val expected = Projection.fold(log)
        assertEquals(expected, Projection.fold(log.shuffled(random)))
        assertEquals(expected, Projection.fold((log + log.filter { random.nextInt(3) == 0 }).shuffled(random)))
    }

    @Test
    fun `applying ops in batches equals folding them all at once`() = forEachSeed { random, log ->
        val shuffled = log.shuffled(random)
        val cut = random.nextInt(shuffled.size + 1)
        assertEquals(Projection.fold(shuffled), Projection.fold(shuffled.take(cut)).plus(shuffled.drop(cut)))
    }

    @Test
    fun `splitting a window never changes the agenda`() = forEachSeed { random, log ->
        val state = Projection.fold(log)
        val from = origin.plusDays(random.nextInt(60).toLong())
        val to = from.plusDays(10L + random.nextInt(80))
        val split = from.plusDays(random.nextInt((to.toEpochDay() - from.toEpochDay()).toInt()).toLong())
        val whole = Agenda.resolve(state, DateWindow(from, to)).toSet()
        val left = Agenda.resolve(state, DateWindow(from, split)).toSet()
        val right = Agenda.resolve(state, DateWindow(split.plusDays(1), to)).toSet()
        assertEquals(whole, left + right)
        assertEquals(whole.map { it.key }.toSet().size, (left + right).size)
    }

    @Test
    fun `ops of unknown future types change nothing on the calendar`() = forEachSeed { random, log ->
        val builder = CalendarLog(random.nextLong())
        val future = List(1 + random.nextInt(20)) {
            val type = listOf("task.upsert", "meal.set", "list.item", "event.reaction")[random.nextInt(4)]
            builder.raw(type, """{"id":"${builder.uuid()}","x":${random.nextInt()}}""", schema = 1 + random.nextInt(2))
        }
        val state = Projection.fold(log)
        val withFuture = Projection.fold((log + future).shuffled(random))
        val window = DateWindow(origin, origin.plusDays(120))
        assertEquals(Agenda.resolve(state, window), Agenda.resolve(withFuture, window))
        assertEquals(state.withoutDiagnostics(), withFuture.withoutDiagnostics())
    }

    private fun forEachSeed(check: (Random, List<Op>) -> Unit) {
        repeat(iterations) { iteration ->
            val seed = 0xCA1E0000L + iteration
            val random = Random(seed)
            try {
                check(random, randomLog(random))
            } catch (failure: AssertionError) {
                throw AssertionError("seed $seed: ${failure.message}", failure)
            }
        }
    }

    private fun randomLog(random: Random): List<Op> {
        val log = CalendarLog(random.nextLong()).createFamily()
        val devices = listOf(log.phoneA, log.phoneB, log.display)
        fun device() = devices[random.nextInt(devices.size)]
        fun lamport(): Long? = if (random.nextInt(3) == 0) 1L + random.nextInt(300) else null
        fun day(): LocalDate = origin.plusDays(random.nextInt(150).toLong())
        val members: List<MemberId> = List(2 + random.nextInt(4)) {
            log.member("M$it", if (it < 2) MemberKind.Adult else MemberKind.Child, it, device())
        }
        val events = mutableListOf<EventId>()
        fun who(): Who = if (random.nextInt(4) == 0) Who.Everyone else Who.Members(members.shuffled(random).take(1 + random.nextInt(2)).toSet())
        fun taking(): Taking = when (random.nextInt(4)) {
            0 -> Taking.NotNeeded
            1 -> Taking.NeedsSomeone
            else -> Taking.Person(members[random.nextInt(members.size)])
        }
        fun slot(): Slot = if (random.nextInt(4) == 0) Slot.AllDay(1 + random.nextInt(3)) else {
            val hour = 6 + random.nextInt(14)
            Slot.Timed(time("%02d:00".format(hour)), time("%02d:30".format(hour + 1 + random.nextInt(3))), 0)
        }
        fun repeat(start: LocalDate): Repeat = when (random.nextInt(6)) {
            0 -> Repeat.Daily(1 + random.nextInt(3), until = if (random.nextBoolean()) start.plusDays(40) else null)
            1, 2 -> Repeat.Weekly(1 + random.nextInt(2), setOf(start.dayOfWeek) + DayOfWeek.of(1 + random.nextInt(7)))
            3 -> Repeat.MonthlyOnDay(1, start.dayOfMonth)
            4 -> Repeat.MonthlyOnWeekday(1, if (random.nextBoolean()) -1 else 1 + random.nextInt(4), start.dayOfWeek)
            else -> Repeat.Never
        }
        repeat(5 + random.nextInt(40)) {
            when (random.nextInt(9)) {
                0, 1 -> {
                    val start = day()
                    events += log.event("E${events.size}", start, slot(), who(), repeat(start), taking(), random.nextBoolean(), device())
                }
                2 -> if (events.isNotEmpty()) log.upsert(EventUpsert(events.random(random), title = "T${random.nextInt(9)}", slot = slot()), device(), lamport())
                3 -> if (events.isNotEmpty()) log.change(EventChange(events.random(random), from = day(), who = who(), slot = slot()), device(), lamport())
                4 -> if (events.isNotEmpty()) log.edit(
                    EventEdit(events.random(random), occurrence = day(), movedTo = if (random.nextBoolean()) day() else null, taking = taking(), cancelled = random.nextInt(5) == 0),
                    device(), lamport(),
                )
                5 -> if (events.isNotEmpty()) log.upsert(EventUpsert(events.random(random), deleted = random.nextInt(3) == 0), device(), lamport())
                6 -> log.holiday("H", day().let { it }, day().plusDays(random.nextInt(7).toLong()), device()).let { id ->
                    if (random.nextInt(3) == 0) log.add(HolidayUpsert(id, deleted = true), device(), lamport())
                }
                7 -> log.add(MemberUpsert(members[random.nextInt(members.size)], archived = random.nextBoolean()), device(), lamport())
                else -> if (events.isNotEmpty()) log.change(EventChange(events.random(random), from = day(), ended = random.nextInt(4) == 0, repeat = repeat(day())), device(), lamport())
            }
        }
        return log.ops()
    }

    private fun <T> List<T>.random(random: Random): T = this[random.nextInt(size)]
}
