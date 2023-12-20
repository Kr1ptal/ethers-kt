package io.ethers.abi.call

import io.ethers.abi.error.ContractError
import io.ethers.abi.error.DecodingError
import io.ethers.abi.error.RevertError
import io.ethers.core.FastHex
import io.ethers.core.types.AccessList
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.transaction.TransactionSigned
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.types.transaction.TxAccessList
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingInclusion
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcResponse
import io.ethers.signers.Signer
import java.math.BigInteger

/**
 * Contract call that can be used to both read and write data to the blockchain.
 * */
abstract class ReadWriteContractCall<C, S : PendingInclusion<*>, B : ReadWriteContractCall<C, S, B>>(
    provider: Middleware,
) : ReadContractCall<C, B>(provider) {
    /**
     * Try to sign the call using the [signer]. If [call] does not have all the required fields set, the function
     * returns null. The following fields must be set:
     * - [nonce]
     * - [gas]
     * - [gasPrice] or [gasFeeCap] + [gasTipCap]
     *
     * @return [TransactionSigned], or null if [call] is not ready to be signed (missing some fields).
     * */
    fun sign(signer: Signer): TransactionSigned? {
        val tx = call.from(signer.address).toTransaction() ?: return null
        return signer.signTransaction(tx)
    }

    /**
     * Sign the call using the [signer] and send it to the network. If call does not have all the required fields
     * set (see [sign] function), it will be filled using [Middleware.fillTransaction] before signing and sending.
     * */
    fun send(signer: Signer): RpcRequest<S> {
        // if all params are set on "call", create signed tx directly
        val signed = sign(signer)
        if (signed != null) {
            return provider.sendRawTransaction(signed).map {
                if (it.isError) return@map it.propagateError()

                RpcResponse.result(handleSendResult(it.resultOrThrow()))
            }
        }

        return provider.fillTransaction(call).map { result ->
            if (result.isError) return@map result.propagateError()

            val tx = signer.signTransaction(result.resultOrThrow())
            provider.sendRawTransaction(tx).sendAwait().map(::handleSendResult)
        }
    }

    protected abstract fun handleSendResult(result: PendingTransaction): S

    /**
     * Try to convert [CallRequest] to [TransactionUnsigned], if all required fields are set.
     * */
    private fun CallRequest.toTransaction(): TransactionUnsigned? {
        if (nonce < 0) {
            return null
        }

        if (gas < 21000L) {
            return null
        }

        if (gasFeeCap != null && gasTipCap != null) {
            return TxDynamicFee(
                to = to,
                value = value ?: BigInteger.ZERO,
                nonce = nonce,
                gas = gas,
                gasFeeCap = gasFeeCap!!,
                gasTipCap = gasTipCap!!,
                data = data,
                chainId = chainId,
                accessList = accessList,
            )
        }

        if (gasPrice != null) {
            if (accessList != null) {
                return TxAccessList(
                    to = to,
                    value = value ?: BigInteger.ZERO,
                    nonce = nonce,
                    gas = gas,
                    gasPrice = gasPrice!!,
                    data = data,
                    chainId = chainId,
                    accessList = accessList!!,
                )
            }

            return TxLegacy(
                to = to,
                value = value ?: BigInteger.ZERO,
                nonce = nonce,
                gas = gas,
                gasPrice = gasPrice!!,
                data = data,
                chainId = chainId,
            )
        }

        return null
    }
}

/**
 * Contract call that can be used only to read data from the blockchain. This corresponds to "pure" and "view"
 * functions in Solidity.
 * */
abstract class ReadContractCall<C, B : ReadContractCall<C, B>>(
    protected val provider: Middleware,
) {
    protected val call = CallRequest().apply { chainId = provider.chainId }

    /**
     * Execute "eth_call" at the given [BlockId] and return the result of the call. This is a read-only call, and it
     * will not modify the blockchain state. Optionally, the [stateOverride] and [blockOverride] can be provided to
     * override the state on which the call is executed.
     * */
    @JvmOverloads
    fun call(
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<C> {
        return provider.call(call, blockId, stateOverride, blockOverride).map { response ->
            // "eth_call" execution reverts are included in "error" field of the json-rpc response
            if (response.isError) {
                val err = response.error?.asTypeOrNull<RpcResponse.RpcError>()
                if (err != null && err.isExecutionError && err.data != null) {
                    // if data is not a valid hex string, it's an already decoded revert error
                    if (!FastHex.isValidHex(err.data!!)) {
                        return@map RpcResponse.error(RevertError(err.data!!))
                    }

                    // otherwise it could be a custom error
                    val error = ContractError.getOrNull(Bytes(err.data!!))
                    if (error != null) {
                        return@map RpcResponse.error(error)
                    }
                }

                return@map response.propagateError()
            }

            val result = response.resultOrThrow()
            val error = ContractError.getOrNull(result)
            if (error != null) {
                return@map RpcResponse.error(error)
            }

            try {
                return@map handleCallResult(result)
            } catch (e: Exception) {
                return@map RpcResponse.error(DecodingError(result, e))
            }
        }
    }

    /**
     * Execute "debug_traceCall" at the given [BlockId] using the provided [TracerConfig], returning the result of
     * the tracer. Similar to [call] function, this is a read-only call, and it will not modify the blockchain state.
     * */
    fun <T> traceCall(blockId: BlockId, config: TracerConfig<T>): RpcRequest<T> {
        return provider.traceCall(call, blockId, config)
    }

    protected abstract val self: B

    protected abstract fun handleCallResult(result: Bytes): RpcResponse<C>

    var from: Address?
        get() = call.from
        @JvmSynthetic set(value) {
            call.from = value
        }

    var gas: Long
        get() = call.gas
        @JvmSynthetic set(value) {
            call.gas = value
        }

    var gasPrice: BigInteger?
        get() = call.gasPrice
        @JvmSynthetic set(value) {
            call.gasPrice = value
        }

    var gasFeeCap: BigInteger?
        get() = call.gasFeeCap
        @JvmSynthetic set(value) {
            call.gasFeeCap = value
        }

    var gasTipCap: BigInteger?
        get() = call.gasTipCap
        @JvmSynthetic set(value) {
            call.gasTipCap = value
        }

    var nonce: Long
        get() = call.nonce
        @JvmSynthetic set(value) {
            call.nonce = value
        }

    var accessList: List<AccessList.Item>?
        get() = call.accessList
        @JvmSynthetic set(value) {
            call.accessList = value
        }

    fun from(value: Address?): B {
        call.from = value
        return self
    }

    fun gas(value: Long): B {
        call.gas = value
        return self
    }

    fun gasPrice(value: BigInteger?): B {
        call.gasPrice = value
        return self
    }

    fun gasFeeCap(value: BigInteger?): B {
        call.gasFeeCap = value
        return self
    }

    fun gasTipCap(value: BigInteger?): B {
        call.gasTipCap = value
        return self
    }

    fun nonce(value: Long): B {
        call.nonce = value
        return self
    }

    fun accessList(value: List<AccessList.Item>?): B {
        call.accessList = value
        return self
    }
}
