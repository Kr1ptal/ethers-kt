package io.ethers.providers

import com.github.michaelbull.result.Result
import io.channels.core.ChannelReceiver
import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.core.ThrowingError
import io.ethers.core.json.JsonElement
import io.ethers.core.toJsonElement
import io.ethers.providers.types.BatchRpcRequest
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.serializer
import java.util.concurrent.CompletableFuture
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

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
    ) = request(method, params, decoderForClass(resultType))

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
        resultDecoder: (KJsonElement) -> T,
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
        return subscribe(params, decoderForClass(resultType))
    }

    /**
     * Subscribe to a stream via `eth_subscribe`, if the client supports it.
     *
     * @param params the subscription parameters
     * @param resultDecoder function to convert JSON result into return object [T]
     */
    fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): CompletableFuture<Result<ChannelReceiver<T>, RpcError>>
}

/**
 * Build a result decoder for the legacy `Class<T>`-based [JsonRpcClient.request] and
 * [JsonRpcClient.subscribe] entry points.
 *
 * Special-cases [ByteArray]: Ethereum JSON-RPC returns byte payloads (`eth_getCode`,
 * `eth_call`, …) as 0x-prefixed hex strings. Jackson used to register a custom deserializer
 * for that, but kotlinx' default `ByteArray` serializer expects a JSON array of numbers
 * and would throw a [kotlinx.serialization.json.internal.JsonDecodingException] at runtime.
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> decoderForClass(resultType: Class<T>): (KJsonElement) -> T {
    if (resultType == ByteArray::class.java) {
        return { element ->
            val text = element.jsonPrimitive.content
            val bytes = if (text.isEmpty() || text == "0x" || text == "0X") {
                ByteArray(0)
            } else {
                FastHex.decode(text)
            }
            bytes as T
        }
    }
    val serializer = serializer(resultType)
    return { element -> Kotlinx.DEFAULT.decodeFromJsonElement(serializer, element) as T }
}

/**
 * Build a JSON-RPC request string.
 */
internal fun buildJsonRpcRequest(method: String, id: Long, params: Array<*>): String {
    return buildJsonObject {
        put("id", id)
        put("jsonrpc", "2.0")
        put("method", method)
        putJsonArray("params") {
            params.forEach { add(it.toParamJsonElement()) }
        }
    }.toString()
}

/**
 * Convert any value to a [KJsonElement] suitable for use as a JSON-RPC parameter.
 *
 * Recursively handles common Kotlin/JVM types so callers can pass arbitrary nested
 * structures (e.g. `mapOf("from" to addr, "data" to bytes)`). Custom types are looked up
 * via the kotlinx `@Serializable` contract and fail fast if no serializer is registered,
 * rather than silently producing an unusable payload.
 */
internal fun Any?.toParamJsonElement(): KJsonElement = toJsonElement()

/**
 * Internal JSON-RPC error, returned when the RPC call fails.
 */
data class RpcError @JvmOverloads constructor(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
    val cause: Exception? = null,
) : ThrowingError {
    override fun toException(): RuntimeException {
        return RuntimeException(toString(), cause)
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

        internal fun fromJsonObject(obj: JsonObject): RpcError {
            val code = obj["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            val message = obj["message"]?.jsonPrimitive?.content ?: ""
            val dataEl = obj["data"]
            val data = if (dataEl == null || dataEl is JsonNull) null else JsonElement(dataEl.toString())
            return RpcError(code, message, data)
        }
    }
}

/**
 * Config for creating a new [JsonRpcClient]. Not all values are used by all clients.
 * */
class RpcClientConfig {
    /**
     * Client to use for making JSON-RPC requests. If not set, a default client will be used.
     * */
    var client: KtorHttpClient? = null
        @JvmSynthetic set
        get() = field ?: DEFAULT_CLIENT

    /**
     * Headers to include with each RPC request. Can be used to set authorization headers, etc...
     * */
    var requestHeaders: Map<String, String> = emptyMap()
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
     * WebSocket connection timeout in milliseconds. Only used by [WsClient].
     * */
    var connectTimeoutMs: Long = 10_000L
        @JvmSynthetic set

    /**
     * Request read timeout in milliseconds, used to expire in-flight requests. Only used by [WsClient].
     * */
    var readTimeoutMs: Long = 30_000L
        @JvmSynthetic set

    /**
     * Client to use for making JSON-RPC requests. If not set, a default client will be used.
     * */
    fun client(client: KtorHttpClient) = apply { this.client = client }

    /**
     * Headers to include with each RPC request. Can be used to set authorization headers, etc...
     * */
    fun requestHeaders(headers: Map<String, String>) = apply { this.requestHeaders = headers }

    /**
     * If true, automatically resubscribes existing subscription streams on WebSocket reconnection.
     * If false, closes the streams instead, allowing consumers to handle resubscription explicitly.
     * Default is true (auto-resubscription is enabled).
     *
     * Only used by [WsClient].
     * */
    fun resubscribeOnReconnect(resubscribe: Boolean) = apply { this.resubscribeOnReconnect = resubscribe }

    /** WebSocket connection timeout in milliseconds. Only used by [WsClient]. */
    fun connectTimeoutMs(ms: Long) = apply { this.connectTimeoutMs = ms }

    /** Request read timeout in milliseconds. Only used by [WsClient]. */
    fun readTimeoutMs(ms: Long) = apply { this.readTimeoutMs = ms }

    companion object {
        private val DEFAULT_CLIENT by lazy {
            KtorHttpClient(CIO) {
                install(WebSockets) { pingIntervalMillis = 10_000L }
            }
        }

        inline operator fun invoke(builder: RpcClientConfig.() -> Unit): RpcClientConfig {
            return RpcClientConfig().apply(builder)
        }
    }
}
