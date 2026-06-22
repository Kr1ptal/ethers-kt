package io.ethers.core.types.tracers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
        return json.decodeFromJsonElement(Result.serializer(), element)
    }

    /**
     * Empty result marker for the noop tracer.
     */
    @Serializable
    data object Result
}
