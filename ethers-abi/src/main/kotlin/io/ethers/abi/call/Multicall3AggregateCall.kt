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
 * Contract call that aggregates multiple aggregatable calls into a single [Multicall3] contract call. The calls
 * will be aggregated using as few calldata as possible, depending on the calls' properties. One of the following
 * aggregation functions will be used:
 * - [Multicall3.aggregate3Value], if any call is payable with non-zero value set,
 * - [Multicall3.aggregate3], if calls have mixed failure conditions (i.e. some allow failure, some don't),
 * - [Multicall3.tryAggregate], if all calls have the same failure condition, and are not payable or with zero value.
 *
 * Aggregate calls can be nested, i.e. an aggregate call can contain other aggregate calls.
 * */
class Multicall3AggregateCall<T> internal constructor(
    provider: Middleware,
    multicall3: Address,
) : ReadWriteContractCall<List<Result<T, ContractError>>, PendingTransaction, Multicall3AggregateCall<T>>(provider),
    Multicall3Aggregatable<List<Result<T, ContractError>>> {
    private val requests = ArrayList<AggregateRequest<T>>()
    private var lastRequestsSize = 0
    private var mixedFailureConditions = false
    private var anyPayable = false

    init {
        super.call.to = multicall3
    }

    override val self: Multicall3AggregateCall<T>
        get() = this

    override val call: CallRequest
        get() {
            val call = super.call

            // only re-encode data if new requests were added. It also short-circuits if there are no requests
            if (lastRequestsSize == requests.size) {
                return call
            }

            lastRequestsSize = requests.size

            // try to pack the calls using as few calldata as possible
            when {
                anyPayable -> {
                    var totalValue = BigInteger.ZERO
                    val arr = Array(requests.size) {
                        val req = requests[it]
                        val value = req.function.value ?: BigInteger.ZERO
                        totalValue += value

                        Multicall3.Call3Value(
                            req.function.to!!,
                            req.allowFailure,
                            value,
                            req.function.data ?: Bytes.EMPTY,
                        )
                    }

                    call.value = totalValue
                    call.data = Multicall3.FUNCTION_AGGREGATE3_VALUE.encodeCall(arrayOf(arr))
                }

                mixedFailureConditions -> {
                    val arr = Array(requests.size) {
                        val req = requests[it]
                        Multicall3.Call3(
                            req.function.to!!,
                            req.allowFailure,
                            req.function.data ?: Bytes.EMPTY,
                        )
                    }

                    call.data = Multicall3.FUNCTION_AGGREGATE3.encodeCall(arrayOf(arr))
                }

                else -> {
                    val allowFailure = requests[0].allowFailure

                    val arr = Array(requests.size) {
                        val req = requests[it]
                        Multicall3.Call(
                            req.function.to!!,
                            req.function.data ?: Bytes.EMPTY,
                        )
                    }

                    call.data = Multicall3.FUNCTION_TRY_AGGREGATE.encodeCall(arrayOf(!allowFailure, arr))
                }
            }

            return call
        }

    /**
     * Add a call to the aggregate call. Only [to], [value], [data] fields are used from the call.
     * */
    @JvmOverloads
    fun <RetType : T> addCall(
        call: Multicall3Aggregatable<RetType>,
        allowFailure: Boolean = false,
    ): CompletableFuture<Result<RetType, ContractError>> {
        // TODO replace with ConditionalCompletableFuture, but the flag needs to be set when
        //  the RPC request is actually sent
        val future = CompletableFuture<Result<RetType, ContractError>>()

        if (!mixedFailureConditions && requests.isNotEmpty()) {
            mixedFailureConditions = requests[0].allowFailure != allowFailure
        }

        if (!anyPayable) {
            anyPayable = call.value != null && call.value != BigInteger.ZERO
        }

        // safe cast, we're downcasting from R to T
        @Suppress("UNCHECKED_CAST")
        requests.add(
            AggregateRequest(
                call,
                allowFailure,
                future,
            ) as AggregateRequest<T>,
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
                ),
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
        val function: Multicall3Aggregatable<T>,
        val allowFailure: Boolean,
        val future: CompletableFuture<Result<T, ContractError>>,
    )
}

/**
 * A contract call that can be aggregated via [Multicall3] contract function call.
 * */
interface Multicall3Aggregatable<T> {
    val provider: Middleware

    val to: Address?
    val value: BigInteger?
    val data: Bytes?

    fun decodeCallResult(result: Bytes): Result<T, ContractError>

    /**
     * Aggregate this call via [Multicall3] contract call. Only [CallRequest.to], [CallRequest.value],
     * [CallRequest.data] fields are used from the call.
     *
     * Returns a future that will be completed with the result of the call.
     *
     * IMPORTANT: Do not await the future until the [aggregate] call is sent. Otherwise, the call will deadlock,
     * waiting for itself to complete.
     * */
    fun aggregate(
        aggregate: Multicall3AggregateCall<in T>,
        allowFailure: Boolean = false,
    ): CompletableFuture<Result<T, ContractError>> {
        return aggregate.addCall(this, allowFailure)
    }
}

/**
 * Aggregate all calls into a [Multicall3] call. Only [CallRequest.to], [CallRequest.value], [CallRequest.data]
 * fields are used from the calls.
 * */
fun <T> Iterable<Multicall3Aggregatable<T>>.aggregate(allowFailure: Boolean = false): Multicall3AggregateCall<T> {
    val iter = iterator()
    if (!iter.hasNext()) {
        throw IllegalArgumentException("No calls to aggregate")
    }

    val first = iter.next()
    val call = Multicall3.newAggregation<T>(first.provider)
    call.addCall(first, allowFailure)

    for (req in iter) {
        call.addCall(req, allowFailure)
    }

    return call
}

/**
 * Aggregate all calls into a [Multicall3] call via provided [agg]. Only [CallRequest.to], [CallRequest.value],
 * [CallRequest.data] fields are used from the calls.
 * */
fun <AggType, RetType : AggType> Iterable<Multicall3Aggregatable<RetType>>.aggregate(
    agg: Multicall3AggregateCall<AggType>,
    allowFailure: Boolean = false,
): List<CompletableFuture<Result<RetType, ContractError>>> {
    val ret = ArrayList<CompletableFuture<Result<RetType, ContractError>>>()
    for (req in this) {
        ret.add(agg.addCall(req, allowFailure))
    }

    return ret
}
