package io.ethers.abi

import io.ethers.abi.AbiEvent.Companion.NON_VALUE_INDEXED_TYPE
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.providers.middleware.Middleware

interface EventFactory<T : ContractEvent> {
    val abi: AbiEvent

    /**
     * Create a filter for this event to query/stream them from the [provider].
     *
     * NOTE: When implementing this function, change the return type to either [EventFilter], [AnonymousEventFilter],
     * or a custom filter class with type-specific filtering methods.
     * */
    fun filter(provider: Middleware): EventFilterBase<T, *>

    /**
     * Return whether the [log] is a valid event of this type.
     * */
    fun isLogValid(log: Log): Boolean {
        when {
            abi.isAnonymous -> {
                if (abi.indexed.size != log.topics.size) return false
                if (abi.data.isEmpty() != log.data.isEmpty) return false
                return true
            }

            else -> {
                if (abi.indexed.size != log.topics.size - 1) return false
                if (abi.data.isEmpty() != log.data.isEmpty) return false
                if (abi.topicId != log.topics[0]) return false
                return true
            }
        }
    }

    /**
     * Decode the [log] into this event type, returning null if the log does not match this event.
     *
     * @return the decoded event, or null if the log does not match this event.
     * */
    fun decode(log: Log): T? {
        val topics: List<Any>
        val data: List<Any>
        val logTopicSize: Int

        if (abi.isAnonymous) {
            logTopicSize = log.topics.size
            if (abi.indexed.size != logTopicSize) return null
            if (abi.data.isEmpty() != log.data.isEmpty) return null

            topics = if (logTopicSize == 0) emptyList() else abi.decodeTopics(log.topics)
            data = if (abi.data.isEmpty()) emptyList() else abi.decodeData(log.data)
        } else {
            logTopicSize = log.topics.size - 1
            if (abi.indexed.size != logTopicSize) return null
            if (abi.data.isEmpty() != log.data.isEmpty) return null
            if (abi.topicId != log.topics[0]) return null

            topics = if (logTopicSize == 0) emptyList() else abi.decodeTopics(log.topics)
            data = if (abi.data.isEmpty()) emptyList() else abi.decodeData(log.data)
        }

        var topicIndex = 0
        var dataIndex = 0
        val merged = ArrayList<Any>(logTopicSize + data.size)
        for (i in 0 until (logTopicSize + data.size)) {
            if (abi.tokens[i].indexed) {
                merged.add(topics[topicIndex++])
            } else {
                merged.add(data[dataIndex++])
            }
        }

        return decode(log, merged)
    }

    fun decode(log: Log, data: List<Any>): T
}

data class AbiEvent(
    val name: String,
    val tokens: List<Token>,
    val isAnonymous: Boolean,
) {
    val indexed = tokens.filter { it.indexed }.map { it.type }
    val data = tokens.filter { !it.indexed }.map { it.type }

    val topicId = Hash(AbiType.computeSignatureHash(name, tokens.map { it.type }))

    fun decodeTopics(topics: List<Hash>): List<Any> {
        val offsetTopic0 = if (isAnonymous) 0 else 1
        val ret = mutableListOf<Any>()
        for (i in indexed.indices) {
            ret.add(AbiCodec.decode(indexed[i], topics[i + offsetTopic0].asByteArray()))
        }
        return ret
    }

    fun decodeData(data: Bytes): List<Any> {
        return AbiCodec.decode(this.data, data.asByteArray())
    }

    data class Token(val type: AbiType<*>, val indexed: Boolean)

    companion object {
        @JvmField
        val NON_VALUE_INDEXED_TYPE = AbiType.FixedBytes(32)

        /**
         * Dynamic/array/tuple indexed parameters are stored as a hash of the actual value.
         * See: [docs](https://docs.soliditylang.org/en/latest/abi-spec.html#encoding-of-indexed-event-parameters)
         *
         * @return [type] if it can be used as an indexed parameter, or [NON_VALUE_INDEXED_TYPE] if it cannot.
         * */
        @JvmStatic
        fun getTopicAbiType(type: AbiType<*>): AbiType<*> {
            return if (type.isDynamic || type is AbiType.FixedArray<*> || type is AbiType.Tuple<*>) {
                NON_VALUE_INDEXED_TYPE
            } else {
                type
            }
        }
    }
}

/**
 * Decoded event from a [Log], exposing its metadata.
 * */
interface ContractEvent {
    val log: Log

    val address get() = log.address
    val blockNumber get() = log.blockNumber
    val blockHash get() = log.blockHash
    val transactionHash get() = log.transactionHash
    val transactionIndex get() = log.transactionIndex
    val logIndex get() = log.logIndex
    val removed get() = log.removed
}

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event.
 * */
fun <T : ContractEvent> List<EventFactory<out T>>.decode(log: Log): T? {
    for (i in indices) {
        val event = log.toEventOrNull(this[i])
        if (event != null) {
            return event
        }
    }
    return null
}

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event.
 * */
fun <T : ContractEvent> Array<out EventFactory<out T>>.decode(log: Log): T? {
    for (i in this) {
        val event = log.toEventOrNull(i)
        if (event != null) {
            return event
        }
    }
    return null
}

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event.
 * */
fun <T : ContractEvent> Iterable<EventFactory<out T>>.decode(log: Log): T? {
    for (i in this) {
        val event = log.toEventOrNull(i)
        if (event != null) {
            return event
        }
    }
    return null
}

/**
 * Return whether this [Log] is a valid event of the [event] type.
 * */
fun Log.isEvent(event: EventFactory<*>) = event.isLogValid(this)

/**
 * Return whether this [Log] is any valid event of the [events] types.
 * */
fun Log.isEvent(events: List<EventFactory<*>>): Boolean {
    for (i in events.indices) {
        if (this.isEvent(events[i])) {
            return true
        }
    }
    return false
}

/**
 * Return whether this [Log] is any valid event of the [events] types.
 * */
fun Log.isEvent(events: Iterable<EventFactory<*>>): Boolean {
    for (event in events) {
        if (this.isEvent(event)) {
            return true
        }
    }
    return false
}

/**
 * Return whether this [Log] is any valid event of the [events] types.
 * */
fun Log.isEvent(vararg events: EventFactory<*>): Boolean {
    for (event in events) {
        if (this.isEvent(event)) {
            return true
        }
    }
    return false
}

/**
 * Decode the [Log] into an event of the [event] type, returning null if the log does not match the event.
 * */
fun <T : ContractEvent> Log.toEventOrNull(event: EventFactory<T>): T? {
    return event.decode(this)
}

/**
 * Decode the [Log] into any event of the [events] types, returning null if the log does not match any event.
 * */
fun <T : ContractEvent> Log.toEventOrNull(events: List<EventFactory<out T>>): T? {
    for (i in events.indices) {
        val event = this.toEventOrNull(events[i])
        if (event != null) {
            return event
        }
    }
    return null
}

/**
 * Decode the [Log] into any event of the [events] types, returning null if the log does not match any event.
 * */
fun <T : ContractEvent> Log.toEventOrNull(events: Iterable<EventFactory<out T>>): T? {
    for (e in events) {
        val event = this.toEventOrNull(e)
        if (event != null) {
            return event
        }
    }
    return null
}

/**
 * Decode the [Log] into any event of the [events] types, returning null if the log does not match any event.
 * */
fun <T : ContractEvent> Log.toEventOrNull(vararg events: EventFactory<out T>): T? {
    for (e in events) {
        val event = this.toEventOrNull(e)
        if (event != null) {
            return event
        }
    }
    return null
}
