package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
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
 * - [config]: The tracer configuration payload to serialize
 *
 * Example:
 * ```kotlin
 * data class MyTracer(
 *     val someOption: Boolean = false,
 *     val anotherOption: String = "default"
 * ) : Tracer<MyTracer.Result> {
 *     override val name = "myTracer"
 *     override val resultType = Result::class
 *     override val config = mapOf(
 *         "someOption" to someOption,
 *         "anotherOption" to anotherOption,
 *     )
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

    /**
     * Tracer configuration payload to serialize as tracerConfig.
     */
    val config: Map<String, Any?>
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
            is Tracer<*> -> {
                // Named tracer - serialize provided config payload
                gen.writeStringField("tracer", tracer.name)
                gen.writeFieldName("tracerConfig")
                serializers.defaultSerializeValue(tracer.config, gen)
            }

            else -> {
                // StructTracer - merge config payload fields at root level
                for ((fieldName, fieldValue) in tracer.config) {
                    gen.writeFieldName(fieldName)
                    serializers.defaultSerializeValue(fieldValue, gen)
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
