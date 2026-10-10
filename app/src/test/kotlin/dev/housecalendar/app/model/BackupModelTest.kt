package dev.housecalendar.app.model

import dev.housecalendar.app.TestFamily
import dev.housecalendar.contracts.FamilyId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** SPEC FR-48: an export merges like a sync, and only into the family it came from. */
class BackupModelTest {
    private val family = TestFamily()

    @Test
    fun `an export of this family merges`() {
        assertEquals(ImportVerdict.Merge(family.ops().size), BackupModel.verdict(family.ops(), family.family))
    }

    @Test
    fun `an export of another family is refused`() {
        val other = FamilyId(UUID.fromString("99999999-9999-4999-8999-999999999999"))
        assertEquals(ImportVerdict.OtherFamily, BackupModel.verdict(family.ops(), other))
    }

    @Test
    fun `an empty or mixed export is refused`() {
        assertEquals(ImportVerdict.Empty, BackupModel.verdict(emptyList(), family.family))
        val mixed = family.ops() + TestFamily(seed = 9).ops()
        assertEquals(ImportVerdict.OtherFamily, BackupModel.verdict(mixed, family.family))
    }

    @Test
    fun `export files are named by the family time date`() {
        assertEquals("house-calendar-2026-10-10.hcx", BackupModel.fileName(java.time.LocalDate.of(2026, 10, 10)))
    }
}
