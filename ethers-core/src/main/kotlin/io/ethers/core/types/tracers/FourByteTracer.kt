package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.readMapOf
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

    /**
     * Result of the 4byte tracer, mapping 4-byte identifiers (with data size suffix) to call counts.
     */
    @JsonDeserialize(using = ResultDeserializer::class)
    data class Result(val entries: Map<String, Int>)

    private class ResultDeserializer : JsonDeserializer<Result>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Result {
            return Result(p.readMapOf({ it }) { intValue })
        }
    }
}
