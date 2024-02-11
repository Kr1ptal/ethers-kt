package io.ethers.abi.call

import io.ethers.abi.error.ContractError
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
 * An instance of this class is created via [Multicall3.newAggregation], or one of the [aggregate] extension functions.
 *
 * Aggregate calls can be nested, i.e. an aggregate call can contain other aggregate calls.
 *
 * Usage example:
 * ```kotlin
 *     val pool = UniswapV2Pair(provider, Address("0x0d4a11d5EEaaC28EC3F61d100daF4d40471f1852"))
 *
 *     // initialize a new aggregate call
 *     val agg = Multicall3.newAggregation(provider)
 *
 *     // add calls
 *     val name = pool.name().aggregate(agg)
 *     val symbol = pool.symbol().aggregate(agg)
 *     val decimals = pool.decimals().aggregate(agg)
 *
 *     // send the aggregate call
 *     agg.call(BlockId.LATEST).sendAwait()
 *
 *      // get the results of function calls
 *     println(name.get())
 *     println(symbol.get())
 *     println(decimals.get())
 * ```
 * */
class Multicall3AggregateCall internal constructor(
    provider: Middleware,
    multicall3: Address,
) : ReadWriteContractCall<Boolean, PendingTransaction, Multicall3AggregateCall>(provider),
    Multicall3Aggregatable<Boolean> {
    private val requests = ArrayList<AggregateRequest<*>>()
    private var lastRequestsSize = 0
    private var mixedFailureConditions = false
    private var anyPayable = false

    init {
        super.call.to = multicall3
    }

    override val self: Multicall3AggregateCall
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
    fun <T> addCall(
        call: Multicall3Aggregatable<T>,
        allowFailure: Boolean = false,
    ): CompletableFuture<Result<T, ContractError>> {
        // TODO replace with ConditionalCompletableFuture, but the flag needs to be set when
        //  the RPC request is actually sent
        val future = CompletableFuture<Result<T, ContractError>>()

        if (!mixedFailureConditions && requests.isNotEmpty()) {
            mixedFailureConditions = requests[0].allowFailure != allowFailure
        }

        if (!anyPayable) {
            anyPayable = call.value != null && call.value != BigInteger.ZERO
        }

        requests.add(
            AggregateRequest(
                call,
                allowFailure,
                future,
            )
        )
        return future
    }

    override fun handleCallResult(result: Bytes): Result<Boolean, ContractError> {
        @Suppress("UNCHECKED_CAST")
        val decoded = Multicall3.FUNCTION_AGGREGATE3_VALUE.decodeResponse(result)[0] as Array<Multicall3.Result>

        for (i in requests.indices) {
            val request = requests[i]
            val callResult = decoded[i]

            handleRequestResult(request, callResult)
        }

        return success(true)
    }

    private fun <T> handleRequestResult(request: AggregateRequest<T>, result: Multicall3.Result) {
        val res = if (result.success) {
            request.function.decodeCallResult(result.returnData)
        } else {
            failure(tryDecodingCallRevert(result.returnData))
        }

        request.future.complete(res)

    }

    private fun tryDecodingCallRevert(err: Bytes): ContractError {
        val contractError = ContractError.getOrNull(err)
        if (contractError != null) {
            return contractError
        }

        // if we can't decode the error, just return the raw bytes as hex
        return RevertError(err.toString())
    }

    override fun handleCallError(error: ContractError): ContractError {
        val failure = failure(error)

        // complete all futures with the same error
        for (i in requests.indices) {
            handleRequestError(requests[i], failure)
        }

        return error
    }

    private fun <T> handleRequestError(request: AggregateRequest<T>, error: Result<Nothing, ContractError>) {
        request.future.complete(error)
    }

    override fun handleSendResult(result: PendingTransaction) = result

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
        aggregate: Multicall3AggregateCall,
        allowFailure: Boolean = false,
    ): CompletableFuture<Result<T, ContractError>> {
        return aggregate.addCall(this, allowFailure)
    }
}

/**
 * Aggregate all calls into a [Multicall3] call. Only [CallRequest.to], [CallRequest.value], [CallRequest.data]
 * fields are used from the calls.
 *
 * NOTE: This function is intended to be used when the result of the calls are not needed. This is useful if you're
 * only interested in the side effects of the calls, either when sending/tracing the call or by adding additional
 * calls to the returned [Multicall3AggregateCall], which depend on the state changes made by the calls in [this]
 * Iterable.
 *
 * If you need the result of the calls, use the [aggregate] extension function which accepts a [Multicall3AggregateCall]
 * as an argument.
 * */
fun <T> Iterable<Multicall3Aggregatable<T>>.aggregate(allowFailure: Boolean = false): Multicall3AggregateCall {
    val iter = iterator()
    if (!iter.hasNext()) {
        throw IllegalArgumentException("No calls to aggregate")
    }

    val first = iter.next()
    val call = Multicall3.newAggregation(first.provider)
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
fun <T> Iterable<Multicall3Aggregatable<T>>.aggregate(
    agg: Multicall3AggregateCall,
    allowFailure: Boolean = false,
): List<CompletableFuture<Result<T, ContractError>>> {
    val ret = ArrayList<CompletableFuture<Result<T, ContractError>>>()
    for (req in this) {
        ret.add(agg.addCall(req, allowFailure))
    }

    return ret
}
