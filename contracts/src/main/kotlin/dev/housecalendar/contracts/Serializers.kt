package dev.housecalendar.contracts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.reflect.KClass

/** Canonical lower-case hyphenated form. */
public object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housecalendar.UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** ISO-8601 calendar date, e.g. `2026-10-05`. */
public object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housecalendar.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate): Unit = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** IANA tz identifier [SPEC R3], e.g. `Europe/London`. */
public object ZoneIdSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housecalendar.ZoneId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ZoneId): Unit = encoder.encodeString(value.id)
    override fun deserialize(decoder: Decoder): ZoneId = ZoneId.of(decoder.decodeString())
}

/** `MONDAY` … `SUNDAY`. */
public object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housecalendar.DayOfWeek", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DayOfWeek): Unit = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): DayOfWeek = DayOfWeek.valueOf(decoder.decodeString())
}

/** Wall-clock time to the minute, `HH:mm`; seconds are dropped when written (SPEC FR-9). */
public object LocalTimeSerializer : KSerializer<LocalTime> {
    private val pattern = Regex("([01][0-9]|2[0-3]):([0-5][0-9])")
    private const val PAD = 2

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.housecalendar.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime): Unit =
        encoder.encodeString(value.hour.toString().padStart(PAD, '0') + ":" + value.minute.toString().padStart(PAD, '0'))

    override fun deserialize(decoder: Decoder): LocalTime {
        val text = decoder.decodeString()
        val match = pattern.matchEntire(text) ?: throw SerializationException("not an HH:mm time: $text")
        return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }
}

/** One variant of a [TaggedUnionSerializer]: the `type` tag it is written with, and its own serializer. */
public class Variant<T : Any>(internal val tag: String, internal val type: KClass<out T>, internal val serializer: KSerializer<out T>)

/**
 * A sealed field type written as a JSON object with a `"type"` tag followed by the variant's own fields.
 *
 * A tag this version does not know is read as the union's unrecognised variant, holding the object as it
 * was, so a newer device's value never makes the whole payload unreadable (SPEC FR-46, NFR-EXT-1). The
 * unrecognised variant is written back exactly as it was read.
 */
public abstract class TaggedUnionSerializer<T : Any>(private val serialName: String) : KSerializer<T> {
    protected abstract val variants: List<Variant<out T>>

    /** The value standing for an object whose tag is not one of [variants]. */
    protected abstract fun unrecognised(json: JsonObject): T

    /** The JSON an unrecognised value was read from, or null for a known variant. */
    protected abstract fun rawOf(value: T): JsonObject?

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(serialName)

    override fun serialize(encoder: Encoder, value: T) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("$value can only be written as JSON")
        output.encodeJsonElement(rawOf(value) ?: tagged(output.json, value))
    }

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as? JsonDecoder ?: throw SerializationException("$serialName can only be read from JSON")
        val element = input.decodeJsonElement() as? JsonObject ?: throw SerializationException("$serialName must be an object")
        val tag = (element[TAG] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw SerializationException("$serialName has no \"$TAG\"")
        val variant = variants.firstOrNull { it.tag == tag } ?: return unrecognised(element)
        return input.json.decodeFromJsonElement(variant.serializer, JsonObject(element - TAG))
    }

    private fun tagged(json: Json, value: T): JsonObject {
        val variant = variants.firstOrNull { it.type.isInstance(value) }
            ?: throw SerializationException("${value::class} is not a variant of $serialName")
        @Suppress("UNCHECKED_CAST") // the variant was chosen because value is an instance of its type
        val fields = json.encodeToJsonElement(variant.serializer as KSerializer<T>, value) as? JsonObject
            ?: throw SerializationException("${variant.tag} must encode to an object")
        return JsonObject(mapOf(TAG to JsonPrimitive(variant.tag)) + fields)
    }

    private companion object {
        const val TAG = "type"
    }
}

/**
 * An open set of named values written as plain strings. A name this version does not know is kept as the
 * set's unrecognised value and written back unchanged, like [TaggedUnionSerializer]'s unrecognised variant.
 */
public abstract class OpenSetSerializer<T : Any>(private val serialName: String) : KSerializer<T> {
    protected abstract val known: Map<String, T>

    protected abstract fun unrecognised(raw: String): T

    protected abstract fun rawOf(value: T): String?

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        val name = rawOf(value) ?: known.entries.firstOrNull { it.value == value }?.key
            ?: throw SerializationException("$value has no name in $serialName")
        encoder.encodeString(name)
    }

    override fun deserialize(decoder: Decoder): T {
        val raw = decoder.decodeString()
        return known[raw] ?: unrecognised(raw)
    }
}
