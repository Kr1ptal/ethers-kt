package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockOverride

interface Tracer<T> {
    val name: String

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
    val tracer: Tracer<T>,
    val timeoutMs: Long = -1L,
    val reexec: Long = -1L,
    val stateOverrides: Map<Address, AccountOverride>? = null,
    val blockOverrides: BlockOverride? = null,
)

private class TracerConfigSerializer : JsonSerializer<TracerConfig<*>>() {
    override fun serialize(value: TracerConfig<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        if (value.tracer is StructTracer) {
            value.tracer.encodeConfig(gen)
        } else {
            gen.writeStringField("tracer", value.tracer.name)

            gen.writeFieldName("tracerConfig")
            gen.writeStartObject()
            value.tracer.encodeConfig(gen)
            gen.writeEndObject()
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
