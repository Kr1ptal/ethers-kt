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
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.isSuccess
import io.ktor.util.date.GMTDate
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

        val (body, requestIndexPerId) = batch.toRequestBody()

        return try {
            val response = client.post(httpUrl) {
                headers { requestHeaders.forEach { (k, v) -> append(k, v) } }
                setBody(ByteArrayContent(body, ContentType.Application.Json))
            }
            val text = response.bodyAsText()
            LOG.trc { "Batch response: ${text.removeSuffix("\n")}" }

            if (!response.status.isSuccess()) {
                val status = response.status.value
                val retryAfterMs = response.retryAfterMs()
                try {
                    val parsed = Kotlinx.DEFAULT.parseToJsonElement(text)
                    for (element in parsed.jsonArray) {
                        val obj = element.jsonObject
                        var index = -1
                        val result = obj.decodeNextResult { id ->
                            index = requestIndexPerId[id] ?: throw Exception("Invalid response ID: $id")
                            batch.requests[index].resultDeserializer
                        }
                        batch.complete(index, result.mapError { it.withHttpStatus(status, retryAfterMs) })
                    }
                    // calls the response left out fail with the HTTP error below
                    if (!batch.hasUnanswered()) return true
                } catch (_: Exception) {
                }

                val error = httpError(response, JsonPrimitive(text), null, retryAfterMs)
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
        val body = buildJsonRpcRequest(method, requestId.getAndIncrement(), params)
        LOG.trc { "Request: ${body.decodeToString()}" }

        return try {
            val response = client.post(httpUrl) {
                headers { requestHeaders.forEach { (k, v) -> append(k, v) } }
                // the request's UTF-8 bytes, sent as they are: a String body would be copied into new bytes by Ktor
                setBody(ByteArrayContent(body, ContentType.Application.Json))
            }
            val text = response.bodyAsText()
            LOG.trc { "Response: ${text.removeSuffix("\n")}" }

            if (!response.status.isSuccess()) {
                val status = response.status.value
                val retryAfterMs = response.retryAfterMs()
                try {
                    return decode(text).mapError { it.withHttpStatus(status, retryAfterMs) }
                } catch (_: Exception) {
                }

                val error = httpError(response, JsonPrimitive(text), null, retryAfterMs)
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

    private fun BatchRpcRequest.toRequestBody(): Pair<ByteArray, HashMap<Long, Int>> {
        // encoded before taking any request ID, so a batch that can't be encoded doesn't consume IDs
        val params = encodeParams()
        val requestIndexPerId = HashMap<Long, Int>(requests.size, 1.0F)

        val body = buildJsonRpcBatch(requests, params) { i ->
            val id = requestId.getAndIncrement()
            requestIndexPerId[id] = i
            id
        }
        LOG.trc { "Request: ${body.decodeToString()}" }
        return body to requestIndexPerId
    }

    companion object {
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

        /**
         * The error for a [response] outside the 2xx range whose body is not a JSON-RPC response. [cause] is the
         * exception ktor threw for it, if the client was configured to throw on such responses.
         */
        private fun httpError(
            response: HttpResponse,
            data: KJsonElement?,
            cause: Exception?,
            retryAfterMs: Long? = response.retryAfterMs(),
        ): RpcError {
            val status = response.status
            val message = "HTTP ${status.value}: ${status.description}"
            return RpcError(RpcError.CODE_CALL_FAILED, message, data, cause, status.value, retryAfterMs)
        }

        private fun RpcError.withHttpStatus(status: Int, retryAfterMs: Long?): RpcError {
            return copy(httpStatus = status, retryAfterMs = retryAfterMs)
        }

        private fun HttpResponse.retryAfterMs(): Long? = parseRetryAfterMs(headers[HttpHeaders.RetryAfter])

        /**
         * Parse a `Retry-After` header [value] into the number of milliseconds to wait. The header holds either a
         * number of seconds or an HTTP date, which is measured from [nowMs]. A date in the past means no wait.
         * Returns null if [value] is null or neither form.
         */
        internal fun parseRetryAfterMs(value: String?, nowMs: Long = GMTDate().timestamp): Long? {
            val text = value?.trim()
            if (text.isNullOrEmpty()) return null

            val seconds = text.toLongOrNull()
            if (seconds != null) {
                return when {
                    seconds < 0 -> null
                    seconds > Long.MAX_VALUE / 1000 -> Long.MAX_VALUE
                    else -> seconds * 1000
                }
            }

            // not allowed by RFC 9110, but some servers send fractional seconds
            val fractional = text.toDoubleOrNull()
            if (fractional != null) {
                return if (fractional.isFinite() && fractional >= 0) (fractional * 1000).toLong() else null
            }

            return try {
                (text.fromHttpToGmtDate().timestamp - nowMs).coerceAtLeast(0)
            } catch (_: Exception) {
                null
            }
        }

        private fun getResponseFromException(e: Exception): Result<Nothing, RpcError> {
            val msg = e.message
            return when {
                // thrown instead of returning the response when the client is configured with `expectSuccess`
                e is ResponseException -> failure(httpError(e.response, null, e))
                msg != null && msg.contains("timeout", ignoreCase = true) -> ERROR_CALL_TIMEOUT
                else -> failure(RpcError(RpcError.CODE_CALL_FAILED, msg ?: "call failed", null, e))
            }
        }
    }
}
