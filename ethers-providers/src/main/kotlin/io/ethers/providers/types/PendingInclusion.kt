package io.ethers.providers.types

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
    fun inclusion(): CompletableFuture<RpcResponse<T>> {
        return inclusion(DEFAULT_RETRIES, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined).
     *
     * @param retries number of attempts to receive a transaction inclusion response
     */
    fun inclusion(retries: Int): CompletableFuture<RpcResponse<T>> {
        return inclusion(retries, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined).
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     */
    fun inclusion(retries: Int, interval: Duration): CompletableFuture<RpcResponse<T>> {
        return inclusion(retries, interval, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined).
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
     */
    fun inclusion(retries: Int, interval: Duration, confirmations: Int): CompletableFuture<RpcResponse<T>> {
        return CompletableFuture.supplyAsync { awaitInclusion(retries, interval, confirmations) }
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     */
    fun awaitInclusion(): RpcResponse<T> {
        return awaitInclusion(DEFAULT_RETRIES, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     */
    fun awaitInclusion(retries: Int): RpcResponse<T> {
        return awaitInclusion(retries, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     */
    fun awaitInclusion(retries: Int, interval: Duration): RpcResponse<T> {
        return awaitInclusion(retries, interval, DEFAULT_CONFIRMATIONS)
    }

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
     */
    fun awaitInclusion(retries: Int, interval: Duration, confirmations: Int): RpcResponse<T>
}
