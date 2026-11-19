package io.ethers.providers

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement

/**
 * The `method`, `params.subscription` and `params.result` of a subscription notification, as read by
 * [RpcNotificationDeserializer]. [result] is only meaningful when [hasResult] is true. [hasIdOrError] tells whether the
 * message also has an `id` or `error` key, which a notification does not.
 */
internal class RpcNotificationFields<T>(
    val method: String?,
    val subscription: JsonElement?,
    val hasResult: Boolean,
    val result: T?,
    val hasIdOrError: Boolean,
)

/**
 * Reads a subscription notification, decoding `params.result` with [result] as the stream reaches it and skipping other
 * keys, so the notification is never parsed into a [JsonElement] tree. A repeated key keeps its last value, as a tree
 * does. A `params` that is not an object fails the decode.
 */
internal class RpcNotificationDeserializer<T>(result: DeserializationStrategy<T>) : DeserializationStrategy<RpcNotificationFields<T>> {
    private val params = ParamsDeserializer(result)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("io.ethers.providers.RpcNotification") {
        element("method", String.serializer().descriptor, isOptional = true)
        element("params", params.descriptor, isOptional = true)
        element("id", JsonElement.serializer().descriptor, isOptional = true)
        element("error", JsonElement.serializer().descriptor, isOptional = true)
    }

    override fun deserialize(decoder: Decoder): RpcNotificationFields<T> {
        var method: String? = null
        var params: ParamsDeserializer.Params<T>? = null
        var hasIdOrError = false
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    // read as a tree's JsonPrimitive.content reads it: in lenient mode a number is read as its text
                    0 -> method = decodeStringElement(descriptor, 0)
                    1 -> params = decodeSerializableElement(descriptor, 1, this@RpcNotificationDeserializer.params)
                    2, 3 -> {
                        decodeSerializableElement(descriptor, index, JsonElement.serializer())
                        hasIdOrError = true
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw IllegalStateException("Unexpected element index $index")
                }
            }
        }
        val decoded = params
        return RpcNotificationFields(method, decoded?.subscription, decoded?.hasResult == true, decoded?.result, hasIdOrError)
    }

    private class ParamsDeserializer<T>(private val result: DeserializationStrategy<T>) : DeserializationStrategy<ParamsDeserializer.Params<T>> {
        class Params<T>(val subscription: JsonElement?, val hasResult: Boolean, val result: T?)

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("io.ethers.providers.RpcNotificationParams") {
            element("subscription", JsonElement.serializer().descriptor, isOptional = true)
            element("result", result.descriptor, isOptional = true)
        }

        override fun deserialize(decoder: Decoder): Params<T> {
            var subscription: JsonElement? = null
            var hasResult = false
            var value: T? = null
            decoder.decodeStructure(descriptor) {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> subscription = decodeSerializableElement(descriptor, 0, JsonElement.serializer())
                        1 -> {
                            value = decodeSerializableElement(descriptor, 1, result)
                            hasResult = true
                        }
                        CompositeDecoder.DECODE_DONE -> break
                        else -> throw IllegalStateException("Unexpected element index $index")
                    }
                }
            }
            return Params(subscription, hasResult, value)
        }
    }
}
