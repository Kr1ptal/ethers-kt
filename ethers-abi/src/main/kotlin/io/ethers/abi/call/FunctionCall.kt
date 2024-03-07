package io.ethers.abi.call

import io.ethers.abi.error.ContractError
import io.ethers.abi.error.DecodingError
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.Bytes
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcRequest
import java.math.BigInteger
import java.util.function.Function

class FunctionCall<T>(
    provider: Middleware,
    to: Address,
    data: Bytes?,
    private val decoder: Function<Bytes, T>,
) : ReadWriteContractCall<T, PendingTransaction, FunctionCall<T>>(provider), Multicall3.Aggregatable<T> {
    // NOTE: not intended for general use. Used only in Multicall3#aggregateCalls function where we need a call on which
    // we can set a value, but not allow consumers to change it.
    internal constructor(
        provider: Middleware,
        to: Address,
        value: BigInteger?,
        data: Bytes?,
        decoder: Function<Bytes, T>,
    ) : this(provider, to, data, decoder) {
        call.value = value
    }

    init {
        call.to = to
        call.data = data
    }

    override val self: FunctionCall<T>
        get() = this

    override fun doCall(
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>?,
        blockOverride: BlockOverride?,
    ): RpcRequest<T, ContractError> {
        return provider.call(call, blockId, stateOverride, blockOverride)
            .mapError(::tryDecodingContractRevert)
            .andThen(::decodeCallResult)
    }

    override fun decodeCallResult(result: Bytes): Result<T, ContractError> {
        return try {
            success(decoder.apply(result))
        } catch (e: Exception) {
            failure(DecodingError(result, "Unable to decode result", e))
        }
    }

    override fun handleSendResult(result: PendingTransaction) = result
}

class ReadFunctionCall<T>(
    provider: Middleware,
    to: Address,
    data: Bytes?,
    private val decoder: Function<Bytes, T>,
) : ReadContractCall<T, ReadFunctionCall<T>>(provider), Multicall3.Aggregatable<T> {

    init {
        call.to = to
        call.data = data
    }

    override val self: ReadFunctionCall<T>
        get() = this

    override fun doCall(
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>?,
        blockOverride: BlockOverride?,
    ): RpcRequest<T, ContractError> {
        return provider.call(call, blockId, stateOverride, blockOverride)
            .mapError(::tryDecodingContractRevert)
            .andThen(::decodeCallResult)
    }

    override fun decodeCallResult(result: Bytes): Result<T, ContractError> {
        return try {
            success(decoder.apply(result))
        } catch (e: Exception) {
            failure(DecodingError(result, "Unable to decode result", e))
        }
    }
}

class PayableFunctionCall<T>(
    provider: Middleware,
    to: Address,
    data: Bytes?,
    private val decoder: Function<Bytes, T>,
) : ReadWriteContractCall<T, PendingTransaction, PayableFunctionCall<T>>(provider), Multicall3.Aggregatable<T> {

    init {
        call.to = to
        call.data = data
    }

    override val self: PayableFunctionCall<T>
        get() = this

    override fun doCall(
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>?,
        blockOverride: BlockOverride?,
    ): RpcRequest<T, ContractError> {
        return provider.call(call, blockId, stateOverride, blockOverride)
            .mapError(::tryDecodingContractRevert)
            .andThen(::decodeCallResult)
    }

    override fun decodeCallResult(result: Bytes): Result<T, ContractError> {
        return try {
            success(decoder.apply(result))
        } catch (e: Exception) {
            failure(DecodingError(result, "Unable to decode result", e))
        }
    }

    override fun handleSendResult(result: PendingTransaction) = result

    override var value: BigInteger?
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
    value: BigInteger,
) : ReadWriteContractCall<Unit, PendingTransaction, ReceiveFunctionCall>(provider), Multicall3.Aggregatable<Unit> {

    init {
        call.to = to
        call.value = value
    }

    override val self: ReceiveFunctionCall
        get() = this

    override fun doCall(
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>?,
        blockOverride: BlockOverride?,
    ): RpcRequest<Unit, ContractError> {
        return provider.call(call, blockId, stateOverride, blockOverride)
            .mapError(::tryDecodingContractRevert)
            .andThen(::decodeCallResult)
    }

    override fun decodeCallResult(result: Bytes): Result<Unit, ContractError> {
        return UNIT_RESPONSE
    }

    override fun handleSendResult(result: PendingTransaction) = result
}
