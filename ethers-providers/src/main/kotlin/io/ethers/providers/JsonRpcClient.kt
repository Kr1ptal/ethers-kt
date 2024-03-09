package io.ethers.providers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.Result
import io.ethers.providers.types.BatchRpcRequest
import okhttp3.OkHttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadFactory
import java.util.function.Function

interface JsonRpcClient {
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
        resultDecoder: Function<JsonParser, T>,
    ): CompletableFuture<Result<T, RpcError>>

    /**
     * Asynchronously execute [batch] of RPC requests.
     */
    fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean>
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
data class RpcError(
    val code: Int,
    val message: String,
    val data: String?,
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
    val isMethodNotFound get() = code == CODE_METHOD_NOT_FOUND

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
     * Factory for creating additional [JsonRpcClient] threads, if needed. By default, a daemon thread is created.
     * */
    var threadFactory: ThreadFactory = ThreadFactory { r -> Thread(r).apply { isDaemon = true } }
        @JvmSynthetic set

    fun client(client: OkHttpClient) = apply { this.client = client }

    fun threadFactory(factory: ThreadFactory) = apply { this.threadFactory = factory }

    companion object {
        private val DEFAULT_CLIENT by lazy { OkHttpClient() }

        inline operator fun invoke(builder: RpcClientConfig.() -> Unit): RpcClientConfig {
            return RpcClientConfig().apply(builder)
        }
    }
}
