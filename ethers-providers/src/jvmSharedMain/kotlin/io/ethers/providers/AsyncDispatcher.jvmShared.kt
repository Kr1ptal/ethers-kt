package io.ethers.providers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The virtual-thread executor is intentionally shared for the lifetime of the library. It creates no persistent
 * worker threads, while individual provider jobs retain responsibility for cancellation and lifecycle management.
 *
 * Shared by the JVM and Android targets. Android has no virtual threads, so the lookup below fails there and the
 * dispatcher falls back to [Dispatchers.IO] - the same result the dedicated Android actual used to return.
 */
internal actual val asyncDispatcher: CoroutineDispatcher by lazy {
    virtualThreadExecutorOrNull()?.asCoroutineDispatcher() ?: Dispatchers.IO
}

/**
 * Returns `Executors.newVirtualThreadPerTaskExecutor()` on JDK 21+, or null where it is unavailable (older JDKs
 * and Android). Looked up reflectively so the module keeps compiling against a lower JDK target.
 */
private fun virtualThreadExecutorOrNull(): ExecutorService? = runCatching {
    Executors::class.java
        .getMethod("newVirtualThreadPerTaskExecutor")
        .invoke(null) as ExecutorService
}.getOrNull()
