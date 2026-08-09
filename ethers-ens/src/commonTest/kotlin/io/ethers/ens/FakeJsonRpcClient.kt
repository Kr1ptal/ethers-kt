package io.ethers.ens

import io.channels.core.ChannelReceiver
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * A [JsonRpcClient] that answers from a queued script instead of the network, and records every request it saw.
 *
 * Responses are queued per method and consumed in FIFO order, which is what ENS resolution needs: a single
 * `resolveAddress` call issues four separate `eth_call` requests that are only distinguishable by their order.
 * */
class FakeJsonRpcClient : JsonRpcClient {
    private val queued = HashMap<String, ArrayDeque<String>>()
    private val recorded = ArrayList<RecordedRequest>()

    val requests: List<RecordedRequest> get() = recorded

    class RecordedRequest(val method: String, val params: List<Any?>)

    /**
     * Queue the raw JSON `result` payload returned for the next [method] request.
     * */
    fun enqueue(method: String, resultJson: String) = apply {
        queued.getOrPut(method) { ArrayDeque() }.addLast(resultJson)
    }

    override suspend fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): Result<T, RpcError> {
        recorded.add(RecordedRequest(method, params.toList()))

        val next = queued[method]?.removeFirstOrNull() ?: return failure(
            RpcError(RpcError.CODE_INTERNAL_ERROR, "FakeJsonRpcClient: no queued response for '$method'"),
        )

        return success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(next)))
    }

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean {
        throw UnsupportedOperationException("FakeJsonRpcClient does not support batching")
    }

    override suspend fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): Result<ChannelReceiver<T>, RpcError> {
        return failure(RpcError(RpcError.CODE_METHOD_NOT_FOUND, "FakeJsonRpcClient does not support subscriptions"))
    }

    override fun close() = Unit
}

/** ABI-encoded `true`, as returned by `supportsInterface`. */
const val ABI_TRUE = "0x0000000000000000000000000000000000000000000000000000000000000001"

/** ABI-encoded `false`, as returned by `supportsInterface`. */
const val ABI_FALSE = "0x0000000000000000000000000000000000000000000000000000000000000000"

/** Left-pad [address] to a full 32-byte ABI word. */
fun abiWord(address: Address): String = "0x" + "0".repeat(24) + address.toString().removePrefix("0x")

/**
 * Queue the four `eth_call` responses a non-wildcard [EnsResolver.resolveAddress] performs: registry lookup,
 * ENSIP-10 support probe, `addr(bytes32)` support probe, and the address itself.
 * */
fun FakeJsonRpcClient.enqueueAddressResolution(resolver: Address, resolved: Address) = apply {
    enqueue("eth_call", "\"${abiWord(resolver)}\"")
    enqueue("eth_call", "\"$ABI_FALSE\"")
    enqueue("eth_call", "\"$ABI_TRUE\"")
    enqueue("eth_call", "\"${abiWord(resolved)}\"")
}
