package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.util.TokenBuffer
import io.channels.core.ChannelReceiver
import io.ethers.core.Jackson
import io.ethers.core.Jackson.createAndInitParser
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.forEachArrayElement
import io.ethers.core.forEachObjectField
import io.ethers.core.success
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.trc
import io.ethers.providers.types.BatchRpcRequest
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

/**
 * [JsonRpcClient] implementation via HTTP transport. Supports both single and batch requests. Subscriptions are not
 * supported.
 */
class HttpClient(
    private val transport: HttpTransport,
) : JsonRpcClient {
    @JvmOverloads
    constructor(url: String, config: RpcClientConfig = RpcClientConfig()) : this(
        OkHttpTransport(url, config.requestHeaders, config.client!!),
    )

    constructor(
        url: String,
        client: OkHttpClient,
        headers: Map<String, String> = emptyMap(),
    ) : this(OkHttpTransport(url, headers, client))

    private val LOG = getLogger()
    private val requestId = AtomicLong(1)

    override fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean> {
        batch.markAsSent()

        if (batch.isEmpty) {
            return CompletableFuture.completedFuture(true)
        }

        val (body, requestIndexPerId) = batch.toRequestBody()

        return transport.execute(body).thenApply { httpResult ->
            when (httpResult) {
                is HttpResult.Failure -> {
                    LOG.err(httpResult.error) { "Error sending batch request" }

                    // complete all requests and the batch future
                    val response = getResponseFromException(httpResult.error)
                    for (i in batch.responses.indices) {
                        batch.responses[i].complete(response)
                    }

                    false
                }

                is HttpResult.HttpError -> {
                    var stream: InputStream = httpResult.body
                    LOG.trc {
                        val arr = stream.readBytes()
                        stream = ByteArrayInputStream(arr)
                        "Batch response: ${String(arr)}".removeSuffix("\n")
                    }

                    val bytes = stream.readBytes()

                    // first, try to decode a JSON response
                    try {
                        ByteArrayInputStream(bytes).useJsonParser {
                            val parser = this

                            parser.forEachArrayElement {
                                var index = -1
                                val result = parser.decodeNextResult { id ->
                                    index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                                    batch.requests[index].resultDecoder
                                }

                                batch.responses[index].complete(result)
                            }
                        }

                        return@thenApply true
                    } catch (_: Exception) {
                    }

                    // second, if decoding fails, return the response as a message and complete all requests
                    // including the batch future
                    val message = "HTTP ${httpResult.code}: ${httpResult.message}"
                    val data = Jackson.MAPPER.valueToTree<JsonNode>(String(bytes))
                    val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                    val failure = failure(error)

                    LOG.err { "Batch request failed: $error" }

                    for (i in batch.responses.indices) {
                        batch.responses[i].complete(failure)
                    }

                    false
                }

                is HttpResult.Success -> {
                    try {
                        var stream: InputStream = httpResult.body
                        LOG.trc {
                            val arr = stream.readBytes()
                            stream = ByteArrayInputStream(arr)
                            "Batch response: ${String(arr)}".removeSuffix("\n")
                        }

                        stream.useJsonParser {
                            val parser = this

                            parser.forEachArrayElement {
                                var index = -1
                                val result = parser.decodeNextResult { id ->
                                    index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                                    batch.requests[index].resultDecoder
                                }

                                batch.responses[index].complete(result)
                            }
                        }

                        true
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing batch response" }
                        val rpcResponse = getResponseFromException(e)

                        // complete all requests and the batch future
                        for (i in batch.responses.indices) {
                            batch.responses[i].complete(rpcResponse)
                        }

                        false
                    }
                }
            }
        }
    }

    override fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: Function<JsonParser, T>,
    ): CompletableFuture<Result<T, RpcError>> {
        val body = createJsonRpcRequestBody(method, params)

        return transport.execute(body).thenApply { httpResult ->
            when (httpResult) {
                is HttpResult.Failure -> {
                    LOG.err(httpResult.error) { "Error sending request for method=$method, params=${params.contentToString()}" }
                    getResponseFromException(httpResult.error)
                }

                is HttpResult.HttpError -> {
                    var stream: InputStream = httpResult.body
                    LOG.trc {
                        val arr = stream.readBytes()
                        stream = ByteArrayInputStream(arr)
                        "Response: ${String(arr)}".removeSuffix("\n")
                    }

                    val bytes = stream.readBytes()

                    // first, try to decode a JSON response
                    try {
                        return@thenApply ByteArrayInputStream(bytes).decodeResult(resultDecoder)
                    } catch (_: Exception) {
                    }

                    // second, if decoding fails, return the response as a message
                    val message = "HTTP ${httpResult.code}: ${httpResult.message}"
                    val data = Jackson.MAPPER.valueToTree<JsonNode>(String(bytes))
                    val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                    LOG.err { "Call failed for method=$method, params=${params.contentToString()}: $error" }

                    failure(error)
                }

                is HttpResult.Success -> {
                    try {
                        var stream: InputStream = httpResult.body
                        LOG.trc {
                            val arr = stream.readBytes()
                            stream = ByteArrayInputStream(arr)
                            "Response: ${String(arr)}".removeSuffix("\n")
                        }

                        stream.decodeResult(resultDecoder)
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing response for method=$method, params=${params.contentToString()}" }
                        getResponseFromException(e)
                    }
                }
            }
        }
    }

    private fun <T> InputStream.decodeResult(decoder: Function<JsonParser, T>): Result<T, RpcError> {
        return useJsonParser { decodeNextResult { decoder } }
    }

    private inline fun <T> JsonParser.decodeNextResult(getDecoder: (id: Long) -> Function<JsonParser, T>): Result<T, RpcError> {
        var result: Result<T, RpcError>? = null
        var buffer: TokenBuffer? = null

        var id = -1L
        this.forEachObjectField { field ->
            when (field) {
                "id" -> id = this.longValue
                "jsonrpc" -> this.skipChildren()
                "result" -> {
                    if (id == -1L) {
                        buffer = TokenBuffer(this)
                        buffer.copyCurrentStructure(this)
                    } else {
                        result = success(getDecoder(id).apply(this))
                    }
                }

                "error" -> {
                    // just call to pass the ID
                    getDecoder(id)
                    result = failure(Jackson.MAPPER.readValue(this, RpcError::class.java))
                }
            }
        }

        if (id == -1L) {
            return ERROR_NO_ID_RESPONSE
        }

        buffer?.asParser()?.use {
            it.nextToken()
            result = success(getDecoder(id).apply(it))
        }

        return result ?: ERROR_INVALID_RESPONSE
    }

    private inline fun <R> InputStream.useJsonParser(action: JsonParser.() -> R): R {
        return Jackson.MAPPER.createAndInitParser(this).use(action)
    }

    override fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: Function<JsonParser, T>,
    ): CompletableFuture<Result<ChannelReceiver<T>, RpcError>> {
        return CompletableFuture.completedFuture(ERROR_SUBSCRIPTION_UNSUPPORTED)
    }

    override fun close() {
        // no-op
    }

    private fun BatchRpcRequest.toRequestBody(): Pair<ByteArray, HashMap<Long, Int>> {
        val requestIndexPerId = HashMap<Long, Int>(requests.size, 1.0F)

        val output = DirectByteArrayOutputStream(requests.size * BYTE_BUFFER_DEFAULT_SIZE)
        output.use { out ->
            val gen = Jackson.MAPPER.createGenerator(out)

            gen.use {
                it.writeStartArray()
                for (i in requests.indices) {
                    val call = requests[i]
                    val id = requestId.getAndIncrement()
                    it.writeJsonRpcRequest(call.method, id, call.params)
                    requestIndexPerId[id] = i
                }
                it.writeEndArray()
            }
        }

        LOG.trc { "Request: ${String(output.toByteArray())}" }
        return output.toByteArray() to requestIndexPerId
    }

    private fun createJsonRpcRequestBody(method: String, params: Array<*>): ByteArray {
        val output = DirectByteArrayOutputStream(BYTE_BUFFER_DEFAULT_SIZE)

        output.use { out ->
            val gen = Jackson.MAPPER.createGenerator(out)
            gen.use { it.writeJsonRpcRequest(method, requestId.getAndIncrement(), params) }
        }

        LOG.trc { "Request: ${String(output.toByteArray())}" }
        return output.toByteArray()
    }

    private class DirectByteArrayOutputStream(size: Int) : ByteArrayOutputStream(size) {
        /**
         * Return the internal buffer.
         *
         * **NOTE**: Contains trailing zeros since it's unlikely that the buffer will be exactly the right size.
         * */
        val internalBuffer: ByteArray
            get() = buf
    }

    companion object {
        // one of the smallest possible requests, `eth_chainID`, takes 59 bytes. Most requests use more,
        // around 100 bytes, so we use a buffer of 128 bytes to try and avoid reallocations in most cases
        private const val BYTE_BUFFER_DEFAULT_SIZE = 128

        private val ERROR_SUBSCRIPTION_UNSUPPORTED = failure(
            RpcError(
                RpcError.CODE_METHOD_NOT_FOUND,
                "'eth_subscribe' is not supported by HTTP client",
            ),
        )

        internal val ERROR_NO_ID_RESPONSE = failure(
            RpcError(
                RpcError.CODE_INVALID_RESPONSE,
                "Invalid response, field 'id' is missing",
            ),
        )

        internal val ERROR_INVALID_RESPONSE = failure(
            RpcError(
                RpcError.CODE_INVALID_RESPONSE,
                "Invalid response, no 'result' or 'error' fields in response",
            ),
        )

        internal val ERROR_CALL_TIMEOUT = failure(RpcError(RpcError.CODE_CALL_TIMEOUT, "Call timeout", null))

        private fun getResponseFromException(e: Throwable): Result<Nothing, RpcError> {
            val msg = e.message
            return when {
                msg != null && msg.contains("timeout") -> ERROR_CALL_TIMEOUT
                else -> failure(RpcError(RpcError.CODE_CALL_FAILED, msg ?: "call failed", null, e as? Exception))
            }
        }
    }
}
