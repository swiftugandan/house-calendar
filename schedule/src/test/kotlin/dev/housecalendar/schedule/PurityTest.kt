package dev.housecalendar.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** SPEC NFR-DET-1: nothing in `:schedule` may read the clock, the locale or a random source. "Now" is a parameter. */
class PurityTest {
    private val forbidden = listOf(
        "System.currentTimeMillis", "System.nanoTime", ".now(", "Clock.", "Locale.getDefault", "TimeZone.getDefault",
        "ZoneId.systemDefault", "Random(", "UUID.randomUUID", "Uuids.v7(",
    )

    @Test
    fun `schedule sources never read the clock, the locale or randomness`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
        val offences = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                forbidden.firstOrNull { it in line }?.let { "${file.name}:${index + 1}: $it" }
            }
        }
        assertEquals(emptyList<String>(), offences)
    }
}
