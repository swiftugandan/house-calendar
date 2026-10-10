package dev.housecalendar.schedule

import dev.housecalendar.contracts.EventChange
import dev.housecalendar.contracts.EventEdit
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.EventUpsert
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.Op
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import dev.housecalendar.contracts.Who
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.util.Random

/**
 * NFR-PERF-1 on the JVM: the sizing log (8 members × 10 years, 40 series with 4 change-froms each, 25
 * one-offs and 5 occurrence edits a week, 3 updates per event) projects in ≤ 1 s and a 6-week agenda
 * resolves in ≤ 100 ms. Machine-dependent, so it runs in the `benchmark` task, not in `test`.
 */
class SizingBenchmark {
    @Test
    fun `the sizing log projects and resolves within budget`() {
        val log = sizingLog()
        println("sizing log: ${log.size} ops")
        val foldMs = medianMs { Projection.fold(log) }
        val state = Projection.fold(log)
        val window = DateWindow(date("2031-03-02"), date("2031-04-12"))
        val agendaMs = medianMs { Agenda.resolve(state, window) }
        println("fold: $foldMs ms, 6-week agenda: $agendaMs ms, entries: ${Agenda.resolve(state, window).size}")
        assertTrue("fold took $foldMs ms", foldMs <= 1_000)
        assertTrue("agenda took $agendaMs ms", agendaMs <= 100)
    }

    private fun medianMs(block: () -> Unit): Long {
        repeat(3) { block() }
        return List(5) {
            val started = System.nanoTime()
            block()
            (System.nanoTime() - started) / 1_000_000
        }.sorted()[2]
    }

    private fun sizingLog(): List<Op> {
        val random = Random(42)
        val log = CalendarLog(7).createFamily()
        val members = List(8) { log.member("M$it", if (it < 2) MemberKind.Adult else MemberKind.Child, it) }
        val origin = date("2026-01-05")
        val weeks = 520
        repeat(40) { index ->
            val start = origin.plusDays(random.nextInt(7).toLong())
            val id = log.event("Series $index", start, timed("17:00", "18:00"), Who.Members(setOf(members[index % 8])),
                repeat = Repeat.Weekly(1, setOf(start.dayOfWeek)), termTimeOnly = index % 2 == 0)
            repeat(4) { change -> log.change(EventChange(id, from = start.plusWeeks(100L * (change + 1)), place = "P$change")) }
        }
        val oneOffs = mutableListOf<EventId>()
        for (week in 0 until weeks) {
            repeat(25) {
                val day = origin.plusWeeks(week.toLong()).plusDays(random.nextInt(7).toLong())
                val id = log.event("One-off $week.$it", day, if (it % 5 == 0) Slot.AllDay(1) else timed("09:00", "10:00"), Who.Everyone)
                oneOffs += id
                repeat(3) { update -> log.upsert(EventUpsert(id, place = "Place $update")) }
            }
            repeat(5) {
                val day = origin.plusWeeks(week.toLong()).with(DayOfWeek.TUESDAY)
                log.edit(EventEdit(oneOffs[random.nextInt(oneOffs.size)], occurrence = day, notes = "n"))
            }
        }
        return log.ops()
    }
}
