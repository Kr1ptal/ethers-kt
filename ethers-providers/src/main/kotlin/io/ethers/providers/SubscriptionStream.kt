package io.ethers.providers

import io.ethers.providers.BlockingSubscriptionStream.Companion.multiProducer
import io.ethers.providers.BlockingSubscriptionStream.Companion.singleProducer
import org.jctools.queues.MpscUnboundedXaddArrayQueue
import org.jctools.queues.SpscUnboundedArrayQueue
import java.util.Queue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import kotlin.concurrent.withLock

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
    fun forEachAsync(
        threadFactory: ThreadFactory = STREAM_DAEMON_FACTORY,
        consumer: Consumer<T>,
    ): SubscriptionStream<T> {
        threadFactory.newThread { forEach(consumer) }.start()
        return this
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

    companion object {
        private val STREAM_DAEMON_FACTORY = ThreadFactory { r ->
            Thread(r).apply {
                name = "SubscriptionStream-$id"
                isDaemon = true
            }
        }
    }
}

/**
 * A single-consumer [SubscriptionStream] that blocks until an event is available. Depending on how it is created, it
 * can be single or multi producer. See [singleProducer]/[multiProducer] for more details.
 * */
class BlockingSubscriptionStream<T> private constructor(
    private val eventQueue: Queue<T>,
    private val unsubscribeFunction: Runnable,
) : SubscriptionStream<T>() {
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
        lock.withLock { newEventCondition.signal() }
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

                    // if no next element, wait until next event to avoid CPU cycle burning
                    if (next == null) {
                        lock.withLock { newEventCondition.await() }
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

    companion object {
        /**
         * Creates a single-producer, single-consumer [BlockingSubscriptionStream]. This stream is suitable for use in
         * scenarios where only a single thread will be pushing events into the stream, and a single thread reading
         * from it.
         * */
        @JvmStatic
        fun <T> singleProducer(unsubscribeFunction: Runnable): BlockingSubscriptionStream<T> {
            return BlockingSubscriptionStream(SpscUnboundedArrayQueue(512), unsubscribeFunction)
        }

        /**
         * Creates a multi-producer, single-consumer [BlockingSubscriptionStream]. This stream is suitable for use in
         * scenarios where multiple threads will be pushing events into the stream, and a single thread reading from it.
         * */
        @JvmStatic
        fun <T> multiProducer(unsubscribeFunction: Runnable): BlockingSubscriptionStream<T> {
            return BlockingSubscriptionStream(MpscUnboundedXaddArrayQueue(512), unsubscribeFunction)
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
