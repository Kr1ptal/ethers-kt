package io.ethers.aa

import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.toJsonElement
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.channels.core.ChannelReceiver
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement

/**
 * A [JsonRpcClient] that answers from registered handlers instead of the network, and records every request.
 *
 * A handler returns the raw JSON `result`, throws [RpcFailure] to answer with an error, or returns null to defer to
 * the next handler.
 */
class FakeJsonRpcClient : JsonRpcClient {
    private val handlers = ArrayList<(String, List<JsonElement>) -> String?>()
    private val recorded = ArrayList<Request>()

    val requests: List<Request> get() = recorded

    class Request(val method: String, val params: List<JsonElement>)

    class RpcFailure(val error: RpcError) : RuntimeException(error.message)

    fun requests(method: String): List<Request> = recorded.filter { it.method == method }

    /**
     * Answer every [method] request with [block].
     */
    fun on(method: String, block: (params: List<JsonElement>) -> String?) = apply {
        handlers.add { m, params -> if (m == method) block(params) else null }
    }

    /**
     * Answer every [method] request with [resultJson].
     */
    fun respond(method: String, resultJson: String) = on(method) { resultJson }

    /**
     * Answer every [method] request with [error].
     */
    fun fail(method: String, error: RpcError) = on(method) { throw RpcFailure(error) }

    override suspend fun <T> request(
        method: String,
        params: Array<*>,
        resultDeserializer: DeserializationStrategy<T>,
    ): Result<T, RpcError> {
        val jsonParams = params.map { it.toJsonElement() }
        recorded.add(Request(method, jsonParams))

        val result = try {
            handlers.firstNotNullOfOrNull { it(method, jsonParams) }
        } catch (e: RpcFailure) {
            return failure(e.error)
        } ?: return failure(RpcError(RpcError.CODE_METHOD_NOT_FOUND, "FakeJsonRpcClient: no response for '$method'"))

        return try {
            success(Kotlinx.DEFAULT.decodeFromString(resultDeserializer, result))
        } catch (e: Exception) {
            failure(RpcError(RpcError.CODE_INTERNAL_ERROR, "Failed to decode: ${e.message}", cause = e))
        }
    }

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean {
        throw UnsupportedOperationException("FakeJsonRpcClient does not support batching")
    }

    override suspend fun <T : Any> subscribe(
        params: Array<*>,
        resultDeserializer: DeserializationStrategy<T>,
    ): Result<ChannelReceiver<T>, RpcError> {
        return failure(RpcError(RpcError.CODE_METHOD_NOT_FOUND, "FakeJsonRpcClient does not support subscriptions"))
    }

    override fun close() = Unit
}
