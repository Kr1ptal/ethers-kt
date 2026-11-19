package io.ethers.core.types.tracers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement
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
                    throw IllegalArgumentException("Multiple tracers of the same type are not allowed: ${t::class}")
                }
            }
        }
    }

    override val name: String
        get() = "muxTracer"

    override val resultSerializer: KSerializer<Result> = ResultSerializer(tracers)

    override val config: Map<String, Any?> = HashMap<String, Any?>(tracers.size, 1.0f).also {
        tracers.forEach { tracer -> it[tracer.name] = tracer.config }
    }

    /**
     * Decodes a [Result], an object keyed by tracer name, decoding each value with the [tracers] entry of that
     * name. A name that is not among [tracers] fails the decoding.
     */
    class ResultSerializer(private val tracers: List<Tracer<out Any>>) : KSerializer<Result> {
        override val descriptor: SerialDescriptor = MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: Result) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): Result {
            val results = arrayOfNulls<Any>(tracers.size)
            decoder.decodeStructure(descriptor) {
                while (true) {
                    val keyIndex = decodeElementIndex(descriptor)
                    if (keyIndex == CompositeDecoder.DECODE_DONE) break
                    val name = decodeStringElement(descriptor, keyIndex)
                    val tracer = tracers.indexOfFirst { it.name == name }
                    if (tracer < 0) throw IllegalArgumentException("Unknown tracer in mux result: $name")
                    // a map's value has its own index, which also moves the decoder past the key
                    val valueIndex = decodeElementIndex(descriptor)
                    results[tracer] = decodeSerializableElement(descriptor, valueIndex, tracers[tracer].resultSerializer)
                }
            }
            return Result(tracers, results)
        }
    }

    @Serializable(with = MuxTracerResultSerializer::class)
    data class Result(
        val tracers: List<Tracer<out Any>>,
        val results: Array<*>,
    ) {
        operator fun <R : Any, T : Tracer<R>> get(tracer: KClass<T>): R {
            for (i in tracers.indices) {
                if (tracers[i]::class == tracer) {
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

/**
 * Marks [MuxTracer.Result] as serializable, so a [TracerConfig] of a [MuxTracer] has a serializer. It cannot decode a
 * result, which needs the tracers that produced it: use [MuxTracer.resultSerializer] for that.
 */
object MuxTracerResultSerializer : KSerializer<MuxTracer.Result> {
    override val descriptor = buildClassSerialDescriptor("MuxTracerResult")
    override fun serialize(encoder: Encoder, value: MuxTracer.Result) = throw UnsupportedOperationException()
    override fun deserialize(decoder: Decoder): MuxTracer.Result = throw UnsupportedOperationException()
}
