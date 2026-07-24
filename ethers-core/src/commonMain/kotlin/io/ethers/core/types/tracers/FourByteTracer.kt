package io.ethers.core.types.tracers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass

/**
 * Search for 4byte-identifiers, and collect them for post-processing.
 * The methods identifiers are collected along with the size of the supplied data, so
 * a reversed signature can be matched against the size of the data.
 *
 * Example:
 *
 * > debug.traceTransaction( "0x214e597e35da083692f5386141e69f47e973b2c56e7a8073b1ea08fd7571e9de", {tracer: "4byteTracer"})
 *	{
 *	  0x27dc297e-128: 1,
 *	  0x38cc4831-0: 2,
 *	  0x524f3889-96: 1,
 *	  0xadf59f99-288: 1,
 *	  0xc281d19e-0: 1
 *	}
 */
data object FourByteTracer : Tracer<FourByteTracer.Result> {
    private val EMPTY_CONFIG = emptyMap<String, Any?>()

    override val name: String
        get() = "4byteTracer"

    override val resultType: KClass<Result>
        get() = Result::class

    override val config: Map<String, Any?> = EMPTY_CONFIG

    override fun decodeResult(json: Json, element: JsonElement): Result {
        return json.decodeFromJsonElement(ResultSerializer, element)
    }

    /**
     * Result of the 4byte tracer, mapping 4-byte identifiers (with data size suffix) to call counts.
     */
    @Serializable(with = ResultSerializer::class)
    data class Result(val entries: Map<String, Int>)

    object ResultSerializer : KSerializer<Result> {
        override val descriptor = buildClassSerialDescriptor("FourByteTracer.Result")

        override fun serialize(encoder: Encoder, value: Result) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): Result {
            val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
            val entries = obj.entries.associate { (k, v) -> k to v.jsonPrimitive.int }
            return Result(entries)
        }
    }
}
