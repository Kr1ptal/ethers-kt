package io.ethers.core.types.tracers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
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

    override val config: Map<String, Any?> = HashMap<String, Any?>(tracers.size, 1.0f).also {
        tracers.forEach { tracer -> it[tracer.name] = tracer.config }
    }

    override fun decodeResult(json: Json, element: JsonElement): Result {
        val results = arrayOfNulls<Any>(tracers.size)

        for ((name, value) in element.jsonObject.entries) {
            var found = false
            for (i in tracers.indices) {
                val tracer = tracers[i]
                if (name == tracer.name) {
                    results[i] = tracer.decodeResult(json, value)
                    found = true
                    break
                }
            }
            if (!found) throw IllegalArgumentException("Unknown tracer in mux result: $name")
        }

        return Result(tracers, results)
    }

    @Serializable(with = MuxTracerResultSerializer::class)
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

object MuxTracerResultSerializer : KSerializer<MuxTracer.Result> {
    override val descriptor = buildClassSerialDescriptor("MuxTracerResult")
    override fun serialize(encoder: Encoder, value: MuxTracer.Result) = throw UnsupportedOperationException()
    override fun deserialize(decoder: Decoder): MuxTracer.Result = throw UnsupportedOperationException()
}
