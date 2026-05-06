package io.ethers.core.types.tracers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

/**
 * Perform no action.
 *
 * It's mostly useful for testing purposes.
 */
data object NoopTracer : Tracer<NoopTracer.Result> {
    private val EMPTY_CONFIG = emptyMap<String, Any?>()

    override val name: String
        get() = "noopTracer"

    override val resultType: KClass<Result>
        get() = Result::class

    override val config: Map<String, Any?> = EMPTY_CONFIG

    override fun decodeResult(json: Json, element: JsonElement): Result {
        return json.decodeFromJsonElement(ResultSerializer, element)
    }

    /**
     * Empty result marker for the noop tracer.
     */
    @Serializable(with = ResultSerializer::class)
    data object Result

    object ResultSerializer : KSerializer<Result> {
        override val descriptor = buildClassSerialDescriptor("NoopTracer.Result")

        override fun serialize(encoder: Encoder, value: Result) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): Result {
            // consume the '{}' object
            (decoder as JsonDecoder).decodeJsonElement()
            return Result
        }
    }
}
