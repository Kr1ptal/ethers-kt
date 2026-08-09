package io.ethers.ens

import io.ethers.core.success
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.IntoCallRequest
import io.ethers.core.types.StateOverride
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.SuppliedRpcRequest

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

        return ens.resolveAddress(call.toEnsName)
            .mapError { error ->
                RpcError(
                    CODE_ENS_RESOLUTION_FAILED,
                    "Failed to resolve ENS name '${call.toEnsName}': ${error.message}",
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

    companion object {
        /**
         * [RpcError.code] used when an RPC call failed because its ENS name could not be resolved.
         * */
        const val CODE_ENS_RESOLUTION_FAILED = 5100
    }
}
