package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.StateOverride

/**
 * New type for tracers, where each tracer has a distinct name by which it's identified on the node.
 * */
interface Tracer<T> : AnyTracer<T> {
    val name: String
}

/**
 * Base type for tracers which support encoding config and decoding result, without a distinct name. Only implemented
 * by [StructTracer], which is used by default if no tracer name is provided in `debug_traceCall` RPC call.
 *
 * Use [Tracer] type when implementing custom tracers.
 * */
sealed interface AnyTracer<T> {
    /**
     * Encode configuration of this tracer.
     */
    fun encodeConfig(gen: JsonGenerator)

    /**
     * Decode trace and return data object.
     */
    fun decodeResult(parser: JsonParser): T
}

@JsonSerialize(using = TracerConfigSerializer::class)
data class TracerConfig<T> @JvmOverloads constructor(
    val tracer: AnyTracer<T>,
    val timeoutMs: Long = -1L,
    val reexec: Long = -1L,
    val stateOverrides: StateOverride? = null,
    val blockOverrides: BlockOverride? = null,
)

private class TracerConfigSerializer : JsonSerializer<TracerConfig<*>>() {
    override fun serialize(value: TracerConfig<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        if (value.tracer is Tracer<*>) {
            gen.writeStringField("tracer", value.tracer.name)

            gen.writeFieldName("tracerConfig")
            gen.writeStartObject()
            value.tracer.encodeConfig(gen)
            gen.writeEndObject()
        } else {
            value.tracer.encodeConfig(gen)
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

        gen.writeEndObject()
    }
}
