package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.Jackson
import io.ethers.core.Jackson.createAndInitParser
import io.ethers.core.forEachArrayElement
import io.ethers.core.forEachObjectField
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.trc
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcResponse
import okhttp3.Call
import okhttp3.Callback
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
class HttpClient @JvmOverloads constructor(
    url: String,
    private val client: OkHttpClient = OkHttpClient(),
) : JsonRpcClient {
    private val LOG = getLogger()
    private val httpUrl = url.toHttpUrl()
    private val requestId = AtomicLong(0)

    override fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean> {
        val ret = CompletableFuture<Boolean>()

        val body = batch.toRequestBody()
        val call = client.newCall(Request.Builder().url(httpUrl).post(body).build())

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
                                batch.responses[i].complete(RESPONSE_NO_RESPONSE_BODY)
                            }

                            ret.complete(false)
                            return
                        }

                        var stream = responseBody.byteStream()
                        LOG.trc {
                            // reading from response body consumes it, so we need to create a new one
                            val arr = stream.readAllBytes()
                            stream = BufferedInputStream(ByteArrayInputStream(arr))
                            "Response: ${String(arr)}}"
                        }

                        // TODO per the specification, json-rpc batch responses can be returned in any order
                        Jackson.MAPPER.createAndInitParser(stream).use { p ->
                            var index = 0
                            p.forEachArrayElement {
                                var result: RpcResponse<*>? = null

                                p.forEachObjectField { field ->
                                    when (field) {
                                        "id" -> {}
                                        "jsonrpc" -> {}
                                        "result" -> {
                                            result = RpcResponse.result(batch.requests[index].resultDecoder.apply(p))
                                        }

                                        "error" -> {
                                            val err = Jackson.MAPPER.readValue(p, RpcResponse.RpcError::class.java)
                                            result = RpcResponse.error<Any>(err)
                                        }
                                    }
                                }

                                if (result == null) {
                                    batch.responses[index].complete(RESPONSE_NO_RESULT_OR_ERROR)
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
    ): CompletableFuture<RpcResponse<T>> {
        val ret = CompletableFuture<RpcResponse<T>>()

        val body = createJsonRpcRequestBody(method, params)
        val call = client.newCall(Request.Builder().url(httpUrl).post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LOG.err(e) { "Error sending request for method=$method, params=${params.contentToString()}" }

                @Suppress("UNCHECKED_CAST")
                ret.complete(getResponseFromException(e) as RpcResponse<T>)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = it.body
                        if (responseBody == null) {
                            @Suppress("UNCHECKED_CAST")
                            ret.complete(RESPONSE_NO_RESPONSE_BODY as RpcResponse<T>)
                            return
                        }

                        var stream = responseBody.byteStream()
                        LOG.trc {
                            // reading from response body consumes it, so we need to create a new one
                            val arr = stream.readAllBytes()
                            stream = BufferedInputStream(ByteArrayInputStream(arr))
                            "Response: ${String(arr)}}"
                        }

                        Jackson.MAPPER.createAndInitParser(stream).use { p ->
                            var result: RpcResponse<T>? = null
                            p.forEachObjectField { field ->
                                when (field) {
                                    "id" -> {}
                                    "jsonrpc" -> {}
                                    "result" -> result = RpcResponse.result(resultDecoder.apply(p))
                                    "error" -> {
                                        val err = Jackson.MAPPER.readValue(p, RpcResponse.RpcError::class.java)
                                        result = RpcResponse.error(err)
                                    }
                                }
                            }

                            if (result == null) {
                                @Suppress("UNCHECKED_CAST")
                                ret.complete(RESPONSE_NO_RESULT_OR_ERROR as RpcResponse<T>)
                                return
                            }

                            ret.complete(result)
                        }
                    } catch (e: Exception) {
                        LOG.err(e) { "Error processing response for method=$method, params=${params.contentToString()}" }

                        @Suppress("UNCHECKED_CAST")
                        ret.complete(getResponseFromException(e) as RpcResponse<T>)
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

        internal val RESPONSE_NO_RESULT_OR_ERROR = RpcResponse.error<Any>(
            CallFailedError("No 'result' or 'error' fields in response", null),
        )

        internal val RESPONSE_NO_RESPONSE_BODY = RpcResponse.error<Any>(
            CallFailedError("Response body is null", null),
        )

        internal val RESPONSE_CALL_TIMEOUT = RpcResponse.error<Any>(CallTimeoutError)

        private fun getResponseFromException(e: Exception): RpcResponse<*> {
            val msg = e.message
            return when {
                msg != null && msg.contains("timeout") -> RESPONSE_CALL_TIMEOUT
                else -> RpcResponse.error<Any>(CallFailedError(msg ?: "call failed", e))
            }
        }
    }
}
