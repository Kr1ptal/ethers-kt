package io.ethers.core.types.tracers

import io.ethers.core.FastHex
import io.ethers.core.JsonSerializable
import io.ethers.core.toJsonElement
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockOverrideSerializer
import io.ethers.core.types.StateOverride
import io.ethers.core.types.StateOverrideSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.jvm.JvmOverloads

/**
 * Named tracer type, where each tracer has a distinct name by which it's identified on the node.
 *
 * To implement a custom tracer, create a data class implementing this interface and define:
 * - [name]: The tracer name as recognized by the node
 * - [resultSerializer]: The serializer that decodes the tracer's result
 * - [config]: The tracer configuration payload to serialize
 *
 * Example:
 * ```kotlin
 * data class MyTracer(
 *     val someOption: Boolean = false,
 *     val anotherOption: String = "default"
 * ) : Tracer<MyTracer.Result> {
 *     override val name = "myTracer"
 *     override val resultSerializer = Result.serializer()
 *     override val config = mapOf(
 *         "someOption" to someOption,
 *         "anotherOption" to anotherOption,
 *     )
 *
 *     @Serializable
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
     * Decodes the tracer's result. A [KSerializer] rather than a decode-only strategy, so it composes with
     * `ListSerializer`, `.nullable` and the like when a result is nested in a larger response.
     */
    val resultSerializer: KSerializer<T>

    /**
     * Tracer configuration payload to serialize as tracerConfig.
     */
    val config: Map<String, Any?>
}

@Serializable(with = TracerConfigSerializer::class)
data class TracerConfig<T : Any> @JvmOverloads constructor(
    val tracer: AnyTracer<T>,
    val timeoutMs: Long = -1L,
    val reexec: Long = -1L,
    val stateOverrides: StateOverride? = null,
    val blockOverrides: BlockOverride? = null,
    val txIndex: Int = -1,
) : JsonSerializable<TracerConfig<*>> {
    // generic, so it can't be found by class lookup when passed as a JSON-RPC param
    override val jsonSerializer: KSerializer<TracerConfig<*>>
        get() = TracerConfigSerializer
}

object TracerConfigSerializer : KSerializer<TracerConfig<*>> {
    override val descriptor = buildClassSerialDescriptor("TracerConfig")

    override fun serialize(encoder: Encoder, value: TracerConfig<*>) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                when (val tracer = value.tracer) {
                    is Tracer<*> -> {
                        put("tracer", tracer.name)
                        put("tracerConfig", tracer.config.toJsonElement())
                    }

                    else -> {
                        for ((fieldName, fieldValue) in tracer.config) {
                            put(fieldName, fieldValue.toJsonElement())
                        }
                    }
                }

                if (value.timeoutMs >= 0) {
                    put("timeout", "${value.timeoutMs}ms")
                }
                if (value.reexec >= 0) {
                    put("reexec", value.reexec)
                }
                if (!value.stateOverrides.isNullOrEmpty()) {
                    put("stateOverrides", jsonEncoder.json.encodeToJsonElement(StateOverrideSerializer, value.stateOverrides))
                }
                if (value.blockOverrides != null) {
                    put("blockOverrides", jsonEncoder.json.encodeToJsonElement(BlockOverrideSerializer, value.blockOverrides))
                }
                if (value.txIndex >= 0) {
                    put("txIndex", FastHex.encodeWithPrefix(value.txIndex))
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): TracerConfig<*> = throw UnsupportedOperationException()
}
