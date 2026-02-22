package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
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
import kotlinx.atomicfu.atomic
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * [JsonRpcClient] implementation via HTTP transport. Supports both single and batch requests. Subscriptions are not
 * supported.
 */
class HttpClient(
    url: String,
    private val client: OkHttpClient,
    headers: Map<String, String> = emptyMap(),
    private val jsonMapper: JsonMapper = Jackson.MAPPER,
) : JsonRpcClient {
    @JvmOverloads
    constructor(url: String, config: RpcClientConfig = RpcClientConfig()) : this(
        url,
        config.client!!,
        config.requestHeaders,
        config.jsonMapper,
    )

    private val LOG = getLogger()
    private val httpUrl = url.toHttpUrl()
    private val requestId = atomic(1L)
    private val headers = Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build()

    override fun requestBatch(batch: BatchRpcRequest, callback: ResultCallback<Boolean>) {
        batch.markAsSent()

        if (batch.isEmpty) {
            callback.complete(true)
            return
        }

        val (body, requestIndexPerId) = batch.toRequestBody()
        val call = client.newCall(Request.Builder().url(httpUrl).headers(headers).post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LOG.err(e) { "Error sending batch request" }

                val response = getResponseFromException(e)
                for (i in batch.callbacks.indices) {
                    batch.callbacks[i].complete(response)
                }

                callback.complete(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        var stream = it.body.byteStream()
                        LOG.trc {
                            val arr = stream.readBytes()
                            stream = ByteArrayInputStream(arr)
                            "Batch response: ${String(arr)}".removeSuffix("\n")
                        }

                        if (!it.isSuccessful) {
                            val bytes = stream.readBytes()

                            try {
                                ByteArrayInputStream(bytes).useJsonParser {
                                    val parser = this

                                    parser.forEachArrayElement {
                                        var index = -1
                                        val result = parser.decodeNextResult { id ->
                                            index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                                            batch.requests[index].resultDecoder
                                        }

                                        batch.callbacks[index].complete(result)
                                    }
                                }

                                callback.complete(true)
                                return
                            } catch (_: Exception) {
                            }

                            val message = "HTTP ${it.code}: ${it.message}"
                            val data = jsonMapper.valueToTree<JsonNode>(String(bytes))
                            val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                            val failure = failure(error)

                            LOG.err { "Batch request failed: $error" }

                            for (i in batch.callbacks.indices) {
                                batch.callbacks[i].complete(failure)
                            }

                            callback.complete(false)
                            return
                        }

                        stream.useJsonParser {
                            val parser = this

                            parser.forEachArrayElement {
                                var index = -1
                                val result = parser.decodeNextResult { id ->
                                    index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                                    batch.requests[index].resultDecoder
                                }

                                batch.callbacks[index].complete(result)
                            }
                        }

                        callback.complete(true)
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing batch response" }
                        val rpcResponse = getResponseFromException(e)

                        for (i in batch.callbacks.indices) {
                            batch.callbacks[i].complete(rpcResponse)
                        }

                        callback.complete(false)
                        return
                    }
                }
            }
        })
    }

    override fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (JsonParser) -> T,
        callback: ResultCallback<Result<T, RpcError>>,
    ) {
        val body = createJsonRpcRequestBody(method, params)
        val call = client.newCall(Request.Builder().url(httpUrl).headers(headers).post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LOG.err(e) { "Error sending request for method=$method, params=${params.contentToString()}" }

                callback.complete(getResponseFromException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        var stream = it.body.byteStream()
                        LOG.trc {
                            val arr = stream.readBytes()
                            stream = ByteArrayInputStream(arr)
                            "Response: ${String(arr)}".removeSuffix("\n")
                        }

                        if (!it.isSuccessful) {
                            val bytes = stream.readBytes()

                            try {
                                callback.complete(ByteArrayInputStream(bytes).decodeResult(resultDecoder))
                                return
                            } catch (_: Exception) {
                            }

                            val message = "HTTP ${it.code}: ${it.message}"
                            val data = jsonMapper.valueToTree<JsonNode>(String(bytes))
                            val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                            LOG.err { "Call failed for method=$method, params=${params.contentToString()}: $error" }

                            callback.complete(failure(error))
                            return
                        }

                        callback.complete(stream.decodeResult(resultDecoder))
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing response for method=$method, params=${params.contentToString()}" }

                        callback.complete(getResponseFromException(e))
                        return
                    }
                }
            }
        })
    }

    private fun <T> InputStream.decodeResult(decoder: (JsonParser) -> T): Result<T, RpcError> {
        return useJsonParser { decodeNextResult { decoder } }
    }

    private inline fun <T> JsonParser.decodeNextResult(getDecoder: (id: Long) -> (JsonParser) -> T): Result<T, RpcError> {
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
                        result = success(getDecoder(id)(this))
                    }
                }

                "error" -> {
                    // just call to pass the ID
                    getDecoder(id)
                    result = failure(jsonMapper.readValue(this, RpcError::class.java))
                }
            }
        }

        if (id == -1L) {
            return ERROR_NO_ID_RESPONSE
        }

        buffer?.asParser()?.use {
            it.nextToken()
            result = success(getDecoder(id)(it))
        }

        return result ?: ERROR_INVALID_RESPONSE
    }

    private inline fun <R> InputStream.useJsonParser(action: JsonParser.() -> R): R {
        return jsonMapper.createAndInitParser(this).use(action)
    }

    override fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (JsonParser) -> T,
        callback: ResultCallback<Result<ChannelReceiver<T>, RpcError>>,
    ) {
        callback.complete(ERROR_SUBSCRIPTION_UNSUPPORTED)
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun BatchRpcRequest.toRequestBody(): Pair<RequestBody, HashMap<Long, Int>> {
        val requestIndexPerId = HashMap<Long, Int>(requests.size, 1.0F)

        val output = DirectByteArrayOutputStream(requests.size * BYTE_BUFFER_DEFAULT_SIZE)
        output.use { out ->
            val gen = jsonMapper.createGenerator(out)

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
        return output.internalBuffer.toRequestBody(JSON_MEDIA_TYPE, byteCount = output.size()) to requestIndexPerId
    }

    private fun createJsonRpcRequestBody(method: String, params: Array<*>): RequestBody {
        val output = DirectByteArrayOutputStream(BYTE_BUFFER_DEFAULT_SIZE)

        output.use { out ->
            val gen = jsonMapper.createGenerator(out)
            gen.use { it.writeJsonRpcRequest(method, requestId.getAndIncrement(), params) }
        }

        LOG.trc { "Request: ${String(output.toByteArray())}" }
        return output.internalBuffer.toRequestBody(JSON_MEDIA_TYPE, byteCount = output.size())
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
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

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

        private fun getResponseFromException(e: Exception): Result<Nothing, RpcError> {
            val msg = e.message
            return when {
                msg != null && msg.contains("timeout") -> ERROR_CALL_TIMEOUT
                else -> failure(RpcError(RpcError.CODE_CALL_FAILED, msg ?: "call failed", null, e))
            }
        }
    }
}
