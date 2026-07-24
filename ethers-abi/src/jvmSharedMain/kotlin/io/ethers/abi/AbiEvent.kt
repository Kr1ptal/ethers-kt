package io.ethers.abi

import io.ethers.abi.AbiEvent.Companion.NON_VALUE_INDEXED_TYPE
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
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

    fun decode(log: Log): T? = decodeOrNull(log)

    /**
     * Decode the [log] into this event type, returning null if the log does not match this event or decoding fails.
     *
     * @return the decoded event, or null if the log does not match this event or cannot be decoded.
     * */
    fun decodeOrNull(log: Log): T? {
        val topics: List<Any>
        val data: List<Any>
        val logTopicSize: Int

        if (abi.isAnonymous) {
            logTopicSize = log.topics.size
            if (abi.indexed.size != logTopicSize) return null
            if (abi.data.isEmpty() != log.data.isEmpty) return null

            try {
                topics = if (logTopicSize == 0) emptyList() else abi.decodeTopics(log.topics)
                data = if (abi.data.isEmpty()) emptyList() else abi.decodeData(log.data)
            } catch (_: Exception) {
                return null
            }
        } else {
            logTopicSize = log.topics.size - 1
            if (abi.indexed.size != logTopicSize) return null
            if (abi.data.isEmpty() != log.data.isEmpty) return null
            if (abi.topicId != log.topics[0]) return null

            try {
                topics = if (logTopicSize == 0) emptyList() else abi.decodeTopics(log.topics)
                data = if (abi.data.isEmpty()) emptyList() else abi.decodeData(log.data)
            } catch (_: Exception) {
                return null
            }
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

        return try {
            decode(log, merged)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try to decode the [log] into this event type.
     *
     * @return the decoded event, or an [EventDecodingError] if the log does not match or cannot be decoded.
     * */
    fun tryDecode(log: Log): Result<T, EventDecodingError> {
        val topics: List<Any>
        val data: List<Any>
        val logTopicSize: Int

        if (abi.isAnonymous) {
            logTopicSize = log.topics.size
            if (abi.indexed.size != logTopicSize) return failure(EventDecodingError.NoMatchingEvent(log, listOf(abi)))
            if (abi.data.isEmpty() != log.data.isEmpty) return failure(EventDecodingError.NoMatchingEvent(log, listOf(abi)))

            try {
                topics = if (logTopicSize == 0) emptyList() else abi.decodeTopics(log.topics)
                data = if (abi.data.isEmpty()) emptyList() else abi.decodeData(log.data)
            } catch (e: Exception) {
                return failure(EventDecodingError.MalformedEvent(log, abi, e))
            }
        } else {
            logTopicSize = log.topics.size - 1
            if (abi.indexed.size != logTopicSize) return failure(EventDecodingError.NoMatchingEvent(log, listOf(abi)))
            if (abi.data.isEmpty() != log.data.isEmpty) return failure(EventDecodingError.NoMatchingEvent(log, listOf(abi)))
            if (abi.topicId != log.topics[0]) return failure(EventDecodingError.NoMatchingEvent(log, listOf(abi)))

            try {
                topics = if (logTopicSize == 0) emptyList() else abi.decodeTopics(log.topics)
                data = if (abi.data.isEmpty()) emptyList() else abi.decodeData(log.data)
            } catch (e: Exception) {
                return failure(EventDecodingError.MalformedEvent(log, abi, e))
            }
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

        return try {
            success(decode(log, merged))
        } catch (e: Exception) {
            failure(EventDecodingError.MalformedEvent(log, abi, e))
        }
    }

    fun decode(log: Log, data: List<Any>): T
}

sealed class EventDecodingError(
    open val log: Log,
    open val msg: String,
    open val cause: Exception? = null,
) : Result.Error {
    data class NoMatchingEvent(
        override val log: Log,
        val expectedEvents: List<AbiEvent>,
    ) : EventDecodingError(log, "Log does not match any expected event")

    data class MalformedEvent(
        override val log: Log,
        val expectedEvent: AbiEvent,
        override val cause: Exception,
    ) : EventDecodingError(log, "Unable to decode event log", cause)

    override fun doThrow(): Nothing {
        throw RuntimeException(msg, cause)
    }
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
fun <T : ContractEvent> List<EventFactory<out T>>.decode(log: Log): T? = decodeOrNull(log)

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event or decoding fails.
 * */
fun <T : ContractEvent> List<EventFactory<out T>>.decodeOrNull(log: Log): T? {
    for (i in indices) {
        return this[i].decodeOrNull(log) ?: continue
    }
    return null
}

/**
 * Try to decode the [Log] into any event of `this` types.
 * */
fun <T : ContractEvent> List<EventFactory<out T>>.tryDecode(log: Log): Result<T, EventDecodingError> {
    for (i in indices) {
        when (val event = this[i].tryDecode(log)) {
            is Result.Success -> return success(event.value)
            is Result.Failure -> {
                if (event.error is EventDecodingError.MalformedEvent) {
                    return failure(event.error)
                }
            }
        }
    }
    return failure(EventDecodingError.NoMatchingEvent(log, map { it.abi }))
}

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event.
 * */
fun <T : ContractEvent> Array<out EventFactory<out T>>.decode(log: Log): T? = decodeOrNull(log)

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event or decoding fails.
 * */
fun <T : ContractEvent> Array<out EventFactory<out T>>.decodeOrNull(log: Log): T? {
    for (i in this) {
        return i.decodeOrNull(log) ?: continue
    }
    return null
}

/**
 * Try to decode the [Log] into any event of `this` types.
 * */
fun <T : ContractEvent> Array<out EventFactory<out T>>.tryDecode(log: Log): Result<T, EventDecodingError> {
    for (i in this) {
        when (val event = i.tryDecode(log)) {
            is Result.Success -> return success(event.value)
            is Result.Failure -> {
                if (event.error is EventDecodingError.MalformedEvent) {
                    return failure(event.error)
                }
            }
        }
    }
    return failure(EventDecodingError.NoMatchingEvent(log, map { it.abi }))
}

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event.
 * */
fun <T : ContractEvent> Iterable<EventFactory<out T>>.decode(log: Log): T? = decodeOrNull(log)

/**
 * Decode the [Log] into any event of `this` types, returning null if the log does not match the event or decoding fails.
 * */
fun <T : ContractEvent> Iterable<EventFactory<out T>>.decodeOrNull(log: Log): T? {
    for (i in this) {
        return i.decodeOrNull(log) ?: continue
    }
    return null
}

/**
 * Try to decode the [Log] into any event of `this` types.
 * */
fun <T : ContractEvent> Iterable<EventFactory<out T>>.tryDecode(log: Log): Result<T, EventDecodingError> {
    val expectedEvents = ArrayList<AbiEvent>()
    for (i in this) {
        expectedEvents.add(i.abi)
        when (val event = i.tryDecode(log)) {
            is Result.Success -> return success(event.value)
            is Result.Failure -> {
                if (event.error is EventDecodingError.MalformedEvent) {
                    return failure(event.error)
                }
            }
        }
    }
    return failure(EventDecodingError.NoMatchingEvent(log, expectedEvents))
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
    return event.decodeOrNull(this)
}

/**
 * Try to decode this [Log] into an event of the [event] type.
 * */
fun <T : ContractEvent> Log.tryToEvent(event: EventFactory<T>): Result<T, EventDecodingError> {
    return event.tryDecode(this)
}

/**
 * Decode the [Log] into any event of the [events] types, returning null if the log does not match any event.
 * */
fun <T : ContractEvent> Log.toEventOrNull(events: List<EventFactory<out T>>): T? {
    return events.decodeOrNull(this)
}

/**
 * Try to decode this [Log] into any event of the [events] types.
 * */
fun <T : ContractEvent> Log.tryToEvent(events: List<EventFactory<out T>>): Result<T, EventDecodingError> {
    return events.tryDecode(this)
}

/**
 * Decode the [Log] into any event of the [events] types, returning null if the log does not match any event.
 * */
fun <T : ContractEvent> Log.toEventOrNull(events: Iterable<EventFactory<out T>>): T? {
    return events.decodeOrNull(this)
}

/**
 * Try to decode this [Log] into any event of the [events] types.
 * */
fun <T : ContractEvent> Log.tryToEvent(events: Iterable<EventFactory<out T>>): Result<T, EventDecodingError> {
    return events.tryDecode(this)
}

/**
 * Decode the [Log] into any event of the [events] types, returning null if the log does not match any event.
 * */
fun <T : ContractEvent> Log.toEventOrNull(vararg events: EventFactory<out T>): T? {
    return events.decodeOrNull(this)
}

/**
 * Try to decode this [Log] into any event of the [events] types.
 * */
fun <T : ContractEvent> Log.tryToEvent(vararg events: EventFactory<out T>): Result<T, EventDecodingError> {
    return events.tryDecode(this)
}
