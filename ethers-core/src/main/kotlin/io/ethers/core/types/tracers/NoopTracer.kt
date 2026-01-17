package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.beans.Transient
import kotlin.reflect.KClass

/**
 * Perform no action.
 *
 * It's mostly useful for testing purposes.
 */
data object NoopTracer : Tracer<NoopTracer.Result> {
    @get:Transient
    override val name: String
        get() = "noopTracer"

    @get:Transient
    override val resultType: KClass<Result>
        get() = Result::class

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
