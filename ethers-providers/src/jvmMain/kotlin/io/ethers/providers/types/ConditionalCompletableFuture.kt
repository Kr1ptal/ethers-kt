package io.ethers.providers.types

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [CompletableFuture] that throws an exception if awaited before the [canAwait] flag is set to true.
 *
 * This is useful for ensuring that a request has been sent before awaiting the result, which would otherwise
 * deadlock the calling thread.
 * */
internal class ConditionalCompletableFuture<T>(private val canAwait: AtomicBoolean) : CompletableFuture<T>() {
    override fun get(): T {
        verifyCanAwait()
        return super.get()
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
        verifyCanAwait()
        return super.get(timeout, unit)
    }

    override fun getNow(valueIfAbsent: T): T {
        verifyCanAwait()
        return super.getNow(valueIfAbsent)
    }

    override fun join(): T {
        verifyCanAwait()
        return super.join()
    }

    private fun verifyCanAwait() {
        if (!canAwait.get()) {
            throw IllegalStateException("Request has not been sent yet. Awaiting would block indefinitely.")
        }
    }
}
