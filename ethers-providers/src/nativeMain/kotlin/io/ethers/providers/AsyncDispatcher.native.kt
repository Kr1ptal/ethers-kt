package io.ethers.providers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Native targets have neither virtual threads nor a public `Dispatchers.IO` - it exists in kotlinx-coroutines but
 * is declared `internal` for native - so provider tasks run on [Dispatchers.Default], a multi-threaded worker pool.
 *
 * The distinction matters less here than on the JVM: the Darwin engine is backed by NSURLSession, which completes
 * requests on its own queues rather than by blocking the calling thread.
 */
internal actual val asyncDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default
