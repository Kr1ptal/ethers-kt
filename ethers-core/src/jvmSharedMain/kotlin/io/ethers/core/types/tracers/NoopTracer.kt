package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
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

    /**
     * Empty result marker for the noop tracer.
     */
    @JsonDeserialize(using = ResultDeserializer::class)
    data object Result

    private class ResultDeserializer : JsonDeserializer<Result>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Result {
            // skip returned '{}' object
            p.skipChildren()
            return Result
        }
    }
}
