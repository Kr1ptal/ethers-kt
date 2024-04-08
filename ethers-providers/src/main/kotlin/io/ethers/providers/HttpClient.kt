package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
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
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

/**
 * Http client implementation for RPC request submission and results parsing.
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
    private val requestId = AtomicLong(0)
    private val headers = Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build()

    override fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean> {
        val ret = CompletableFuture<Boolean>()

        val body = batch.toRequestBody()
        val call = client.newCall(Request.Builder().url(httpUrl).headers(headers).post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LOG.err(e) { "Error sending batch request" }

                // complete all requests and the batch future
                val response = getResponseFromException(e)
                for (i in batch.responses.indices) {
                    batch.responses[i].complete(response)
                }

                ret.complete(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = it.body
                        if (responseBody == null) {
                            // complete all requests and the batch future
                            for (i in batch.responses.indices) {
                                batch.responses[i].complete(ERROR_NO_RESPONSE)
                            }

                            ret.complete(false)
                            return
                        }

                        var stream = responseBody.byteStream()
                        LOG.trc {
                            // reading from response body consumes it, so we need to create a new one
                            val arr = stream.readAllBytes()
                            stream = BufferedInputStream(ByteArrayInputStream(arr))
                            "Response: ${String(arr)}"
                        }

                        if (!it.isSuccessful) {
                            // complete all requests and the batch future
                            val error = RpcError(RpcError.CODE_CALL_FAILED, it.message, String(stream.readAllBytes()))
                            val failure = failure(error)

                            LOG.err { "Batch request failed: $error" }

                            for (i in batch.responses.indices) {
                                batch.responses[i].complete(failure)
                            }

                            ret.complete(false)
                            return
                        }

                        // TODO per the specification, json-rpc batch responses can be returned in any order
                        Jackson.MAPPER.createAndInitParser(stream).use { p ->
                            var index = 0
                            p.forEachArrayElement {
                                var result: Result<*, RpcError>? = null

                                p.forEachObjectField { field ->
                                    when (field) {
                                        "id" -> {}
                                        "jsonrpc" -> {}
                                        "result" -> {
                                            result = success(batch.requests[index].resultDecoder.apply(p))
                                        }

                                        "error" -> {
                                            result = failure(Jackson.MAPPER.readValue(p, RpcError::class.java))
                                        }
                                    }
                                }

                                if (result == null) {
                                    batch.responses[index].complete(ERROR_INVALID_RESPONSE)
                                    return
                                }

                                batch.responses[index].complete(result)

                                index++
                            }
                        }

                        ret.complete(true)
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing batch response" }
                        val rpcResponse = getResponseFromException(e)

                        // complete all requests and the batch future
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
        resultDecoder: Function<JsonParser, T>,
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
                        val responseBody = it.body
                        if (responseBody == null) {
                            ret.complete(ERROR_NO_RESPONSE)
                            return
                        }

                        var stream = responseBody.byteStream()
                        LOG.trc {
                            // reading from response body consumes it, so we need to create a new one
                            val arr = stream.readAllBytes()
                            stream = BufferedInputStream(ByteArrayInputStream(arr))
                            "Response: ${String(arr)}"
                        }

                        if (!it.isSuccessful) {
                            val error = RpcError(RpcError.CODE_CALL_FAILED, it.message, String(stream.readAllBytes()))
                            LOG.err { "Call failed for method=$method, params=${params.contentToString()}: $error" }

                            ret.complete(failure(error))
                            return
                        }

                        Jackson.MAPPER.createAndInitParser(stream).use { p ->
                            var result: Result<T, RpcError>? = null
                            p.forEachObjectField { field ->
                                when (field) {
                                    "id" -> {}
                                    "jsonrpc" -> {}
                                    "result" -> result = success(resultDecoder.apply(p))
                                    "error" -> {
                                        result = failure(Jackson.MAPPER.readValue(p, RpcError::class.java))
                                    }
                                }
                            }

                            if (result == null) {
                                ret.complete(ERROR_INVALID_RESPONSE)
                                return
                            }

                            ret.complete(result)
                        }
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

    private fun BatchRpcRequest.toRequestBody(): RequestBody {
        val output = DirectByteArrayOutputStream()
        output.use { out ->
            val gen = Jackson.MAPPER.createGenerator(out)

            gen.use {
                it.writeStartArray()
                for (i in requests.indices) {
                    val call = requests[i]
                    it.writeJsonRpcRequest(call.method, requestId.getAndIncrement(), call.params)
                }
                it.writeEndArray()
            }
        }

        LOG.trc { "Request: ${String(output.toByteArray())}" }
        return output.internalBuffer.toRequestBody(JSON_MEDIA_TYPE, byteCount = output.size())
    }

    private fun createJsonRpcRequestBody(method: String, params: Array<*>): RequestBody {
        val output = DirectByteArrayOutputStream()

        output.use { out ->
            val gen = Jackson.MAPPER.createGenerator(out)
            gen.use { it.writeJsonRpcRequest(method, requestId.getAndIncrement(), params) }
        }

        LOG.trc { "Request: ${String(output.toByteArray())}" }
        return output.internalBuffer.toRequestBody(JSON_MEDIA_TYPE, byteCount = output.size())
    }

    private class DirectByteArrayOutputStream : ByteArrayOutputStream() {
        // contains trailing zeros since it's unlikely that the buffer will be exactly the right size
        val internalBuffer: ByteArray
            get() = buf
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        internal val ERROR_INVALID_RESPONSE = failure(
            RpcError(
                RpcError.CODE_INVALID_RESPONSE,
                "No 'result' or 'error' fields in response",
                null,
            ),
        )

        internal val ERROR_NO_RESPONSE = failure(
            RpcError(
                RpcError.CODE_NO_RESPONSE,
                "Response body is null",
                null,
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
