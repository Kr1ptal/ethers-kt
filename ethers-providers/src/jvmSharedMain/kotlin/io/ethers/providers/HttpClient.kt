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
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * [JsonRpcClient] implementation via HTTP transport. Supports both single and batch requests. Subscriptions are not
 * supported.
 */
class HttpClient(
    url: String,
    private val client: KtorHttpClient,
    private val requestHeaders: Map<String, String> = emptyMap(),
) : JsonRpcClient {
    @JvmOverloads
    constructor(url: String, config: RpcClientConfig = RpcClientConfig()) : this(
        url,
        config.client!!,
        config.requestHeaders,
    )

    private val LOG = getLogger()
    private val httpUrl = url
    private val requestId = atomic(1L)

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean {
        batch.markAsSent()

        if (batch.isEmpty) {
            return true
        }

        val (json, requestIndexPerId) = batch.toRequestBody()

        return try {
            val response = client.post(httpUrl) {
                contentType(ContentType.Application.Json)
                headers { requestHeaders.forEach { (k, v) -> append(k, v) } }
                setBody(json)
            }
            val text = response.bodyAsText()
            LOG.trc { "Batch response: ${text.removeSuffix("\n")}" }

            if (response.status.value !in 200..299) {
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
                    return true
                } catch (_: Exception) {
                }

                val message = "HTTP ${response.status.value}: ${response.status.description}"
                val data = JsonElement(JsonPrimitive(text).toString())
                val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                LOG.err { "Batch request failed: $error" }

                for (i in batch.responses.indices) {
                    batch.responses[i].complete(failure(error))
                }
                return false
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
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.err(e) { "Error processing batch response" }
            val rpcResponse = getResponseFromException(e)
            for (i in batch.responses.indices) {
                batch.responses[i].complete(rpcResponse)
            }
            false
        }
    }

    override suspend fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): Result<T, RpcError> {
        val json = buildJsonRpcRequest(method, requestId.getAndIncrement(), params)
        LOG.trc { "Request: $json" }

        return try {
            val response = client.post(httpUrl) {
                contentType(ContentType.Application.Json)
                headers { requestHeaders.forEach { (k, v) -> append(k, v) } }
                setBody(json)
            }
            val text = response.bodyAsText()
            LOG.trc { "Response: ${text.removeSuffix("\n")}" }

            if (response.status.value !in 200..299) {
                try {
                    val obj = Kotlinx.DEFAULT.parseToJsonElement(text).jsonObject
                    return obj.decodeNextResult { resultDecoder }
                } catch (_: Exception) {
                }

                val message = "HTTP ${response.status.value}: ${response.status.description}"
                val data = JsonElement(JsonPrimitive(text).toString())
                val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                LOG.err { "Call failed for method=$method, params=${params.contentToString()}: $error" }
                return failure(error)
            }

            val obj = Kotlinx.DEFAULT.parseToJsonElement(text).jsonObject
            obj.decodeNextResult { resultDecoder }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.err(e) { "Error processing response for method=$method, params=${params.contentToString()}" }
            getResponseFromException(e)
        }
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

    override suspend fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): Result<ChannelReceiver<T>, RpcError> {
        return ERROR_SUBSCRIPTION_UNSUPPORTED
    }

    override fun close() {
        client.close()
    }

    private fun BatchRpcRequest.toRequestBody(): Pair<String, HashMap<Long, Int>> {
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
        return json to requestIndexPerId
    }

    companion object {
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

        private fun getResponseFromException(e: Exception): Result<Nothing, RpcError> {
            val msg = e.message
            return when {
                msg != null && msg.contains("timeout", ignoreCase = true) -> ERROR_CALL_TIMEOUT
                else -> failure(RpcError(RpcError.CODE_CALL_FAILED, msg ?: "call failed", null, e))
            }
        }
    }
}
