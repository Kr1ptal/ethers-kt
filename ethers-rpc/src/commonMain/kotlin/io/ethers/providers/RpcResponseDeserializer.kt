package io.ethers.providers

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Decodes a single JSON-RPC response straight into its result, with [result] reading the `result` value as the
 * stream reaches it, so the response is never parsed into a [JsonElement] tree. Other keys are skipped.
 *
 * Outcomes match decoding the response as a tree: a missing `id` is [HttpClient.ERROR_NO_ID_RESPONSE], a `result`
 * wins over an `error`, and a response with neither is [HttpClient.ERROR_INVALID_RESPONSE]. The one difference is a
 * response without an `id` whose `result` fails to decode: the tree route reports the missing `id`, while this one
 * throws while decoding the `result`, before it can know the `id` is missing. Either way the call fails.
 */
internal class RpcResponseDeserializer<T>(result: DeserializationStrategy<T>) : DeserializationStrategy<Result<T, RpcError>> {
    private val fields = RpcResponseFieldsDeserializer(result)

    override val descriptor: SerialDescriptor get() = fields.descriptor

    // the result is only absent while hasResult is false, so value holds a T, which may itself be nullable
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Result<T, RpcError> {
        val response = fields.deserialize(decoder)

        // the same checks, in the same order, as HttpClient.decodeNextResult does on a tree
        response.id?.jsonPrimitive?.long ?: return HttpClient.ERROR_NO_ID_RESPONSE
        val error = response.error
        return when {
            response.hasResult -> success(response.result as T)
            error != null -> failure(RpcError.fromJsonObject(error.jsonObject))
            else -> HttpClient.ERROR_INVALID_RESPONSE
        }
    }
}

/**
 * The `id`, `result` and `error` of a JSON-RPC response, as read by [RpcResponseFieldsDeserializer]. [result] is only
 * meaningful when [hasResult] is true, since a present `result` may itself decode to null. [hasMethod] tells whether
 * the message also has a `method` key, which a response does not.
 */
internal class RpcResponseFields<T>(
    val id: JsonElement?,
    val hasResult: Boolean,
    val result: T?,
    val error: JsonElement?,
    val hasMethod: Boolean,
)

/**
 * Reads the `id`, `result` and `error` of a JSON-RPC response, decoding `result` with [result] as the stream reaches it
 * and skipping other keys. A repeated key keeps its last value, as a tree does. Each client decides what the fields
 * mean, since HTTP and WebSocket rank `result` and `error` differently.
 */
internal class RpcResponseFieldsDeserializer<T>(private val result: DeserializationStrategy<T>) : DeserializationStrategy<RpcResponseFields<T>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("io.ethers.providers.RpcResponse") {
        element("id", JsonElement.serializer().descriptor, isOptional = true)
        element("result", result.descriptor, isOptional = true)
        element("error", JsonElement.serializer().descriptor, isOptional = true)
        element("method", JsonElement.serializer().descriptor, isOptional = true)
    }

    override fun deserialize(decoder: Decoder): RpcResponseFields<T> {
        var id: JsonElement? = null
        var hasResult = false
        var value: T? = null
        var error: JsonElement? = null
        var hasMethod = false
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    ID -> id = decodeSerializableElement(descriptor, ID, JsonElement.serializer())
                    RESULT -> {
                        value = decodeSerializableElement(descriptor, RESULT, result)
                        hasResult = true
                    }
                    ERROR -> error = decodeSerializableElement(descriptor, ERROR, JsonElement.serializer())
                    METHOD -> {
                        decodeSerializableElement(descriptor, METHOD, JsonElement.serializer())
                        hasMethod = true
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw IllegalStateException("Unexpected element index $index")
                }
            }
        }
        return RpcResponseFields(id, hasResult, value, error, hasMethod)
    }

    private companion object {
        const val ID = 0
        const val RESULT = 1
        const val ERROR = 2
        const val METHOD = 3
    }
}
