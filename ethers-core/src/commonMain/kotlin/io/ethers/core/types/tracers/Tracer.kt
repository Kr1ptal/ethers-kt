package io.ethers.core.types.tracers

import io.ethers.core.FastHex
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockOverrideSerializer
import io.ethers.core.types.StateOverride
import io.ethers.core.types.StateOverrideSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
 *     override val config = buildJsonObject {
 *         put("someOption", someOption)
 *         put("anotherOption", anotherOption)
 *     }
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
     * Tracer configuration, sent as `tracerConfig`.
     */
    val config: JsonObject
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
    private const val TRACER = 0
    private const val TRACER_CONFIG = 1

    // The fields written after the tracer, in this order, as indices into TAIL_NAMES. In the descriptor, each is at
    // TAIL_OFFSET plus its index.
    private const val TIMEOUT = 0
    private const val REEXEC = 1
    private const val STATE_OVERRIDES = 2
    private const val BLOCK_OVERRIDES = 3
    private const val TX_INDEX = 4
    private const val TAIL_OFFSET = 2
    private val TAIL_NAMES = arrayOf("timeout", "reexec", "stateOverrides", "blockOverrides", "txIndex")

    // The fields of the named-tracer form, `{"tracer":<name>,"tracerConfig":{…},…}`. The fields after the tracer's are
    // optional: only the ones that are set are written, in this order.
    override val descriptor = buildClassSerialDescriptor("TracerConfig") {
        element<String>("tracer")
        element("tracerConfig", JsonObject.serializer().descriptor)
        element<String>(TAIL_NAMES[TIMEOUT], isOptional = true)
        element<Long>(TAIL_NAMES[REEXEC], isOptional = true)
        element(TAIL_NAMES[STATE_OVERRIDES], StateOverrideSerializer.descriptor, isOptional = true)
        element(TAIL_NAMES[BLOCK_OVERRIDES], BlockOverrideSerializer.descriptor, isOptional = true)
        element<String>(TAIL_NAMES[TX_INDEX], isOptional = true)
    }

    // The unnamed-tracer form (StructTracer) writes the tracer's config fields at the top level, next to the fields
    // above, so its keys aren't known up front: it is written as a map of string keys, each followed by its value.
    private val inlineConfigDescriptor = MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: TracerConfig<*>) {
        when (val tracer = value.tracer) {
            is Tracer<*> -> {
                val descriptor = descriptor
                encoder.encodeStructure(descriptor) {
                    encodeStringElement(descriptor, TRACER, tracer.name)
                    encodeSerializableElement(descriptor, TRACER_CONFIG, JsonObject.serializer(), tracer.config)
                    for (field in TAIL_NAMES.indices) {
                        if (value.isSet(field)) encodeTailValue(descriptor, TAIL_OFFSET + field, value, field)
                    }
                }
            }

            else -> {
                // Only StructTracer, whose config fields never have the name of a field written after them, so each
                // key is written once, as putting them all into one JSON object would.
                val descriptor = inlineConfigDescriptor
                encoder.encodeStructure(descriptor) {
                    var index = 0
                    for ((name, element) in tracer.config) {
                        encodeStringElement(descriptor, index++, name)
                        encodeSerializableElement(descriptor, index++, JsonElement.serializer(), element)
                    }
                    for (field in TAIL_NAMES.indices) {
                        if (value.isSet(field)) {
                            encodeStringElement(descriptor, index++, TAIL_NAMES[field])
                            encodeTailValue(descriptor, index++, value, field)
                        }
                    }
                }
            }
        }
    }

    private fun TracerConfig<*>.isSet(field: Int): Boolean = when (field) {
        TIMEOUT -> timeoutMs >= 0
        REEXEC -> reexec >= 0
        STATE_OVERRIDES -> !stateOverrides.isNullOrEmpty()
        BLOCK_OVERRIDES -> blockOverrides != null
        else -> txIndex >= 0
    }

    // Write the value of the tail [field], which must be set, as the element at [index].
    private fun CompositeEncoder.encodeTailValue(
        descriptor: SerialDescriptor,
        index: Int,
        value: TracerConfig<*>,
        field: Int,
    ) {
        when (field) {
            TIMEOUT -> encodeStringElement(descriptor, index, "${value.timeoutMs}ms")
            REEXEC -> encodeLongElement(descriptor, index, value.reexec)
            STATE_OVERRIDES -> encodeSerializableElement(descriptor, index, StateOverrideSerializer, value.stateOverrides!!)
            BLOCK_OVERRIDES -> encodeSerializableElement(descriptor, index, BlockOverrideSerializer, value.blockOverrides!!)
            else -> encodeStringElement(descriptor, index, FastHex.encodeWithPrefix(value.txIndex))
        }
    }

    override fun deserialize(decoder: Decoder): TracerConfig<*> = throw UnsupportedOperationException()
}
