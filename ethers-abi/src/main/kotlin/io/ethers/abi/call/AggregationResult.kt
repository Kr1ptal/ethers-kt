package io.ethers.abi.call

import io.ethers.abi.error.ContractError
import io.ethers.core.Result

/**
 * Aggregation of multiple results, returned by [Multicall3] when aggregating multiple abi-generated calls.
 * */
class AggregationResult<T>(private val results: Array<Result<T, ContractError>>) : Iterable<Result<T, ContractError>> {
    operator fun get(index: Int): Result<T, ContractError> = results[index]

    /**
     * Get the result at [index] as [R].
     * */
    @Suppress("UNCHECKED_CAST")
    fun <R : T> getAs(index: Int): Result<R, ContractError> {
        return results[index] as Result<R, ContractError>
    }

    /**
     * Get the result at [index] as [AggregationResult] of type [R]. Useful for getting the results of nested
     * aggregate calls.
     * */
    @Suppress("UNCHECKED_CAST")
    fun <R> getAsAggregation(index: Int): Result<AggregationResult<R>, ContractError> {
        return results[index] as Result<AggregationResult<R>, ContractError>
    }

    override fun iterator(): Iterator<Result<T, ContractError>> {
        return results.iterator()
    }

    @get:JvmSynthetic
    val indices: IntRange
        get() = results.indices
}
