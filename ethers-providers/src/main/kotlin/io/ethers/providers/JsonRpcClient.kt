package io.ethers.providers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.json.JsonMapper
import io.channels.core.ChannelReceiver
import io.ethers.core.Jackson
import io.ethers.core.Result
import io.ethers.core.forEachObjectField
import io.ethers.providers.types.BatchRpcRequest
import okhttp3.OkHttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface JsonRpcClient : AutoCloseable {
    /**
     * Asynchronously execute RPC request.
     *
     * @param method RPC function name
     * @param params RPC function parameters
     * @param resultType class into which JSON result is converted
     */
    fun <T> request(
        method: String,
        params: Array<*>,
        resultType: Class<T>,
    ) = request(method, params) { p -> p.readValueAs(resultType) }

    /**
     * Asynchronously execute RPC request.
     *
     * @param method RPC function name
     * @param params RPC function parameters
     * @param resultDecoder function to convert JSON result into object return [T].
     */
    fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (JsonParser) -> T,
    ): CompletableFuture<Result<T, RpcError>>

    /**
     * Asynchronously execute [batch] of RPC requests.
     */
    fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean>

    /**
     * Subscribe to a stream via `eth_subscribe`, if the client supports it.
     *
     * @param params the subscription parameters
     * @param resultType class into which JSON result is converted
     */
    fun <T : Any> subscribe(
        params: Array<*>,
        resultType: Class<T>,
    ): CompletableFuture<Result<ChannelReceiver<T>, RpcError>> {
        return subscribe(params) { p -> p.readValueAs(resultType) }
    }

    /**
     * Subscribe to a stream via `eth_subscribe`, if the client supports it.
     *
     * @param params the subscription parameters
     * @param resultDecoder function to convert JSON result into return object [T]
     */
    fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (JsonParser) -> T,
    ): CompletableFuture<Result<ChannelReceiver<T>, RpcError>>
}

/**
 * Write JSON-RPC request directly to receiver [JsonGenerator].
 */
internal fun JsonGenerator.writeJsonRpcRequest(method: String, id: Long, params: Array<*>) {
    writeStartObject()
    writeNumberField("id", id)
    writeStringField("jsonrpc", "2.0")
    writeStringField("method", method)
    writeArrayFieldStart("params")
    for (p in params) {
        writeObject(p)
    }
    writeEndArray()
    writeEndObject()
}

/**
 * Internal JSON-RPC error, returned when the RPC call fails.
 */
@JsonDeserialize(using = RpcErrorDeserializer::class)
data class RpcError @JvmOverloads constructor(
    val code: Int,
    val message: String,
    val data: JsonNode? = null,
    val cause: Exception? = null,
) : Result.Error {
    override fun doThrow(): Nothing {
        throw RuntimeException(this.toString(), cause)
    }

    /**
     * Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text.
     * */
    val isParseError get() = code == CODE_PARSE_ERROR

    /**
     * The JSON sent is not a valid Request object.
     * */
    val isInvalidRequest get() = code == CODE_INVALID_REQUEST

    /**
     * The method does not exist / is not available.
     * */
    val isMethodNotFound: Boolean
        get() {
            return code == CODE_METHOD_NOT_FOUND ||
                UNSUPPORTED_METHOD_MESSAGE_FRAGMENTS.any {
                    message.contains(
                        it,
                        ignoreCase = true,
                    )
                }
        }

    /**
     * Invalid method parameter(s).
     * */
    val isInvalidParams get() = code == CODE_INVALID_PARAMS

    /**
     * Internal JSON-RPC error.
     * */
    val isInternalError get() = code == CODE_INTERNAL_ERROR

    /**
     * Server error. Contains implementation-defined server-errors.
     * */
    val isServerError get() = code in CODE_SERVER_ERROR

    /**
     * Action is not authorized, e.g. sending from a locked account.
     * */
    val isUnauthorized get() = code == CODE_UNAUTHORIZED

    /**
     * Action is not allowed, e.g. preventing an action, while another dependant action is being processed.
     * */
    val isActionNotAllowed get() = code == CODE_ACTION_NOT_ALLOWED

    /**
     * Will contain a subset of custom errors in the data field. See below.
     * */
    val isExecutionError get() = code == CODE_EXECUTION_ERROR

    /**
     * Client error. Contains custom-defined client-errors.
     * */
    val isClientError get() = code in CODE_CLIENT_ERROR

    /**
     * Invalid response received from the server.
     * */
    val isInvalidResponse get() = code == CODE_INVALID_RESPONSE

    /**
     * No response received from the server.
     * */
    val isNoResponse get() = code == CODE_NO_RESPONSE

    /**
     * Call timed out.
     * */
    val isCallTimeout get() = code == CODE_CALL_TIMEOUT

    /**
     * Call failed.
     * */
    val isCallFailed get() = code == CODE_CALL_FAILED

    companion object {
        private val UNSUPPORTED_METHOD_MESSAGE_FRAGMENTS = listOf(
            "did not match any variant", // foundry's Anvil
            "is not available", // go-ethereum / standard response
            "not supported", // hardhat
            "unsupported", // alchemy
        )

        // Standard JSON-RPC errors
        const val CODE_PARSE_ERROR = -32700
        const val CODE_INVALID_REQUEST = -32600
        const val CODE_METHOD_NOT_FOUND = -32601
        const val CODE_INVALID_PARAMS = -32602
        const val CODE_INTERNAL_ERROR = -32603

        @JvmField
        val CODE_SERVER_ERROR = -32099..-32000

        @JvmField
        val CODE_CLIENT_ERROR = 5000..<5100

        // Custom errors
        const val CODE_UNAUTHORIZED = 1
        const val CODE_ACTION_NOT_ALLOWED = 2
        const val CODE_EXECUTION_ERROR = 3

        // Client errors
        const val CODE_INVALID_RESPONSE = 5000
        const val CODE_NO_RESPONSE = 5001
        const val CODE_CALL_TIMEOUT = 5002
        const val CODE_CALL_FAILED = 5003
    }
}

/**
 * Config for creating a new [JsonRpcClient]. Not all values are used by all clients.
 * */
class RpcClientConfig {
    /**
     * Client to use for making JSON-RPC requests. If not set, a default client will be used.
     * */
    var client: OkHttpClient? = null
        @JvmSynthetic set
        get() = field ?: DEFAULT_CLIENT

    /**
     * Headers to include with each RPC request. Can be used to set authorization headers, etc...
     * */
    var requestHeaders: Map<String, String> = emptyMap()
        @JvmSynthetic set

    /**
     * Jackson [JsonMapper] to use for serializing/deserializing JSON-RPC requests and responses.
     * If not set, the default [Jackson.MAPPER] will be used.
     * */
    var jsonMapper: JsonMapper = Jackson.MAPPER
        @JvmSynthetic set

    /**
     * If true, automatically resubscribes existing subscription streams on WebSocket reconnection.
     * If false, closes the streams instead, allowing consumers to handle resubscription explicitly.
     * Default is true (auto-resubscription is enabled).
     *
     * Only used by [WsClient].
     * */
    var resubscribeOnReconnect: Boolean = true
        @JvmSynthetic set

    /**
     * Client to use for making JSON-RPC requests. If not set, a default client will be used.
     * */
    fun client(client: OkHttpClient) = apply { this.client = client }

    /**
     * Headers to include with each RPC request. Can be used to set authorization headers, etc...
     * */
    fun requestHeaders(headers: Map<String, String>) = apply { this.requestHeaders = headers }

    /**
     * Jackson [JsonMapper] to use for serializing/deserializing JSON-RPC requests and responses.
     * If not set, the default [Jackson.MAPPER] will be used.
     * */
    fun jsonMapper(mapper: JsonMapper) = apply { this.jsonMapper = mapper }

    /**
     * If true, automatically resubscribes existing subscription streams on WebSocket reconnection.
     * If false, closes the streams instead, allowing consumers to handle resubscription explicitly.
     * Default is true (auto-resubscription is enabled).
     *
     * Only used by [WsClient].
     * */
    fun resubscribeOnReconnect(resubscribe: Boolean) = apply { this.resubscribeOnReconnect = resubscribe }

    companion object {
        private val DEFAULT_CLIENT by lazy {
            OkHttpClient.Builder()
                .pingInterval(10, TimeUnit.SECONDS)
                .build()
        }

        inline operator fun invoke(builder: RpcClientConfig.() -> Unit): RpcClientConfig {
            return RpcClientConfig().apply(builder)
        }
    }
}

private class RpcErrorDeserializer : JsonDeserializer<RpcError>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): RpcError {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var code = -1
        lateinit var message: String
        var data: JsonNode? = null
        p.forEachObjectField { field ->
            when (field) {
                "code" -> code = p.intValue
                "message" -> message = p.text
                "data" -> data = p.readValueAs(JsonNode::class.java)
                else -> p.skipChildren()
            }
        }

        if (data == null || data.isNull) {
            return RpcError(code, message, null)
        }

        return RpcError(code, message, data)
    }
}
