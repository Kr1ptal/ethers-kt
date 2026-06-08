package io.ethers.core.types.tracers

import io.ethers.core.FastHex
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
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

    /**
     * Decode the tracer result from the JSON element.
     *
     * Default implementation uses [resultType] for simple deserialization via kotlinx.serialization.
     * Override for custom decoding logic (e.g., MuxTracer).
     */
    @Suppress("UNCHECKED_CAST")
    fun decodeResult(json: Json, element: JsonElement): T {
        return json.decodeFromJsonElement(serializer(resultType.java), element) as T
    }
}

@Serializable(with = TracerConfigSerializer::class)
data class TracerConfig<T : Any> @JvmOverloads constructor(
    val tracer: AnyTracer<T>,
    val timeoutMs: Long = -1L,
    val reexec: Long = -1L,
    val stateOverrides: StateOverride? = null,
    val blockOverrides: BlockOverride? = null,
    val txIndex: Int = -1,
)

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
