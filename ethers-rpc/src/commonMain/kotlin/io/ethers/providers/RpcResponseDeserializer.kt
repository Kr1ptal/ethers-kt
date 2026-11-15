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
internal class RpcResponseDeserializer<T>(private val result: DeserializationStrategy<T>) : DeserializationStrategy<Result<T, RpcError>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("io.ethers.providers.RpcResponse") {
        element("id", JsonElement.serializer().descriptor, isOptional = true)
        element("result", result.descriptor, isOptional = true)
        element("error", JsonElement.serializer().descriptor, isOptional = true)
    }

    // the result is only absent while hasResult is false, so value holds a T, which may itself be nullable
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Result<T, RpcError> {
        var id: JsonElement? = null
        var hasResult = false
        var value: T? = null
        var error: JsonElement? = null
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    ID -> id = decodeSerializableElement(descriptor, ID, JsonElement.serializer())
                    RESULT -> {
                        value = decodeSerializableElement(descriptor, RESULT, result)
                        hasResult = true
                    }
                    ERROR -> error = decodeSerializableElement(descriptor, ERROR, JsonElement.serializer())
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw IllegalStateException("Unexpected element index $index")
                }
            }
        }

        // the same checks, in the same order, as HttpClient.decodeNextResult does on a tree
        id?.jsonPrimitive?.long ?: return HttpClient.ERROR_NO_ID_RESPONSE
        val errorElement = error
        return when {
            hasResult -> success(value as T)
            errorElement != null -> failure(RpcError.fromJsonObject(errorElement.jsonObject))
            else -> HttpClient.ERROR_INVALID_RESPONSE
        }
    }

    private companion object {
        const val ID = 0
        const val RESULT = 1
        const val ERROR = 2
    }
}
