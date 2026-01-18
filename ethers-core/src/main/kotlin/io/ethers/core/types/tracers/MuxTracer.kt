package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.ethers.core.forEachObjectField
import kotlin.reflect.KClass

/**
 * Run multiple tracers in one go. Only one tracer with the same type can be nested in a single mux tracer. If you need
 * multiple tracers of the same type - but with different configurations -, consider nesting another mux tracer.
 */
data class MuxTracer(
    val tracers: List<Tracer<out Any>>,
) : Tracer<MuxTracer.Result> {
    constructor(vararg tracers: Tracer<out Any>) : this(tracers.toList())

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

    override val resultType: KClass<Result>
        get() = Result::class

    override val config: Map<String, Any?> = tracers.associate { it.name to it.config }

    override fun decodeResult(mapper: ObjectMapper, parser: JsonParser): Result {
        val results = arrayOfNulls<Any>(tracers.size)

        parser.forEachObjectField { name ->
            for (i in tracers.indices) {
                val tracer = tracers[i]
                if (name == tracer.name) {
                    results[i] = tracer.decodeResult(mapper, parser)
                    return@forEachObjectField
                }
            }
            throw IllegalArgumentException("Unknown tracer in response: $name")
        }

        return Result(tracers, results)
    }

    data class Result(
        val tracers: List<Tracer<out Any>>,
        val results: Array<*>,
    ) {
        operator fun <R : Any, T : Tracer<R>> get(tracer: Class<T>): R {
            for (i in tracers.indices) {
                if (tracers[i].javaClass == tracer) {
                    @Suppress("UNCHECKED_CAST")
                    return results[i] as R
                }
            }

            throw NoSuchElementException("Tracer not found: $tracer")
        }

        operator fun <T : Any> get(tracer: Tracer<T>): T {
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
