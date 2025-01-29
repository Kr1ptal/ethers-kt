package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonParser
import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.asTypeOrNull
import io.ethers.core.isFailure
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.inf
import io.ethers.providers.AsyncExecutor
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function

private val DEFAULT_POLLER_INTERVAL = Duration.ofSeconds(7)

/**
 * Filter poller that polls a filter based on its [id] for changes and returns a stream of events. It behaves like a
 * [ChannelReceiver].
 * */
class FilterPoller<T : Any>(
    private val id: String,
    private val provider: Middleware,
    private val decoder: Function<JsonParser, List<T>>,
) : ChannelReceiver<T> {
    private val LOG = getLogger()
    private var interval = DEFAULT_POLLER_INTERVAL

    @Volatile
    private var unsubscribed = false
    private val initialized = AtomicBoolean(false)
    private val channel = QueueChannel.spscUnbounded<T> { unsubscribed = true }

    init {
        initializePoller()
    }

    /**
     * Map results of the poller to a different type.
     * */
    fun <R : Any> mapPoller(mapper: Function<List<T>, List<R>>): FilterPoller<R> {
        return FilterPoller(id, provider, decoder.andThen(mapper))
    }

    /**
     * Set polling interval. Default is 7 seconds.
     * */
    fun withInterval(interval: Duration): FilterPoller<T> {
        this.interval = interval
        return this
    }

    override fun onStateChange(listener: Runnable) {
        channel.onStateChange(listener)
    }

    override fun forEach(consumer: Consumer<in T>) {
        channel.forEach(consumer)
    }

    override fun take(): T? {
        return channel.take()
    }

    override fun poll(): T? {
        return channel.poll()
    }

    override val isClosed: Boolean
        get() = channel.isClosed

    override val size: Int
        get() = channel.size

    override fun close() {
        channel.close()
    }

    /**
     * Initialize a polling thread that will poll the filter for changes every [interval] seconds. Subsequent calls to
     * this function have no effect.
     *
     * When the [close] method is called, the polling thread will be stopped and the filter will be uninstalled
     * on the host.
     * */
    private fun initializePoller() {
        if (initialized.compareAndSet(false, true)) {
            val thread = AsyncExecutor.maybeVirtualThread {
                val getChangesCall = RpcCall(provider.client, "eth_getFilterChanges", arrayOf(id), decoder)

                var filterExists = true
                while (!unsubscribed) {
                    val response = getChangesCall.sendAwait()
                    if (response.isFailure()) {
                        LOG.err { "Error polling filter '$id': ${response.error}" }

                        // The filters persist on the node for some time if there were no polling requests made to it.
                        // In case the connection drops, the filter will keep accumulating changes on the node and will
                        // return all of them at the next polling request. If the filter is not found, it means it has
                        // expired, and we should stop polling it.

                        val error = response.error.asTypeOrNull<RpcError>()
                        if (error?.message?.contains("filter not found") == true) {
                            LOG.err { "Filter '$id' expired, stopping polling thread and unsubscribing" }

                            // need to unsubscribe via stream so its loop gets terminated
                            channel.close()
                            filterExists = false
                            break
                        }
                    } else {
                        val result = response.unwrap()
                        for (i in result.indices) {
                            channel.offer(result[i])
                        }
                    }

                    Thread.sleep(interval.toMillis())
                }

                if (filterExists) {
                    val uninstallCall = RpcCall(
                        provider.client,
                        "eth_uninstallFilter",
                        arrayOf(id),
                        Boolean::class.java,
                    )

                    val response = uninstallCall.sendAwait()
                    if (response.isFailure()) {
                        LOG.err { "Error uninstalling filter '$id': ${response.error}" }
                    } else {
                        LOG.inf { "Uninstalled filter '$id'" }
                    }
                }
            }

            thread.name = "FilterPoller-$id"
            thread.isDaemon = true
            thread.start()
        }
    }
}
