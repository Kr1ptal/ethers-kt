package io.ethers.providers

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ThreadFactory

/**
 * Get the best executor for async operations of [io.ethers.providers.types.RpcRequest]s. It first tries to use a virtual thread executor, if not
 * available, it falls back to the same implementation as [java.util.concurrent.CompletableFuture.defaultExecutor].
 * */
internal object AsyncExecutor {
    private val USE_COMMON_POOL: Boolean = (ForkJoinPool.getCommonPoolParallelism() > 1)
    private val THREAD_FACTORY: ThreadFactory
    private val ASYNC_POOL: Executor

    init {
        val lookup = MethodHandles.lookup()
        val virtualThreadFactory = runCatching {
            val threadBuilderClass = Thread::class.java.getMethod("ofVirtual").returnType // Thread.Builder.OfVirtual

            val factory = MethodHandles.lookup().findVirtual(
                threadBuilderClass,
                "factory",
                MethodType.methodType(ThreadFactory::class.java) // The method returns a ThreadFactory
            ).bindTo(Thread::class.java.getMethod("ofVirtual").invoke(null))

            // invoke while catching to see if we need to set "--enable-preview" flag
            factory.invokeExact() as? ThreadFactory
        }.getOrNull()

        val virtualExecutor = runCatching {
            // if we don't have a virtual thread factory, we can't use a virtual executor
            if (virtualThreadFactory == null) {
                return@runCatching null
            }

            lookup.findStatic(
                Executors::class.java,
                "newVirtualThreadPerTaskExecutor",
                MethodType.methodType(ExecutorService::class.java)
            )
        }.getOrNull()?.invokeExact() as? ExecutorService

        THREAD_FACTORY = virtualThreadFactory ?: ThreadFactory { r -> Thread(r) }
        ASYNC_POOL = virtualExecutor ?: (if (USE_COMMON_POOL) ForkJoinPool.commonPool() else ThreadPerTaskExecutor())
    }

    /**
     * Get the executor that is used for async blocking IO operations, using either a virtual or platform threads.
     * */
    fun maybeVirtualExecutor(): Executor = ASYNC_POOL

    /**
     * Create either a virtual or platform thread, depending on the java version.
     * */
    fun maybeVirtualThread(runnable: Runnable): Thread {
        return THREAD_FACTORY.newThread(runnable)
    }

    private class ThreadPerTaskExecutor : Executor {
        override fun execute(r: Runnable) {
            Thread(r).start()
        }
    }
}
