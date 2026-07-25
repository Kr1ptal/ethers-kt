package io.ethers.providers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The virtual-thread executor is intentionally shared for the lifetime of the library. It creates no persistent
 * worker threads, while individual provider jobs retain responsibility for cancellation and lifecycle management.
 */
internal actual val asyncDispatcher: CoroutineDispatcher by lazy {
    virtualThreadExecutorOrNull()?.asCoroutineDispatcher() ?: Dispatchers.IO
}

private fun virtualThreadExecutorOrNull(): ExecutorService? = runCatching {
    Executors::class.java
        .getMethod("newVirtualThreadPerTaskExecutor")
        .invoke(null) as ExecutorService
}.getOrNull()
