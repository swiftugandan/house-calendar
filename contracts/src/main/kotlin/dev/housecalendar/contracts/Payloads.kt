package dev.housecalendar.contracts

/**
 * What an [Op] says happened. Each concrete payload has a stable [PayloadType] string; the JSON body is
 * stored and forwarded byte-for-byte, so a device that does not understand a type still keeps it (SPEC FR-46).
 */
public sealed interface Payload

/** Payload type identifiers. Never rename one: they are stored in every log. */
public object PayloadType

/** A payload of a type (or schema version) this version does not know. Kept and forwarded unchanged. */
public data class UnknownPayload(val type: String, val json: String) : Payload
