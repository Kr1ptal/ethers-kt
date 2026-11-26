package io.ethers.core.types.tracers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Perform no action.
 *
 * It's mostly useful for testing purposes.
 */
data object NoopTracer : Tracer<NoopTracer.Result> {
    private val EMPTY_CONFIG = JsonObject(emptyMap())

    override val name: String
        get() = "noopTracer"

    override val resultSerializer: KSerializer<Result>
        get() = Result.serializer()

    override val config: JsonObject = EMPTY_CONFIG

    /**
     * Empty result marker for the noop tracer.
     */
    @Serializable
    data object Result
}
