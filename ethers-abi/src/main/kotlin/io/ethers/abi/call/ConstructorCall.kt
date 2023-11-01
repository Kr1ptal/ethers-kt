package io.ethers.abi.call

import io.ethers.abi.AbiContract
import io.ethers.abi.error.DeployError
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingInclusion
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcResponse
import java.math.BigInteger
import java.time.Duration
import java.util.function.BiFunction

class ConstructorCall<T : AbiContract>(
    provider: Middleware,
    bytecode: Bytes,
    private val constructor: BiFunction<Middleware, Address, T>,
) : ReadWriteContractCall<CallDeploy, PendingContractDeploy<T>, ConstructorCall<T>>(provider) {

    init {
        call.to = null
        call.from = Address.ZERO
        call.nonce = 0L
        call.data = bytecode
    }

    override val self: ConstructorCall<T>
        get() = this

    override fun handleCallResult(result: Bytes) = handleCallResult(call, result)
    override fun handleSendResult(result: PendingTransaction): PendingContractDeploy<T> {
        return handleSendResult(provider, result, constructor)
    }
}

class PayableConstructorCall<T : AbiContract>(
    provider: Middleware,
    bytecode: Bytes,
    private val constructor: BiFunction<Middleware, Address, T>,
) : ReadWriteContractCall<CallDeploy, PendingContractDeploy<T>, PayableConstructorCall<T>>(provider) {

    init {
        call.to = null
        call.from = Address.ZERO
        call.nonce = 0L
        call.data = bytecode
    }

    override val self: PayableConstructorCall<T>
        get() = this

    override fun handleCallResult(result: Bytes) = handleCallResult(call, result)
    override fun handleSendResult(result: PendingTransaction): PendingContractDeploy<T> {
        return handleSendResult(provider, result, constructor)
    }

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

private fun handleCallResult(call: CallRequest, result: Bytes): RpcResponse<CallDeploy> {
    if (result.size == 0) {
        return RpcResponse.error(DeployError.NO_BYTECODE)
    }

    val nonce = if (call.nonce == -1L) 0L else call.nonce
    val deployAddress: Address = Address.computeCreate(call.from ?: Address.ZERO, nonce)
    return RpcResponse.result(CallDeploy(deployAddress, result))
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

    override fun awaitInclusion(retries: Int, interval: Duration, confirmations: Int): RpcResponse<T> {
        val pending = result.awaitInclusion(retries, interval, confirmations)
        if (pending.isError) {
            return pending.propagateError()
        }

        val receipt = pending.resultOrThrow()
        if (!receipt.isSuccessful || receipt.contractAddress == null) {
            return RpcResponse.error(DeployError.TX_FAILED)
        }

        return RpcResponse.result(constructor.apply(provider, receipt.contractAddress!!))
    }

    override fun toString(): String {
        return "PendingContractDeploy(hash=$hash)"
    }
}
