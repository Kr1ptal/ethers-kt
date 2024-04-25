package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import io.ethers.core.asTypeOrNull
import io.ethers.core.isFailure
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.inf
import io.ethers.providers.BlockingSubscriptionStream
import io.ethers.providers.RpcError
import io.ethers.providers.SubscriptionStream
import io.ethers.providers.middleware.Middleware
import java.time.Duration
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

/**
 * Filter poller that polls a filter based on its [id] for changes and returns a stream of events. It behaves like a
 * [SubscriptionStream].
 * */
class FilterPoller<T>(
    private val id: String,
    private val provider: Middleware,
    private val decoder: Function<JsonParser, List<T>>,
) : SubscriptionStream<T>() {
    private val LOG = getLogger()
    private var interval = DEFAULT_POLLER_INTERVAL
    private var threadFactory = POLLER_DAEMON_FACTORY

    @Volatile
    private var unsubscribed = false
    private val initialized = AtomicBoolean(false)
    private val stream = BlockingSubscriptionStream.singleProducer<T> { unsubscribed = true }

    /**
     * Map results of the poller to a different type.
     * */
    fun <R> mapPoller(mapper: Function<List<T>, List<R>>): FilterPoller<R> {
        return FilterPoller(id, provider, decoder.andThen(mapper))
    }

    /**
     * Set polling interval. Default is 7 seconds. After calling any of iteration functions, this function has
     * no effect.
     *
     * Iteration functions: [iterator], [forEach], [forEachAsync].
     * */
    fun withInterval(interval: Duration): FilterPoller<T> {
        this.interval = interval
        return this
    }

    /**
     * Set thread factory for the polling thread. Default is [POLLER_DAEMON_FACTORY]. After calling any of iteration
     * functions, this function has no effect.
     *
     * Iteration functions: [iterator], [forEach], [forEachAsync].
     * */
    fun withThreadFactory(threadFactory: ThreadFactory): FilterPoller<T> {
        this.threadFactory = threadFactory
        return this
    }

    override fun unsubscribe() {
        stream.unsubscribe()
    }

    override fun iterator(): Iterator<T> {
        val iter = stream.iterator()
        return object : Iterator<T> {
            init {
                initializePoller()
            }

            override fun hasNext() = iter.hasNext()
            override fun next() = iter.next()
        }
    }

    /**
     * Initialize a polling thread (created using [threadFactory]) that will poll the filter for changes every
     * [interval] seconds. Subsequent calls to this function have no effect.
     *
     * When the [unsubscribe] method is called, the polling thread will be stopped and the filter will be uninstalled
     * on the host.
     * */
    private fun initializePoller() {
        if (initialized.compareAndSet(false, true)) {
            threadFactory.newThread {
                val intervalMs = interval.toMillis()
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
                            stream.unsubscribe()
                            filterExists = false
                            break
                        }
                    } else {
                        val result = response.unwrap()
                        for (i in result.indices) {
                            stream.pushEvent(result[i])
                        }
                    }

                    Thread.sleep(intervalMs)
                }

                if (filterExists) {
                    val uninstallCall = RpcCall(
                        provider.client,
                        "eth_uninstallFilter",
                        arrayOf(id),
                    ) { it.currentToken() == JsonToken.VALUE_TRUE }

                    val response = uninstallCall.sendAwait()
                    if (response.isFailure()) {
                        LOG.err { "Error uninstalling filter '$id': ${response.error}" }
                    } else {
                        LOG.inf { "Uninstalled filter '$id'" }
                    }
                }
            }.start()
        }
    }
}

private val DEFAULT_POLLER_INTERVAL = Duration.ofSeconds(7)

private val POLLER_DAEMON_FACTORY = ThreadFactory { r ->
    Thread(r).apply {
        name = "FilterPoller-$id"
        isDaemon = true
    }
}
