package io.ethers.providers

import io.channels.core.ChannelReceiver
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.json.JsonElement
import io.ethers.core.success
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.trc
import io.ethers.providers.types.BatchRpcRequest
import kotlinx.atomicfu.atomic
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * [JsonRpcClient] implementation via HTTP transport. Supports both single and batch requests. Subscriptions are not
 * supported.
 */
class HttpClient(
    url: String,
    private val client: OkHttpClient,
    headers: Map<String, String> = emptyMap(),
) : JsonRpcClient {
    @JvmOverloads
    constructor(url: String, config: RpcClientConfig = RpcClientConfig()) : this(
        url,
        config.client!!,
        config.requestHeaders,
    )

    private val LOG = getLogger()
    private val httpUrl = url.toHttpUrl()
    private val requestId = atomic(1L)
    private val headers = Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build()

    override fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean> {
        batch.markAsSent()

        if (batch.isEmpty) {
            return CompletableFuture.completedFuture(true)
        }

        val ret = CompletableFuture<Boolean>()

        val (body, requestIndexPerId) = batch.toRequestBody()
        val call = client.newCall(Request.Builder().url(httpUrl).headers(headers).post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LOG.err(e) { "Error sending batch request" }

                val response = getResponseFromException(e)
                for (i in batch.responses.indices) {
                    batch.responses[i].complete(response)
                }

                ret.complete(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        var text = it.body.string()
                        LOG.trc { "Batch response: ${text.removeSuffix("\n")}" }

                        if (!it.isSuccessful) {
                            // first, try to decode a JSON response
                            try {
                                val parsed = Kotlinx.DEFAULT.parseToJsonElement(text)
                                for (element in parsed.jsonArray) {
                                    val obj = element.jsonObject
                                    var index = -1
                                    val result = obj.decodeNextResult { id ->
                                        index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                                        batch.requests[index].resultDecoder
                                    }
                                    batch.responses[index].complete(result)
                                }
                                ret.complete(true)
                                return
                            } catch (_: Exception) {
                            }

                            // second, if decoding fails, return the response as a message and complete all requests
                            val message = "HTTP ${it.code}: ${it.message}"
                            val data = JsonElement(JsonPrimitive(text).toString())
                            val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                            val failure = failure(error)

                            LOG.err { "Batch request failed: $error" }

                            for (i in batch.responses.indices) {
                                batch.responses[i].complete(failure)
                            }

                            ret.complete(false)
                            return
                        }

                        val parsed = Kotlinx.DEFAULT.parseToJsonElement(text)
                        for (element in parsed.jsonArray) {
                            val obj = element.jsonObject
                            var index = -1
                            val result = obj.decodeNextResult { id ->
                                index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                                batch.requests[index].resultDecoder
                            }
                            batch.responses[index].complete(result)
                        }

                        ret.complete(true)
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing batch response" }
                        val rpcResponse = getResponseFromException(e)

                        for (i in batch.responses.indices) {
                            batch.responses[i].complete(rpcResponse)
                        }

                        ret.complete(false)
                        return
                    }
                }
            }
        })

        return ret
    }

    override fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): CompletableFuture<Result<T, RpcError>> {
        val ret = CompletableFuture<Result<T, RpcError>>()

        val body = createJsonRpcRequestBody(method, params)
        val call = client.newCall(Request.Builder().url(httpUrl).headers(headers).post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LOG.err(e) { "Error sending request for method=$method, params=${params.contentToString()}" }

                ret.complete(getResponseFromException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val text = it.body.string()
                        LOG.trc { "Response: ${text.removeSuffix("\n")}" }

                        if (!it.isSuccessful) {
                            // first, try to decode a JSON response
                            try {
                                val obj = Kotlinx.DEFAULT.parseToJsonElement(text).jsonObject
                                ret.complete(obj.decodeNextResult { resultDecoder })
                                return
                            } catch (_: Exception) {
                            }

                            // second, if decoding fails, return the response as a message
                            val message = "HTTP ${it.code}: ${it.message}"
                            val data = JsonElement(JsonPrimitive(text).toString())
                            val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                            LOG.err { "Call failed for method=$method, params=${params.contentToString()}: $error" }

                            ret.complete(failure(error))
                            return
                        }

                        val obj = Kotlinx.DEFAULT.parseToJsonElement(text).jsonObject
                        ret.complete(obj.decodeNextResult { resultDecoder })
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing response for method=$method, params=${params.contentToString()}" }

                        ret.complete(getResponseFromException(e))
                        return
                    }
                }
            }
        })

        return ret
    }

    private fun <T> JsonObject.decodeNextResult(getDecoder: (id: Long) -> (KJsonElement) -> T): Result<T, RpcError> {
        val id = this["id"]?.jsonPrimitive?.long ?: return ERROR_NO_ID_RESPONSE
        val resultEl = this["result"]
        val errorEl = this["error"]

        return when {
            resultEl != null -> success(getDecoder(id)(resultEl))
            errorEl != null -> {
                getDecoder(id)
                failure(RpcError.fromJsonObject(errorEl.jsonObject))
            }
            else -> {
                getDecoder(id)
                ERROR_INVALID_RESPONSE
            }
        }
    }

    override fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): CompletableFuture<Result<ChannelReceiver<T>, RpcError>> {
        return CompletableFuture.completedFuture(ERROR_SUBSCRIPTION_UNSUPPORTED)
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun BatchRpcRequest.toRequestBody(): Pair<RequestBody, HashMap<Long, Int>> {
        val requestIndexPerId = HashMap<Long, Int>(requests.size, 1.0F)

        val sb = StringBuilder(requests.size * BYTE_BUFFER_DEFAULT_SIZE)
        sb.append('[')
        for (i in requests.indices) {
            if (i > 0) sb.append(',')
            val call = requests[i]
            val id = requestId.getAndIncrement()
            sb.append(buildJsonRpcRequest(call.method, id, call.params))
            requestIndexPerId[id] = i
        }
        sb.append(']')

        val json = sb.toString()
        LOG.trc { "Request: $json" }
        return json.toByteArray().toRequestBody(JSON_MEDIA_TYPE) to requestIndexPerId
    }

    private fun createJsonRpcRequestBody(method: String, params: Array<*>): RequestBody {
        val json = buildJsonRpcRequest(method, requestId.getAndIncrement(), params)
        LOG.trc { "Request: $json" }
        return json.toByteArray().toRequestBody(JSON_MEDIA_TYPE)
    }

    companion object {
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
