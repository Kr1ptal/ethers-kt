package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import io.ethers.providers.types.RpcResponse
import org.jctools.queues.SpscUnboundedArrayQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import kotlin.concurrent.withLock

interface JsonPubSubClient : JsonRpcClient {
    /**
     * Subscribe to stream.
     *
     * @param params the subscription parameters
     * @param resultType class into which JSON result is converted
     */
    fun <T> subscribe(
        params: Array<*>,
        resultType: Class<T>,
    ): CompletableFuture<RpcResponse<SubscriptionStream<T>>> {
        return subscribe(params) { p -> p.readValueAs(resultType) }
    }

    /**
     * Subscribe to stream.
     *
     * @param params the subscription parameters
     * @param resultDecoder function to convert JSON result into return object [T]
     */
    fun <T> subscribe(
        params: Array<*>,
        resultDecoder: Function<JsonParser, T>,
    ): CompletableFuture<RpcResponse<SubscriptionStream<T>>>
}

private val STREAM_DAEMON_FACTORY = ThreadFactory { r ->
    Thread(r).apply {
        name = "SubscriptionStream-$id"
        isDaemon = true
    }
}

/**
 * A stream of events from a subscription. The stream can be terminated by calling [unsubscribe].
 * */
abstract class SubscriptionStream<T> {
    /**
     * Unsubscribe terminates the subscription stream. This method is idempotent.
     * */
    abstract fun unsubscribe()

    abstract operator fun iterator(): Iterator<T>

    fun forEach(consumer: Consumer<T>) = iterator().forEach { consumer.accept(it) }

    /**
     * Iterates over the stream on a separate thread to avoid blocking the caller.
     * */
    @JvmOverloads
    fun forEachAsync(threadFactory: ThreadFactory = STREAM_DAEMON_FACTORY, consumer: Consumer<T>) {
        threadFactory.newThread { forEach(consumer) }.start()
    }

    /**
     * Returns a stream consisting of the elements of this stream that match the given [predicate].
     * */
    fun filter(predicate: Predicate<T>): SubscriptionStream<T> {
        return FilterSubscriptionStream(this, predicate)
    }

    /**
     * Returns a stream consisting of the results of applying the given [mapper] function to the elements of this stream.
     * */
    fun <O> map(mapper: Function<T, O>): SubscriptionStream<O> {
        return MappingSubscriptionStream(this, mapper)
    }
}

/**
 * [SubscriptionStream] that blocks until an event is available.
 * */
class BlockingSubscriptionStream<T>(private val unsubscribeFunction: Runnable) : SubscriptionStream<T>() {
    // single producer, single consumer
    private val eventQueue = SpscUnboundedArrayQueue<T>(512)

    private val lock = ReentrantLock()
    private val newEventCondition = lock.newCondition()

    @Volatile
    private var unsubscribed = false

    fun pushEvent(event: T) {
        eventQueue.add(event)
        lock.withLock { newEventCondition.signal() }
    }

    override fun unsubscribe() {
        unsubscribeFunction.run()
        unsubscribed = true
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private var next: T? = null

            override fun hasNext(): Boolean {
                if (this.next != null) {
                    return true
                }

                var next: T? = null
                while (next == null) {
                    next = eventQueue.poll()

                    // check after polling, so we still drain the queue even if unsubscribed
                    if (unsubscribed) {
                        break
                    }

                    // if no next element, wait a bit to avoid CPU cycle burning
                    if (next == null) {
                        lock.withLock { newEventCondition.await(10, TimeUnit.MILLISECONDS) }
                    }
                }

                this.next = next
                return next != null
            }

            override fun next(): T {
                val ret = next ?: throw NoSuchElementException()
                next = null
                return ret
            }
        }
    }
}

/**
 * [SubscriptionStream] that filters events based on provided [predicate].
 * */
private class FilterSubscriptionStream<T>(
    private val source: SubscriptionStream<T>,
    private val predicate: Predicate<T>,
) : SubscriptionStream<T>() {
    override fun unsubscribe() = source.unsubscribe()

    override fun iterator(): Iterator<T> {
        val iter = source.iterator()

        return object : Iterator<T> {
            private var next: T? = null

            override fun hasNext(): Boolean {
                if (next != null) {
                    return true
                }

                // loop until we find a matching element or reach the end of the stream
                while (iter.hasNext()) {
                    val next = iter.next()

                    if (predicate.test(next)) {
                        this.next = next
                        return true
                    }
                }

                return false
            }

            override fun next(): T {
                val ret = next ?: throw NoSuchElementException()
                next = null
                return ret
            }
        }
    }
}

/**
 * [SubscriptionStream] that remaps events with [mapper] function.
 * */
private class MappingSubscriptionStream<I, O>(
    private val source: SubscriptionStream<I>,
    private val mapper: Function<I, O>,
) : SubscriptionStream<O>() {
    override fun unsubscribe() = source.unsubscribe()

    override fun iterator(): Iterator<O> {
        return object : Iterator<O> {
            private val iter = source.iterator()

            override fun hasNext(): Boolean {
                return iter.hasNext()
            }

            override fun next(): O {
                return mapper.apply(iter.next())
            }
        }
    }
}
