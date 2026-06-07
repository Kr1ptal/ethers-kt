package io.ethers.core.types.tracers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import org.intellij.lang.annotations.Language
import kotlin.reflect.KClass

/**
 * Evaluate JS functions on the relevant EVM hooks.
 *
 * The result is returned as a raw JSON string, allowing callers to parse it as needed.
 */
data class JSTracer(
    @param:Language("JavaScript")
    val code: String,
    override val config: Map<String, Any?>,
) : Tracer<JSTracer.Result> {
    override val name: String
        get() = code

    override val resultType: KClass<Result>
        get() = Result::class

    override fun decodeResult(json: Json, element: JsonElement): Result {
        return json.decodeFromJsonElement(ResultSerializer, element)
    }

    /**
     * Result of a JS tracer, containing the raw JSON output as a string.
     *
     * @property json The raw JSON string returned by the tracer.
     */
    @Serializable(with = ResultSerializer::class)
    data class Result(val json: String)

    object ResultSerializer : KSerializer<Result> {
        override val descriptor = buildClassSerialDescriptor("JSTracer.Result")

        override fun serialize(encoder: Encoder, value: Result) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): Result {
            val element = (decoder as JsonDecoder).decodeJsonElement()
            return Result(element.toString())
        }
    }
}
