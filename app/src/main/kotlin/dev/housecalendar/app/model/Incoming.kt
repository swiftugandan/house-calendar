package dev.housecalendar.app.model

import dev.housecalendar.intake.IncomingDraft
import dev.housecalendar.intake.InsertExtras
import dev.housecalendar.intake.IntakeResult
import dev.housecalendar.intake.Refusal

/**
 * What another app handed over, exactly as it came (SPEC FR-49 to FR-55). It stays raw until the phone's
 * screens resolve it against the family zone, because an intent can cold-start the app before the
 * calendar has loaded, and times must never be read in the device's zone instead (FR-53).
 */
sealed interface Incoming {
    data class Insert(val extras: InsertExtras) : Incoming

    data class Text(val text: String) : Incoming

    /** At most `IcsReader.MAX_BYTES`: the activity refuses anything larger before it gets here. */
    class File(val bytes: ByteArray) : Incoming

    /** SPEC FR-55: read on the settings screen, where the family zone is already known. */
    data class Copies(val drafts: IntakeResult.Drafts) : Incoming

    data class Refused(val reason: Refusal) : Incoming
}

/** Incoming input resolved in family time: one draft for the add sheet, several for the review, or a refusal. */
sealed interface Arrival {
    data class One(val draft: IncomingDraft) : Arrival

    data class Many(val result: IntakeResult.Drafts, val copies: Boolean) : Arrival

    data class Refused(val reason: Refusal) : Arrival
}
