package io.ethers.aa.bundler

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.aa.UserOperationJson
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
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The ERC-4337 bundler JSON-RPC API (ERC-7769).
 */
interface BundlerApi {
    /**
     * Submit a signed [userOperation] to the bundler's mempool, to be included by [entryPoint]. Returns the user
     * operation hash.
     */
    fun sendUserOperation(userOperation: UserOperation, entryPoint: EntryPoint): RpcRequest<Hash, RpcError>

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

    /**
     * Poll [getUserOperationReceipt] every [pollingInterval] until it returns a receipt, or fail with
     * [UserOperationError.ReceiptTimeout] after [timeout].
     *
     * An RPC error fails the request immediately, unless [retryOnError] is set, in which case it is retried until
     * [timeout] like a missing receipt is.
     */
    fun waitForUserOperationReceipt(userOpHash: Hash): RpcRequest<UserOperationReceipt, UserOperationError> {
        return waitForUserOperationReceipt(userOpHash, DEFAULT_RECEIPT_TIMEOUT, DEFAULT_POLLING_INTERVAL, false)
    }

    /**
     * Poll [getUserOperationReceipt] until it returns a receipt, see [waitForUserOperationReceipt].
     */
    fun waitForUserOperationReceipt(
        userOpHash: Hash,
        timeout: Duration,
        pollingInterval: Duration,
        retryOnError: Boolean,
    ): RpcRequest<UserOperationReceipt, UserOperationError> {
        require(pollingInterval.isPositive()) { "pollingInterval must be positive" }
        return SuppliedRpcRequest { pollReceipt(this, userOpHash, timeout, pollingInterval, retryOnError) }
    }

    companion object {
        val DEFAULT_RECEIPT_TIMEOUT: Duration = 2.minutes
        val DEFAULT_POLLING_INTERVAL: Duration = 2.seconds
    }
}

/**
 * [BundlerApi] over a [JsonRpcClient] connected to a bundler, e.g. `BundlerClient(HttpClient(bundlerUrl))`.
 */
class BundlerClient(val client: JsonRpcClient) : BundlerApi {
    override fun sendUserOperation(userOperation: UserOperation, entryPoint: EntryPoint): RpcRequest<Hash, RpcError> {
        val params = arrayOf(UserOperationJson.encode(userOperation, entryPoint.version), entryPoint.address)
        return RpcCall(client, "eth_sendUserOperation", params, Hash.serializer())
    }

    override fun estimateUserOperationGas(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        stateOverride: StateOverride?,
    ): RpcRequest<UserOperationGasEstimate, RpcError> {
        val op = UserOperationJson.encode(userOperation, entryPoint.version)
        val params = when (stateOverride) {
            null -> arrayOf(op, entryPoint.address)
            else -> arrayOf(op, entryPoint.address, stateOverride)
        }
        return RpcCall(client, "eth_estimateUserOperationGas", params, GAS_ESTIMATE)
    }

    override fun getUserOperationByHash(userOpHash: Hash): RpcRequest<UserOperationByHash?, RpcError> {
        return RpcCall(client, "eth_getUserOperationByHash", arrayOf(userOpHash), BY_HASH)
    }

    override fun getUserOperationReceipt(userOpHash: Hash): RpcRequest<UserOperationReceipt?, RpcError> {
        return RpcCall(client, "eth_getUserOperationReceipt", arrayOf(userOpHash), RECEIPT)
    }

    override fun supportedEntryPoints(): RpcRequest<List<Address>, RpcError> {
        return RpcCall(client, "eth_supportedEntryPoints", EMPTY_PARAMS, ListSerializer(Address.serializer()))
    }

    override fun chainId(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_chainId", EMPTY_PARAMS, HexLongSerializer)
    }

    private companion object {
        val EMPTY_PARAMS = emptyArray<Any>()
        val GAS_ESTIMATE = JsonElement.serializer().map(UserOperationGasEstimate::fromJson)
        val BY_HASH = JsonElement.serializer().map { it.ifNotNull(UserOperationByHash::fromJson) }
        val RECEIPT = JsonElement.serializer().map { it.ifNotNull(UserOperationReceipt::fromJson) }
    }
}

private suspend fun pollReceipt(
    bundler: BundlerApi,
    userOpHash: Hash,
    timeout: Duration,
    pollingInterval: Duration,
    retryOnError: Boolean,
): Result<UserOperationReceipt, UserOperationError> {
    val start = TimeSource.Monotonic.markNow()
    while (true) {
        val result = bundler.getUserOperationReceipt(userOpHash).send()
        if (result.isFailure()) {
            if (!retryOnError) return failure(result.error.toUserOperationError())
        } else {
            val receipt = result.unwrap()
            if (receipt != null) return success(receipt)
        }

        if (start.elapsedNow() + pollingInterval > timeout) {
            return failure(UserOperationError.ReceiptTimeout(userOpHash, timeout))
        }
        delay(pollingInterval)
    }
}

internal fun <T> Result<T, RpcError>.toUserOperationResult(): Result<T, UserOperationError> = mapError { it.toUserOperationError() }
