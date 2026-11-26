package io.ethers.providers

import io.channels.core.ChannelReceiver
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.trc
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcParams
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.jvm.JvmOverloads
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * [JsonRpcClient] implementation via HTTP transport. Supports both single and batch requests. Subscriptions are not
 * supported.
 */
class HttpClient(
    url: String,
    client: KtorHttpClient,
    private val requestHeaders: Map<String, String> = emptyMap(),
) : JsonRpcClient {
    /**
     * A child of the supplied client, sharing its engine and configuration but owning its own lifecycle.
     *
     * ktor ref-counts engine users, so [close] tears down only this client: the caller's client, or
     * [RpcClientConfig]'s shared default, and the connection pool underneath, all survive.
     */
    private val client = client.config { }
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
                            batch.requests[index].resultDeserializer
                        }
                        batch.complete(index, result)
                    }
                    // calls the response left out fail with the HTTP error below
                    if (!batch.hasUnanswered()) return true
                } catch (_: Exception) {
                }

                val message = "HTTP ${response.status.value}: ${response.status.description}"
                val data = JsonPrimitive(text)
                val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                LOG.err { "Batch request failed: $error" }

                for (i in batch.responses.indices) {
                    batch.complete(i, failure(error))
                }
                return false
            }

            val parsed = Kotlinx.DEFAULT.parseToJsonElement(text)
            for (element in parsed.jsonArray) {
                val obj = element.jsonObject
                var index = -1
                val result = obj.decodeNextResult { id ->
                    index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                    batch.requests[index].resultDeserializer
                }
                batch.complete(index, result)
            }
            !batch.failUnanswered(missingResponse(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.err(e) { "Error processing batch response" }
            val rpcResponse = getResponseFromException(e)
            for (i in batch.responses.indices) {
                batch.complete(i, rpcResponse)
            }
            false
        }
    }

    /** Decodes the response straight into [T], without first parsing it into a [KJsonElement] tree. */
    override suspend fun <T> request(
        method: String,
        params: RpcParams,
        resultDeserializer: DeserializationStrategy<T>,
    ): Result<T, RpcError> = execute(method, params) { text ->
        Kotlinx.DEFAULT.decodeFromString(RpcResponseDeserializer(resultDeserializer), text)
    }

    /** Send a single request and turn its response body into a result with [decode]. */
    private suspend inline fun <T> execute(
        method: String,
        params: RpcParams,
        decode: (text: String) -> Result<T, RpcError>,
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
                    return decode(text)
                } catch (_: Exception) {
                }

                val message = "HTTP ${response.status.value}: ${response.status.description}"
                val data = JsonPrimitive(text)
                val error = RpcError(RpcError.CODE_CALL_FAILED, message, data)
                LOG.err { "Call failed for method=$method, params=$params: $error" }
                return failure(error)
            }

            decode(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.err(e) { "Error processing response for method=$method, params=$params" }
            getResponseFromException(e)
        }
    }

    private fun <T> JsonObject.decodeNextResult(
        getDeserializer: (id: Long) -> DeserializationStrategy<T>,
    ): Result<T, RpcError> {
        val id = this["id"]?.jsonPrimitive?.long ?: return ERROR_NO_ID_RESPONSE
        val resultEl = this["result"]
        val errorEl = this["error"]

        return when {
            resultEl != null -> success(Kotlinx.DEFAULT.decodeFromJsonElement(getDeserializer(id), resultEl))
            errorEl != null -> {
                getDeserializer(id)
                failure(RpcError.fromJsonObject(errorEl.jsonObject))
            }
            else -> {
                getDeserializer(id)
                ERROR_INVALID_RESPONSE
            }
        }
    }

    override suspend fun <T : Any> subscribe(
        params: RpcParams,
        resultDeserializer: DeserializationStrategy<T>,
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
            sb.append(buildJsonRpcRequest(call.method, id, encodeParams(i)))
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

        /** The result of a batched call that the batch response, [text], did not answer. */
        internal fun missingResponse(text: String) = failure(
            RpcError(RpcError.CODE_INVALID_RESPONSE, "No response to this call in the batch response", JsonPrimitive(text)),
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
