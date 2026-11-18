package io.ethers.core.types.tracers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Perform no action.
 *
 * It's mostly useful for testing purposes.
 */
data object NoopTracer : Tracer<NoopTracer.Result> {
    private val EMPTY_CONFIG = emptyMap<String, Any?>()

    override val name: String
        get() = "noopTracer"

    override val resultSerializer: KSerializer<Result>
        get() = Result.serializer()

    override val config: Map<String, Any?> = EMPTY_CONFIG

    /**
     * Empty result marker for the noop tracer.
     */
    @Serializable
    data object Result
}
