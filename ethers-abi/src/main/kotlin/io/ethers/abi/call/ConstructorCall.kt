package io.ethers.abi.call

import io.ethers.abi.AbiContract
import io.ethers.abi.error.ContractError
import io.ethers.abi.error.DeployError
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingInclusion
import io.ethers.providers.types.PendingTransaction
import java.math.BigInteger
import java.time.Duration
import java.util.function.BiFunction

class ConstructorCall<T : AbiContract>(
    provider: Middleware,
    bytecode: Bytes,
    private val constructor: BiFunction<Middleware, Address, T>,
) : ReadWriteContractCall<CallDeploy, PendingContractDeploy<T>, ConstructorCall<T>>(provider) {

    init {
        // create a random address for each ConstructorCall instance, so multiple deploys can be made via "call",
        // all on a different address, so they don't override each other in state overrides.
        call.from = Address.random()
        call.to = null
        call.nonce = 0L
        call.data = bytecode
    }

    override val self: ConstructorCall<T>
        get() = this

    override fun handleCallResult(result: Bytes) = handleCallResult(call, result)
    override fun handleSendResult(result: PendingTransaction) = handleSendResult(provider, result, constructor)
}

class PayableConstructorCall<T : AbiContract>(
    provider: Middleware,
    bytecode: Bytes,
    private val constructor: BiFunction<Middleware, Address, T>,
) : ReadWriteContractCall<CallDeploy, PendingContractDeploy<T>, PayableConstructorCall<T>>(provider) {

    init {
        // create a random address for each ConstructorCall instance, so multiple deploys can be made via "call",
        // all on a different address, so they don't override each other in state overrides.
        call.from = Address.random()
        call.to = null
        call.nonce = 0L
        call.data = bytecode
    }

    override val self: PayableConstructorCall<T>
        get() = this

    override fun handleCallResult(result: Bytes) = handleCallResult(call, result)
    override fun handleSendResult(result: PendingTransaction) = handleSendResult(provider, result, constructor)

    var value: BigInteger?
        get() = call.value
        @JvmSynthetic set(value) {
            call.value = value
        }

    fun value(value: BigInteger?): PayableConstructorCall<T> {
        call.value = value
        return this
    }
}

data class CallDeploy(val address: Address, val deployedBytecode: Bytes) {
    fun toStateOverride(): Map<Address, AccountOverride> {
        return HashMap<Address, AccountOverride>(1).apply { addStateOverride(this) }
    }

    fun addStateOverride(stateOverride: MutableMap<Address, AccountOverride>) {
        stateOverride[address] = AccountOverride().code(deployedBytecode)
    }
}

private fun handleCallResult(call: CallRequest, result: Bytes): Result<CallDeploy, ContractError> {
    if (result.size == 0) {
        return failure(DeployError.NoBytecode)
    }

    val nonce = if (call.nonce == -1L) 0L else call.nonce
    val deployAddress: Address = Address.computeCreate(call.from ?: Address.ZERO, nonce)
    return success(CallDeploy(deployAddress, result))
}

private fun <T : AbiContract> handleSendResult(
    provider: Middleware,
    result: PendingTransaction,
    constructor: BiFunction<Middleware, Address, T>,
): PendingContractDeploy<T> {
    return PendingContractDeploy(provider, result, constructor)
}

class PendingContractDeploy<T : AbiContract>(
    private val provider: Middleware,
    private val result: PendingTransaction,
    private val constructor: BiFunction<Middleware, Address, T>,
) : PendingInclusion<T> {
    val hash: Hash
        get() = result.hash

    override fun awaitInclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<T, PendingInclusion.Error> {
        return result.awaitInclusion(retries, interval, confirmations).andThen {
            when {
                !it.isSuccessful || it.contractAddress == null -> failure(PendingInclusion.Error.TxFailed(hash, it))
                else -> success(constructor.apply(provider, it.contractAddress!!))
            }
        }
    }

    override fun toString(): String {
        return "PendingContractDeploy(hash=$hash)"
    }
}
