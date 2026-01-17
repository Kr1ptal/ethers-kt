package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ethers.core.FastHex
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.StateOverride
import kotlin.reflect.KClass

/**
 * Named tracer type, where each tracer has a distinct name by which it's identified on the node.
 *
 * To implement a custom tracer, create a data class implementing this interface and define:
 * - [name]: The tracer name as recognized by the node
 * - [resultType]: The KClass of the result type for deserialization
 *
 * The tracer's properties will be serialized as the tracer config using Jackson's default serialization.
 * Use Jackson annotations (e.g., `@JsonInclude`, `@JsonProperty`) to customize serialization if needed.
 *
 * Example:
 * ```kotlin
 * data class MyTracer(
 *     val someOption: Boolean = false,
 *     val anotherOption: String = "default"
 * ) : Tracer<MyTracer.Result> {
 *     override val name = "myTracer"
 *     override val resultType = Result::class
 *
 *     data class Result(val data: String, val count: Int)
 * }
 * ```
 */
interface Tracer<T : Any> : AnyTracer<T> {
    val name: String
}

/**
 * Base type for tracers, without a distinct name. Only implemented by [StructTracer], which is used by default
 * if no tracer name is provided in `debug_traceCall` RPC call.
 *
 * Use [Tracer] type when implementing custom tracers.
 */
sealed interface AnyTracer<T : Any> {
    /**
     * The result type class for deserialization.
     */
    val resultType: KClass<out T>
}

@JsonSerialize(using = TracerConfigSerializer::class)
data class TracerConfig<T : Any> @JvmOverloads constructor(
    val tracer: AnyTracer<T>,
    val timeoutMs: Long = -1L,
    val reexec: Long = -1L,
    val stateOverrides: StateOverride? = null,
    val blockOverrides: BlockOverride? = null,
    val txIndex: Int = -1,
)

private class TracerConfigSerializer : JsonSerializer<TracerConfig<*>>() {
    override fun serialize(value: TracerConfig<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        when (val tracer = value.tracer) {
            is MuxTracer -> {
                // MuxTracer needs special handling - serialize each nested tracer's config
                gen.writeStringField("tracer", tracer.name)
                gen.writeFieldName("tracerConfig")
                gen.writeStartObject()
                for (t in tracer.tracers) {
                    gen.writeFieldName(t.name)
                    serializers.defaultSerializeValue(t, gen)
                }
                gen.writeEndObject()
            }

            is Tracer<*> -> {
                // Named tracer - use Jackson's default serialization for config
                gen.writeStringField("tracer", tracer.name)
                gen.writeFieldName("tracerConfig")
                serializers.defaultSerializeValue(tracer, gen)
            }

            else -> {
                // StructTracer - merge its fields at root level
                val mapper = gen.codec as? ObjectMapper
                    ?: throw IllegalStateException("No ObjectMapper configured on JsonGenerator")
                val node = mapper.valueToTree<ObjectNode>(tracer)
                node.fields().forEach { (fieldName, nodeValue) ->
                    gen.writeFieldName(fieldName)
                    gen.writeTree(nodeValue)
                }
            }
        }

        if (value.timeoutMs >= 0) {
            gen.writeStringField("timeout", "${value.timeoutMs}ms")
        }
        if (value.reexec >= 0) {
            gen.writeNumberField("reexec", value.reexec)
        }
        if (!value.stateOverrides.isNullOrEmpty()) {
            gen.writeObjectField("stateOverrides", value.stateOverrides)
        }
        if (value.blockOverrides != null) {
            gen.writeObjectField("blockOverrides", value.blockOverrides)
        }
        if (value.txIndex >= 0) {
            gen.writeStringField("txIndex", FastHex.encodeWithPrefix(value.txIndex))
        }

        gen.writeEndObject()
    }
}
