package io.ethers.ens

import io.channels.core.ChannelReceiver
import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.CallRequest
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
    private val handlers = ArrayList<(String, List<Any?>) -> String?>()
    private val recorded = ArrayList<RecordedRequest>()

    val requests: List<RecordedRequest> get() = recorded

    class RecordedRequest(val method: String, val params: List<Any?>)

    /**
     * Queue the raw JSON `result` payload returned for the next [method] request.
     * */
    fun enqueue(method: String, resultJson: String) = apply {
        queued.getOrPut(method) { ArrayDeque() }.addLast(resultJson)
    }

    /**
     * Register a content-based responder, consulted before the [enqueue] queue. Returning null defers to the
     * next handler, and then to the queue.
     *
     * Unlike [enqueue], a handler does not depend on the order requests arrive in, which is what concurrent
     * resolution needs: several names resolving at once interleave their `eth_call`s arbitrarily.
     * */
    fun handle(handler: (method: String, params: List<Any?>) -> String?) = apply { handlers.add(handler) }

    override suspend fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): Result<T, RpcError> {
        val paramList = params.toList()
        recorded.add(RecordedRequest(method, paramList))

        val handled = handlers.firstNotNullOfOrNull { it(method, paramList) }
        if (handled != null) {
            return success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(handled)))
        }

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

/** ENSIP-10 (wildcard resolution) interface id, ABI-encoded as the leading bytes of a `bytes4` word. */
private const val ENSIP_10_INTERFACE_ID = "9061b923"

/**
 * Serve ENS resolution for [names] by inspecting each `eth_call`'s calldata rather than its arrival order, so
 * any number of names can resolve concurrently and interleave freely.
 *
 * A name that is not in [names] resolves to the zero address in the registry, which is how the real registry
 * reports "no resolver registered" and makes the name unresolvable.
 * */
fun FakeJsonRpcClient.serveEnsNames(resolver: Address, vararg names: Pair<String, Address>) = apply {
    val byNameHash = names.associate { (name, address) ->
        FastHex.encodeWithoutPrefix(NameHash.nameHash(name)) to address
    }

    handle { method, params ->
        if (method != "eth_call") return@handle null

        val data = (params[0] as? CallRequest)?.data?.toString() ?: return@handle null
        if (data.length < 74) return@handle null

        val selector = data.substring(0, 10)
        val firstWord = data.substring(10, 74)

        when (selector) {
            EnsRegistry.FUNCTION_RESOLVER.selector.toString() ->
                if (byNameHash.containsKey(firstWord)) "\"${abiWord(resolver)}\"" else "\"${abiWord(Address.ZERO)}\""

            ExtendedResolver.FUNCTION_SUPPORTS_INTERFACE.selector.toString() ->
                if (firstWord.startsWith(ENSIP_10_INTERFACE_ID)) "\"$ABI_FALSE\"" else "\"$ABI_TRUE\""

            ExtendedResolver.FUNCTION_ADDR.selector.toString() ->
                byNameHash[firstWord]?.let { "\"${abiWord(it)}\"" }

            else -> null
        }
    }
}
