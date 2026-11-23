package io.ethers.aa.bundler

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.aa.errors.UserOperationError
import io.ethers.aa.errors.toUserOperationError
import io.ethers.core.HexLongSerializer
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Hash
import io.ethers.core.types.StateOverride
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.map
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.SuppliedRpcRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The ERC-4337 bundler JSON-RPC API (ERC-7769).
 */
interface BundlerApi {
    /**
     * Submit a signed [userOperation] to the bundler's mempool, to be included by [entryPoint]. Returns the pending
     * operation, which can await its inclusion.
     */
    fun sendUserOperation(userOperation: UserOperation, entryPoint: EntryPoint): RpcRequest<PendingUserOperation, RpcError>

    /**
     * Estimate the gas limits of [userOperation].
     *
     * The operation does not have to be signed, but its signature must be a valid stub of the right length, as the
     * account validates it during estimation. Gas fields the bundler estimates may be zero.
     */
    fun estimateUserOperationGas(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
    ): RpcRequest<UserOperationGasEstimate, RpcError> = estimateUserOperationGas(userOperation, entryPoint, null)

    /**
     * Estimate the gas limits of [userOperation], executing it on top of [stateOverride].
     */
    fun estimateUserOperationGas(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        stateOverride: StateOverride?,
    ): RpcRequest<UserOperationGasEstimate, RpcError>

    /**
     * Get a user operation by its [userOpHash], or null if the bundler does not know it.
     */
    fun getUserOperationByHash(userOpHash: Hash): RpcRequest<UserOperationByHash?, RpcError>

    /**
     * Get the receipt of a user operation by its [userOpHash], or null if it was not included yet.
     */
    fun getUserOperationReceipt(userOpHash: Hash): RpcRequest<UserOperationReceipt?, RpcError>

    /**
     * Get the addresses of the EntryPoints the bundler supports, in order of preference.
     */
    fun supportedEntryPoints(): RpcRequest<List<Address>, RpcError>

    /**
     * Get the chain ID of the bundler's network.
     */
    fun chainId(): RpcRequest<Long, RpcError>
}

/**
 * [BundlerApi] over a [JsonRpcClient] connected to a bundler, e.g. `BundlerClient(HttpClient(bundlerUrl))`.
 */
class BundlerClient(val client: JsonRpcClient) : BundlerApi {
    override fun sendUserOperation(userOperation: UserOperation, entryPoint: EntryPoint): RpcRequest<PendingUserOperation, RpcError> {
        val params = arrayOf(userOperation.toJson(entryPoint.version), entryPoint.address)
        return RpcCall(client, "eth_sendUserOperation", params, Hash.serializer().map { PendingUserOperation(it, this) })
    }

    override fun estimateUserOperationGas(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        stateOverride: StateOverride?,
    ): RpcRequest<UserOperationGasEstimate, RpcError> {
        val op = userOperation.toJson(entryPoint.version)
        val params = when (stateOverride) {
            null -> arrayOf(op, entryPoint.address)
            else -> arrayOf(op, entryPoint.address, stateOverride)
        }
        return RpcCall(client, "eth_estimateUserOperationGas", params, UserOperationGasEstimate.serializer())
    }

    override fun getUserOperationByHash(userOpHash: Hash): RpcRequest<UserOperationByHash?, RpcError> {
        return RpcCall(client, "eth_getUserOperationByHash", arrayOf(userOpHash), UserOperationByHash.serializer().nullable)
    }

    override fun getUserOperationReceipt(userOpHash: Hash): RpcRequest<UserOperationReceipt?, RpcError> {
        return RpcCall(client, "eth_getUserOperationReceipt", arrayOf(userOpHash), UserOperationReceipt.serializer().nullable)
    }

    override fun supportedEntryPoints(): RpcRequest<List<Address>, RpcError> {
        return RpcCall(client, "eth_supportedEntryPoints", EMPTY_PARAMS, ListSerializer(Address.serializer()))
    }

    override fun chainId(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_chainId", EMPTY_PARAMS, HexLongSerializer)
    }

    private companion object {
        val EMPTY_PARAMS = emptyArray<Any>()
    }
}
