package io.ethers.abi.call

import io.ethers.abi.error.ContractError
import io.ethers.abi.error.DecodingError
import io.ethers.abi.error.RevertError
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingTransaction
import java.math.BigInteger
import java.util.concurrent.CompletableFuture

/**
 * Contract call that aggregates multiple aggregateble calls into a single [Multicall3] contract call. This
 * calls the [Multicall3.aggregate3Value] function.
 *
 * Aggregate calls can be nested, i.e. an aggregate call can contain other aggregate calls.
 * */
class AggregateFunctionCall<T>(
    provider: Middleware,
    multicall3: Address,
) : ReadWriteContractCall<List<Result<T, ContractError>>, PendingTransaction, AggregateFunctionCall<T>>(provider),
    AggregateableCall<List<Result<T, ContractError>>> {
    constructor(provider: Middleware) : this(provider, Multicall3.getAddressForChainId(provider.chainId))

    private val requests = ArrayList<AggregateRequest<T>>()
    private var lastRequestsSize = 0

    init {
        call.to = multicall3
    }

    override val self: AggregateFunctionCall<T>
        get() = this

    override val call: CallRequest
        get() {
            val call = super.call

            // only re-encode data if new requests were added
            if (lastRequestsSize == requests.size) {
                return call
            }

            lastRequestsSize = requests.size

            var totalValue = BigInteger.ZERO
            val arr = Array(requests.size) {
                val req = requests[it]
                val value = req.function.value ?: BigInteger.ZERO
                totalValue += value

                Multicall3.Call3Value(
                    req.function.to!!,
                    req.allowFailure,
                    value,
                    req.function.data ?: Bytes.EMPTY
                )
            }

            call.value = totalValue
            call.data = Multicall3.FUNCTION_AGGREGATE3_VALUE.encodeCall(arrayOf(arr))
            return call
        }

    /**
     * Add a call to the aggregate call. Only [to], [value], [data] fields are used from the call.
     * */
    @JvmOverloads
    fun <R: T> addCall(
        call: AggregateableCall<R>,
        allowFailure: Boolean = false
    ): CompletableFuture<Result<R, ContractError>> {
        // TODO replace with ConditionalCompletableFuture, but the flag needs to be set when
        //  the RPC request is actually sent
        val future = CompletableFuture<Result<R, ContractError>>()

        // safe cast, we're downcasting from R to T
        @Suppress("UNCHECKED_CAST")
        requests.add(
            AggregateRequest(
                call,
                allowFailure,
                future,
            ) as AggregateRequest<T>
        )
        return future
    }

    override fun handleCallResult(result: Bytes): Result<List<Result<T, ContractError>>, ContractError> {
        val decoded = Multicall3.FUNCTION_AGGREGATE3_VALUE.decodeResponse(result)[0] as Array<Multicall3.Result>
        if (decoded.size != requests.size) {
            val ret = failure(
                DecodingError(
                    result,
                    "Multicall returned ${decoded.size} results, expected ${requests.size}",
                    null,
                )
            )

            // complete all futures with the same error
            for (i in requests.indices) {
                requests[i].future.complete(ret)
            }

            return ret
        }

        val ret = ArrayList<Result<T, ContractError>>(requests.size)
        for (i in requests.indices) {
            val request = requests[i]
            val callResult = decoded[i]
            if (callResult.success) {
                val res = request.function.decodeCallResult(callResult.returnData)
                request.future.complete(res)
                ret.add(res)
            } else {
                val res = failure(tryDecodingCallRevert(callResult.returnData))
                request.future.complete(res)
                ret.add(res)
            }
        }

        return success(ret)
    }

    override fun handleSendResult(result: PendingTransaction) = result

    private fun tryDecodingCallRevert(err: Bytes): ContractError {
        val contractError = ContractError.getOrNull(err)
        if (contractError != null) {
            return contractError
        }

        // if we can't decode the error, just return the raw bytes as hex
        return RevertError(err.toString())
    }

    private class AggregateRequest<T>(
        val function: AggregateableCall<T>,
        val allowFailure: Boolean,
        val future: CompletableFuture<Result<T, ContractError>>,
    )
}

/**
 * A contract call that can be aggregated via [Multicall3] contract function call.
 * */
interface AggregateableCall<T> {
    val provider: Middleware

    val to: Address?
    val value: BigInteger?
    val data: Bytes?

    fun decodeCallResult(result: Bytes): Result<T, ContractError>

    fun aggregate(
        aggregate: AggregateFunctionCall<in T>,
        allowFailure: Boolean = false
    ): CompletableFuture<Result<T, ContractError>> {
        return aggregate.addCall(this, allowFailure)
    }
}

fun <T> Iterable<AggregateableCall<T>>.aggregate(allowFailure: Boolean = false): AggregateFunctionCall<T> {
    val iter = iterator()
    if (!iter.hasNext()) {
        throw IllegalArgumentException("No calls to aggregate")
    }

    val first = iter.next()
    val call = AggregateFunctionCall<T>(first.provider)
    call.addCall(first, allowFailure)

    for (req in iter) {
        call.addCall(req, allowFailure)
    }

    return call
}
