package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.forEachObjectField

/**
 * Run multiple tracers in one go. Only one tracer with the same type can be nested in a single mux tracer. If you need
 * multiple tracers of the same type - but with different configurations -, consider nesting another mux tracer.
 */
data class MuxTracer(val tracers: List<Tracer<*>>) : Tracer<MuxTracer.Result> {
    constructor(vararg tracers: Tracer<*>) : this(tracers.toList())

    init {
        for (i in tracers.indices) {
            val t = tracers[i]
            for (j in i + 1 until tracers.size) {
                if (t.name == tracers[j].name) {
                    throw IllegalArgumentException("Multiple tracers of the same type are not allowed: ${t.javaClass}")
                }
            }
        }
    }

    override val name: String
        get() = "muxTracer"

    // encode as: { "tracerName": { ...config... }, ... }
    override fun encodeConfig(gen: JsonGenerator) {
        for (i in tracers.indices) {
            val t = tracers[i]
            gen.writeFieldName(t.name)

            gen.writeStartObject()
            t.encodeConfig(gen)
            gen.writeEndObject()
        }
    }

    override fun decodeResult(parser: JsonParser): Result {
        val results = arrayOfNulls<Any>(tracers.size)

        parser.forEachObjectField { name ->
            for (i in tracers.indices) {
                val tracer = tracers[i]
                if (name == tracer.name) {
                    results[i] = tracer.decodeResult(parser)
                    return@forEachObjectField
                }
            }

            throw Exception("Tracer not found: $name")
        }

        return Result(tracers, results)
    }

    data class Result(
        val tracers: List<Tracer<*>>,
        val results: Array<*>,
    ) {
        operator fun <R, T : Tracer<R>> get(tracer: Class<T>): R {
            for (i in tracers.indices) {
                if (tracers[i].javaClass == tracer) {
                    @Suppress("UNCHECKED_CAST")
                    return results[i] as R
                }
            }

            throw NoSuchElementException("Tracer not found: $tracer")
        }

        operator fun <T> get(tracer: Tracer<T>): T {
            for (i in tracers.indices) {
                if (tracers[i].name == tracer.name) {
                    @Suppress("UNCHECKED_CAST")
                    return results[i] as T
                }
            }

            throw NoSuchElementException("Tracer not found: $tracer")
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Result

            if (tracers != other.tracers) return false
            if (!results.contentEquals(other.results)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = tracers.hashCode()
            result = 31 * result + results.contentHashCode()
            return result
        }
    }
}
