package dev.housecalendar.intake

import dev.housecalendar.contracts.Limits
import dev.housecalendar.contracts.Repeat
import dev.housecalendar.contracts.Slot
import java.time.LocalDate

/**
 * What another app handed over, filled in as far as the source allows (SPEC FR-49 to FR-55). Never recorded
 * as it is: the add sheet shows it, and a person confirms it, usually after adding who and taking.
 */
public data class IncomingDraft(
    val title: String,
    /** Null when the source did not say (shared text without a date). */
    val day: LocalDate? = null,
    val slot: Slot? = null,
    val repeat: Repeat = Repeat.Never,
    /** An RRULE `COUNT`, for the app to turn into an until date with the term-time skip (SPEC FR-22, FR-52). */
    val count: Int? = null,
    /** A rule that does not map onto SPEC FR-20, kept so the app can note it (FR-52). */
    val unmappableRule: String? = null,
    /** `EXDATE`s, recorded as "only this one" deletions (FR-52). */
    val exdates: List<LocalDate> = emptyList(),
    val place: String = "",
    val notes: String = "",
    /** The iCalendar UID, for the deterministic id (SPEC FR-54). */
    val uid: String? = null,
) {
    val allDay: Boolean get() = slot is Slot.AllDay

    /** The last day the draft covers. */
    val lastDay: LocalDate? get() = day?.let { first ->
        when (val current = slot) {
            is Slot.AllDay -> first.plusDays(current.days - 1L)
            is Slot.Timed -> first.plusDays(current.endDayOffset.toLong())
            is Slot.Unrecognised, null -> first
        }
    }

    internal companion object {
        fun limited(title: String, place: String, notes: String): Triple<String, String, String> = Triple(
            title.trim().take(Limits.TITLE_CHARS),
            place.trim().take(Limits.PLACE_CHARS),
            notes.trim().take(Limits.NOTES_CHARS),
        )
    }
}

/** Why a whole input was refused (SPEC FR-56). */
public enum class Refusal { TOO_LARGE, TOO_MANY_EVENTS, NOT_A_CALENDAR }

public sealed interface IntakeResult {
    /** [skipped] items could not be read; [overridesSkipped] were changes to single occurrences (FR-52). */
    public data class Drafts(val drafts: List<IncomingDraft>, val skipped: Int, val overridesSkipped: Int) : IntakeResult

    public data class Refused(val reason: Refusal) : IntakeResult
}
