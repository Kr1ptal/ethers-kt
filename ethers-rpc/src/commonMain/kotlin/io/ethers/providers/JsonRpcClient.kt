package io.ethers.providers

import io.channels.core.ChannelReceiver
import io.ethers.core.FastHex
import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcParams
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

interface JsonRpcClient : AutoCloseable {
    /**
     * Execute an RPC request without blocking the calling thread.
     *
     * Any [kotlinx.serialization.KSerializer] is a [DeserializationStrategy], so the usual `T.serializer()`,
     * `ListSerializer(...)` and `.nullable` serializers can be passed directly.
     *
     * @param method RPC function name
     * @param params RPC function parameters
     * @param resultDeserializer decodes the JSON result into object [T]
     */
    suspend fun <T> request(
        method: String,
        params: RpcParams,
        resultDeserializer: DeserializationStrategy<T>,
    ): Result<T, RpcError>

    /**
     * Execute a [batch] of RPC requests without blocking the calling thread.
     *
     * An implementation sends each of [BatchRpcRequest.requests] and completes it with [BatchRpcRequest.complete].
     * A client that wraps another can forward a batch, or a new one it builds, with [BatchRpcRequest.sendWith].
     */
    suspend fun requestBatch(batch: BatchRpcRequest): Boolean

    /**
     * Subscribe to a stream using the client's configured protocol, if the client supports it.
     *
     * @param params the stream name followed by its arguments
     * @param resultDeserializer decodes each JSON event into object [T]
     */
    suspend fun <T : Any> subscribe(
        params: RpcParams,
        resultDeserializer: DeserializationStrategy<T>,
    ): Result<ChannelReceiver<T>, RpcError>
}

/**
 * Serializer for byte payloads returned by Ethereum JSON-RPC (`eth_getCode`, `eth_call`, …) as 0x-prefixed hex
 * strings.
 *
 * kotlinx' default [ByteArray] serializer expects a JSON array of numbers and would throw at runtime, so byte
 * results have to opt into this serializer explicitly.
 */
object HexByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.ethers.providers.HexByteArray", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ByteArray {
        val text = decoder.decodeString()
        return if (text.isEmpty() || text == "0x" || text == "0X") ByteArray(0) else FastHex.decode(text)
    }

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(FastHex.encodeWithPrefix(value))
    }
}

/**
 * Internal JSON-RPC error, returned when the RPC call fails.
 *
 * @param httpStatus status code of the HTTP response that carried this error, set only when it was outside the 2xx
 * range. Null for errors the server returned with a successful status, for WebSocket calls, and for failures that got
 * no response at all.
 * @param retryAfterMs how long the server asked to wait before retrying, in milliseconds, taken from the `Retry-After`
 * header of a response outside the 2xx range. Null if the response did not include one or it could not be parsed.
 * @param rateLimit set when the client's [RateLimitPolicy] found that this error is a rate limit, with the wait it
 * found, if any. Null for any other error.
 */
data class RpcError @JvmOverloads constructor(
    val code: Int,
    override val message: String,
    val data: JsonElement? = null,
    override val cause: Exception? = null,
    val httpStatus: Int? = null,
    val retryAfterMs: Long? = null,
    val rateLimit: RateLimit? = null,
) : ThrowableError {
    // [message] is the JSON-RPC error message, which on its own omits the code that identifies the failure
    override fun toException(): RuntimeException {
        return ThrowableError.Exception(this, "$message (code: $code)")
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

    /**
     * The HTTP response that carried this error had status 429 (Too Many Requests). It says nothing about errors the
     * server reports in a JSON-RPC response, which differ per chain and provider: see [isRateLimited] for those.
     * */
    val isHttpRateLimited get() = httpStatus == HTTP_TOO_MANY_REQUESTS

    /**
     * The client's [RateLimitPolicy] found that this error is a rate limit. [rateLimit] holds the wait it found.
     * */
    val isRateLimited get() = rateLimit != null

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429

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
            val data = if (dataEl == null || dataEl is JsonNull) null else dataEl
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
     * Decides which errors are rate limits and how long to wait before retrying them, setting [RpcError.rateLimit].
     *
     * If null, the client uses its own default: [RateLimitPolicy.HTTP] for an [HttpClient] or [WsClient] created
     * directly, or the chain's policy for a client made by a chain's provider builder.
     * */
    var rateLimitPolicy: RateLimitPolicy? = null
        @JvmSynthetic set

    /** Subscription protocol rules. Defaults to Ethereum. Only used by [WsClient]. */
    var subscriptionDescriptor: SubscriptionDescriptor = SubscriptionDescriptor.ETHEREUM
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

    /** Decides which errors are rate limits. See [rateLimitPolicy]. */
    fun rateLimitPolicy(policy: RateLimitPolicy?) = apply { this.rateLimitPolicy = policy }

    /** Subscription protocol rules. Only used by [WsClient]. */
    fun subscriptionDescriptor(descriptor: SubscriptionDescriptor) = apply { this.subscriptionDescriptor = descriptor }

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

    /**
     * A new config with the same values, so a builder can change some of them without changing a config the caller
     * may also use elsewhere.
     * */
    fun copy(): RpcClientConfig {
        val source = this
        return RpcClientConfig {
            client = source.client
            requestHeaders = source.requestHeaders
            rateLimitPolicy = source.rateLimitPolicy
            subscriptionDescriptor = source.subscriptionDescriptor
            resubscribeOnReconnect = source.resubscribeOnReconnect
            connectTimeoutMs = source.connectTimeoutMs
            readTimeoutMs = source.readTimeoutMs
        }
    }

    companion object {
        private val DEFAULT_CLIENT by lazy {
            KtorHttpClient(defaultHttpClientEngineFactory) {
                install(WebSockets) { pingIntervalMillis = 10_000L }
            }
        }

        inline operator fun invoke(builder: RpcClientConfig.() -> Unit): RpcClientConfig {
            return RpcClientConfig().apply(builder)
        }
    }
}
