package io.ethers.abi.call

import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingTransaction
import java.math.BigInteger
import java.util.function.Function

class FunctionCall<T>(
    provider: Middleware,
    to: Address,
    val data: Bytes?,
    private val decoder: Function<Bytes, T>,
) : ReadWriteContractCall<T, PendingTransaction, FunctionCall<T>>(provider) {

    init {
        call.to = to
        call.data = data
    }

    override val self: FunctionCall<T>
        get() = this

    override fun handleCallResult(result: Bytes) = success(decoder.apply(result))
    override fun handleSendResult(result: PendingTransaction) = result
}

class ReadFunctionCall<T>(
    provider: Middleware,
    to: Address,
    val data: Bytes?,
    private val decoder: Function<Bytes, T>,
) : ReadContractCall<T, ReadFunctionCall<T>>(provider) {

    init {
        call.to = to
        call.data = data
    }

    override val self: ReadFunctionCall<T>
        get() = this

    override fun handleCallResult(result: Bytes) = success(decoder.apply(result))
}

class PayableFunctionCall<T>(
    provider: Middleware,
    to: Address,
    val data: Bytes?,
    private val decoder: Function<Bytes, T>,
) : ReadWriteContractCall<T, PendingTransaction, PayableFunctionCall<T>>(provider) {

    init {
        call.to = to
        call.data = data
    }

    override val self: PayableFunctionCall<T>
        get() = this

    override fun handleCallResult(result: Bytes) = success(decoder.apply(result))
    override fun handleSendResult(result: PendingTransaction) = result

    var value: BigInteger?
        get() = call.value
        @JvmSynthetic set(value) {
            call.value = value
        }

    fun value(value: BigInteger?): PayableFunctionCall<T> {
        call.value = value
        return this
    }
}

private val UNIT_RESPONSE = success(Unit)

class ReceiveFunctionCall(
    provider: Middleware,
    to: Address,
    val value: BigInteger,
) : ReadWriteContractCall<Unit, PendingTransaction, ReceiveFunctionCall>(provider) {

    init {
        call.to = to
        call.value = value
    }

    override val self: ReceiveFunctionCall
        get() = this

    override fun handleCallResult(result: Bytes) = UNIT_RESPONSE
    override fun handleSendResult(result: PendingTransaction) = result
}
