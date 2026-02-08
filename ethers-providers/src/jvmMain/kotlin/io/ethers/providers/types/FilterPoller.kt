package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonParser
import io.channels.core.Channel
import io.channels.core.ChannelConsumer
import io.channels.core.ChannelFunction
import io.channels.core.ChannelPredicate
import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.channels.core.blocking.NotificationHandle
import io.ethers.core.asTypeOrNull
import io.ethers.core.isFailure
import io.ethers.logger.dbg
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.inf
import io.ethers.providers.AsyncExecutor
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import java.time.Duration
import java.util.function.Function

private val DEFAULT_POLLER_INTERVAL = Duration.ofSeconds(7)

/**
 * A poller that periodically fetches new filter values via `eth_getFilterChanges` RPC call. Polling interval
 * can be adjusted via [withInterval] method.
 * */
class FilterPoller<T : Any> private constructor(
    private val poller: Poller<*>,
    private val channel: ChannelReceiver<T>,
) : ChannelReceiver<T> {
    constructor(
        id: String,
        provider: Middleware,
        decoder: Function<JsonParser, List<T>>,
    ) : this(id, provider, decoder, QueueChannel.spscUnbounded<T> { })

    private constructor(
        id: String,
        provider: Middleware,
        decoder: Function<JsonParser, List<T>>,
        channel: Channel<T>,
    ) : this(Poller(id, provider, decoder, channel), channel)

    /**
     * Set polling interval. Default is 7 seconds.
     * */
    fun withInterval(interval: Duration): FilterPoller<T> {
        poller.interval = interval
        return this
    }

    override val notificationHandle: NotificationHandle
        get() = channel.notificationHandle

    override fun forEach(consumer: ChannelConsumer<in T>) {
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

    override fun <R : Any> map(mapper: ChannelFunction<in T, R>): FilterPoller<R> {
        return FilterPoller<R>(poller, channel.map(mapper))
    }

    override fun <R : Any> mapNotNull(mapper: ChannelFunction<in T, R?>): FilterPoller<R> {
        return FilterPoller<R>(poller, channel.mapNotNull(mapper))
    }

    override fun filter(predicate: ChannelPredicate<in T>): FilterPoller<T> {
        return FilterPoller(poller, channel.filter(predicate))
    }

    /**
     * Initialize a polling thread that will poll the filter for changes every [interval] seconds.
     *
     * When the [close] method is called, the polling thread will be stopped and the filter will be uninstalled
     * on the host.
     * */
    private class Poller<T : Any>(
        private val id: String,
        private val provider: Middleware,
        private val decoder: Function<JsonParser, List<T>>,
        private val channel: Channel<T>,
    ) {
        private val LOG = getLogger()

        var interval: Duration = DEFAULT_POLLER_INTERVAL

        init {
            LOG.dbg { "Initializing poller for filter ID: $id" }

            val thread = AsyncExecutor.maybeVirtualThread {
                val getChangesCall = RpcCall(provider.client, "eth_getFilterChanges", arrayOf(id), decoder)

                var filterExists = true
                while (!channel.isClosed) {
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
