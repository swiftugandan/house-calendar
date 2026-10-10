package dev.housecalendar.app.model

import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.Op
import java.time.LocalDate

sealed interface ImportVerdict {
    /** Merge [ops] ops, exactly like a sync (SPEC FR-48). */
    data class Merge(val ops: Int) : ImportVerdict

    /** Ops of another family would mix two calendars; refused. */
    data object OtherFamily : ImportVerdict

    data object Empty : ImportVerdict
}

/** SPEC FR-48: the passphrase-encrypted export of the whole log, and what may be imported where. */
object BackupModel {
    fun verdict(ops: List<Op>, family: FamilyId): ImportVerdict = when {
        ops.isEmpty() -> ImportVerdict.Empty
        ops.any { it.familyId != family } -> ImportVerdict.OtherFamily
        else -> ImportVerdict.Merge(ops.size)
    }

    fun fileName(today: LocalDate): String = "house-calendar-$today.hcx"
}
