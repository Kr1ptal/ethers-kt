package io.ethers.ens

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.Bytes
import io.ethers.core.types.CreateAccessList
import io.ethers.core.types.Hash
import io.ethers.core.types.IntoCallRequest
import io.ethers.core.types.StateOverride
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.CallFailedError
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.SuppliedRpcRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * A [Middleware] layer that accepts an [EnsCallRequest] anywhere a call request is expected, resolves its ENS
 * name to an [io.ethers.core.types.Address], and forwards a plain call request to [inner].
 *
 * ```kotlin
 * val provider = EnsMiddleware(Provider.builder(url).buildAwait().unwrap())
 * provider.call(EnsCallRequest("vitalik.eth").data(callData), BlockId.LATEST).sendAwait()
 * ```
 *
 * Requests that are not an [EnsCallRequest] are passed through untouched, at no extra cost.
 *
 * Note that [Middleware] fixes the error type of these methods to [RpcError], so ENS failures are wrapped in an
 * [RpcError] with code [CODE_ENS_RESOLUTION_FAILED] and the typed [EnsResolver.Error] as their cause. Use [ens]
 * directly when you need the typed error.
 *
 * The batch methods [callMany] and [traceCallMany] resolve every name in the list concurrently, and a name that
 * cannot be resolved fails the whole batch rather than being reported as a single failed entry.
 * */
class EnsMiddleware(
    override val inner: Middleware,
    val ens: EnsResolver,
) : Middleware by inner {
    constructor(inner: Middleware) : this(inner, EnsResolver(inner))

    /**
     * Resolve [call]'s recipient if it is an [EnsCallRequest], otherwise return it unchanged.
     * */
    internal fun resolveRecipient(call: IntoCallRequest): RpcRequest<IntoCallRequest, RpcError> {
        if (call !is EnsCallRequest) {
            return SuppliedRpcRequest { success(call) }
        }

        return ens.resolveAddress(call.toEns)
            .mapError { error ->
                RpcError(
                    CODE_ENS_RESOLUTION_FAILED,
                    "Failed to resolve ENS name '${call.toEns}': ${error.message}",
                    null,
                    error.toException(),
                )
            }
            .map { address -> call.resolveTo(address) }
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  EthApi#call
    //
    // IMPORTANT: Kotlin class delegation generates a forwarder to `inner` for every interface member this class
    // does not override, including members that have a default body. The convenience overloads below therefore
    // have to be overridden too - otherwise they would call `inner` directly and skip name resolution entirely.
    //-----------------------------------------------------------------------------------------------------------------
    override fun call(
        call: IntoCallRequest,
        blockId: BlockId,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ): RpcRequest<Bytes, RpcError> {
        if (call !is EnsCallRequest) {
            return inner.call(call, blockId, stateOverride, blockOverride)
        }

        return resolveRecipient(call).andThen { resolved ->
            inner.call(resolved, blockId, stateOverride, blockOverride).send()
        }
    }

    override fun call(call: IntoCallRequest, blockId: BlockId) = call(call, blockId, null, null)

    override fun call(call: IntoCallRequest, blockHash: Hash) = call(call, BlockId.Hash(blockHash), null, null)

    override fun call(call: IntoCallRequest, blockNumber: Long) = call(call, BlockId.Number(blockNumber), null, null)

    override fun call(call: IntoCallRequest, blockId: BlockId, stateOverride: StateOverride) = call(call, blockId, stateOverride, null)

    override fun call(call: IntoCallRequest, blockHash: Hash, stateOverride: StateOverride) = call(call, BlockId.Hash(blockHash), stateOverride, null)

    override fun call(call: IntoCallRequest, blockNumber: Long, stateOverride: StateOverride) = call(call, BlockId.Number(blockNumber), stateOverride, null)

    override fun call(call: IntoCallRequest, blockId: BlockId, blockOverride: BlockOverride) = call(call, blockId, null, blockOverride)

    override fun call(call: IntoCallRequest, blockHash: Hash, blockOverride: BlockOverride) = call(call, BlockId.Hash(blockHash), null, blockOverride)

    override fun call(call: IntoCallRequest, blockNumber: Long, blockOverride: BlockOverride) = call(call, BlockId.Number(blockNumber), null, blockOverride)

    override fun call(
        call: IntoCallRequest,
        blockHash: Hash,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ) = call(call, BlockId.Hash(blockHash), stateOverride, blockOverride)

    override fun call(
        call: IntoCallRequest,
        blockNumber: Long,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ) = call(call, BlockId.Number(blockNumber), stateOverride, blockOverride)

    //-----------------------------------------------------------------------------------------------------------------
    //                                  Remaining IntoCallRequest-shaped methods
    //-----------------------------------------------------------------------------------------------------------------
    override fun estimateGas(call: IntoCallRequest, blockId: BlockId): RpcRequest<Long, RpcError> {
        if (call !is EnsCallRequest) {
            return inner.estimateGas(call, blockId)
        }

        return resolveRecipient(call).andThen { resolved -> inner.estimateGas(resolved, blockId).send() }
    }

    override fun estimateGas(call: IntoCallRequest, hash: Hash) = estimateGas(call, BlockId.Hash(hash))

    override fun estimateGas(call: IntoCallRequest, number: Long) = estimateGas(call, BlockId.Number(number))

    override fun createAccessList(call: IntoCallRequest, blockId: BlockId): RpcRequest<CreateAccessList, RpcError> {
        if (call !is EnsCallRequest) {
            return inner.createAccessList(call, blockId)
        }

        return resolveRecipient(call).andThen { resolved -> inner.createAccessList(resolved, blockId).send() }
    }

    override fun createAccessList(call: IntoCallRequest, hash: Hash) = createAccessList(call, BlockId.Hash(hash))

    override fun createAccessList(call: IntoCallRequest, number: Long) = createAccessList(call, BlockId.Number(number))

    override fun fillTransaction(call: IntoCallRequest): RpcRequest<TransactionUnsigned, RpcError> {
        if (call !is EnsCallRequest) {
            return inner.fillTransaction(call)
        }

        return resolveRecipient(call).andThen { resolved -> inner.fillTransaction(resolved).send() }
    }

    override fun <T : Any> traceCall(
        call: IntoCallRequest,
        blockId: BlockId,
        config: TracerConfig<T>,
    ): RpcRequest<T, RpcError> {
        if (call !is EnsCallRequest) {
            return inner.traceCall(call, blockId, config)
        }

        return resolveRecipient(call).andThen { resolved -> inner.traceCall(resolved, blockId, config).send() }
    }

    override fun <T : Any> traceCall(call: IntoCallRequest, blockNumber: Long, config: TracerConfig<T>) = traceCall(call, BlockId.Number(blockNumber), config)

    override fun <T : Any> traceCall(call: IntoCallRequest, blockHash: Hash, config: TracerConfig<T>) = traceCall(call, BlockId.Hash(blockHash), config)

    /**
     * Resolve the recipient of every [EnsCallRequest] in [calls], leaving the other entries untouched and
     * preserving order.
     *
     * Names are resolved concurrently: a batch exists to save round trips, and resolving N names one after
     * another in front of it would cost more than it saves.
     *
     * A name that cannot be resolved fails the whole batch. Per-entry failures would have to be reported as
     * [CallFailedError], which carries only a string and would make an unresolvable name indistinguishable from
     * a reverted call - an unresolvable name is bad input, not an execution result.
     * */
    private suspend fun resolveRecipients(calls: List<IntoCallRequest>): Result<List<IntoCallRequest>, RpcError> {
        if (calls.none { it is EnsCallRequest }) {
            return success(calls)
        }

        val resolved = coroutineScope {
            calls.map { call ->
                async {
                    if (call is EnsCallRequest) resolveRecipient(call).send() else success(call)
                }
            }.awaitAll()
        }

        val out = ArrayList<IntoCallRequest>(calls.size)
        for (result in resolved) {
            out.add(result.unwrapOrReturn { return failure(it) })
        }

        return success(out)
    }

    override fun callMany(
        blockId: BlockId,
        calls: List<IntoCallRequest>,
        transactionIndex: Int,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ): RpcRequest<List<Result<Bytes, CallFailedError>>, RpcError> {
        if (calls.none { it is EnsCallRequest }) {
            return inner.callMany(blockId, calls, transactionIndex, stateOverride, blockOverride)
        }

        return SuppliedRpcRequest {
            val resolved = resolveRecipients(calls).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            inner.callMany(blockId, resolved, transactionIndex, stateOverride, blockOverride).send()
        }
    }

    override fun callMany(
        blockNumber: Long,
        calls: List<IntoCallRequest>,
        transactionIndex: Int,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ) = callMany(BlockId.Number(blockNumber), calls, transactionIndex, stateOverride, blockOverride)

    override fun callMany(
        blockHash: Hash,
        calls: List<IntoCallRequest>,
        transactionIndex: Int,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ) = callMany(BlockId.Hash(blockHash), calls, transactionIndex, stateOverride, blockOverride)

    override fun <T : Any> traceCallMany(
        blockId: BlockId,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int,
    ): RpcRequest<List<T>, RpcError> {
        if (calls.none { it is EnsCallRequest }) {
            return inner.traceCallMany(blockId, calls, config, transactionIndex)
        }

        return SuppliedRpcRequest {
            val resolved = resolveRecipients(calls).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            inner.traceCallMany(blockId, resolved, config, transactionIndex).send()
        }
    }

    override fun <T : Any> traceCallMany(
        blockNumber: Long,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int,
    ) = traceCallMany(BlockId.Number(blockNumber), calls, config, transactionIndex)

    override fun <T : Any> traceCallMany(
        blockHash: Hash,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int,
    ) = traceCallMany(BlockId.Hash(blockHash), calls, config, transactionIndex)

    companion object {
        /**
         * [RpcError.code] used when an RPC call failed because its ENS name could not be resolved.
         * */
        const val CODE_ENS_RESOLUTION_FAILED = 5100
    }
}
