package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.types.Hash
import java.time.Duration
import java.util.concurrent.CompletableFuture

private const val DEFAULT_RETRIES = 3
private val DEFAULT_INCLUSION_INTERVAL = Duration.ofSeconds(6)
private const val DEFAULT_CONFIRMATIONS = 1

/**
 * Result that is pending block inclusion (e.i. getting mined).
 * */
interface PendingInclusion<T> {
    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined).
     */
    fun inclusion(): CompletableFuture<Result<T, Error>> {
        return inclusion(DEFAULT_RETRIES, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined).
     *
     * @param retries number of attempts to receive a transaction inclusion response
     */
    fun inclusion(retries: Int): CompletableFuture<Result<T, Error>> {
        return inclusion(retries, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined).
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     */
    fun inclusion(retries: Int, interval: Duration): CompletableFuture<Result<T, Error>> {
        return inclusion(retries, interval, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined).
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
     */
    fun inclusion(retries: Int, interval: Duration, confirmations: Int): CompletableFuture<Result<T, Error>> {
        return CompletableFuture.supplyAsync { awaitInclusion(retries, interval, confirmations) }
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     */
    fun awaitInclusion(): Result<T, Error> {
        return awaitInclusion(DEFAULT_RETRIES, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     */
    fun awaitInclusion(retries: Int): Result<T, Error> {
        return awaitInclusion(retries, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     */
    fun awaitInclusion(retries: Int, interval: Duration): Result<T, Error> {
        return awaitInclusion(retries, interval, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
     */
    fun awaitInclusion(retries: Int, interval: Duration, confirmations: Int): Result<T, Error>

    sealed class Error : Result.Error {
        data class NoInclusion(val txHash: Hash, val retries: Int) : Error()
        data class RpcError(val txHash: Hash, val error: io.ethers.providers.RpcError) : Error()
    }
}
