package io.ethers.abi

import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.core.types.Topic
import io.ethers.providers.SubscriptionStream
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.FilterPoller
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcResponse
import io.ethers.providers.types.RpcSubscribe

/**
 * Filter for non-anonymous events.
 * */
class EventFilter<T : ContractEvent>(
    provider: Middleware,
    factory: EventFactory<T>,
) : EventFilterBase<T, EventFilter<T>>(provider, factory) {
    override val self: EventFilter<T>
        get() = this

    init {
        require(!factory.abi.isAnonymous) { "Cannot create EventFilter for anonymous event. Use AnonymousEventFilter instead." }

        filter.topic0(factory.abi.topicId)
    }
}

/**
 * Filter for anonymous events.
 * */
class AnonymousEventFilter<T : ContractEvent>(
    provider: Middleware,
    factory: EventFactory<T>,
) : EventFilterBase<T, AnonymousEventFilter<T>>(provider, factory) {
    override val self: AnonymousEventFilter<T>
        get() = this

    init {
        require(factory.abi.isAnonymous) { "Cannot create AnonymousEventFilter for non-anonymous event. Use EventFilter instead." }
    }

    fun topic0(value: Hash): AnonymousEventFilter<T> {
        filter.topic1(value)
        return this
    }

    fun topic0(vararg value: Hash): AnonymousEventFilter<T> {
        filter.topic1(*value)
        return this
    }

    fun topic0(value: Topic): AnonymousEventFilter<T> {
        filter.topic1(value)
        return this
    }
}

abstract class EventFilterBase<T : ContractEvent, F : EventFilterBase<T, F>>(
    private val provider: Middleware,
    private val factory: EventFactory<T>,
) {
    protected val filter: LogFilter = LogFilter()

    /**
     * Watch for events matching this filter. Compared to [subscribe], this function installs a filter and intermittently
     * polls it for new events. It can be used to achieve streaming-like behavior if the provider does not support
     * subscriptions. If the provider supports subscriptions, [subscribe] should be used instead.
     * */
    fun watch(): RpcRequest<FilterPoller<T>> {
        return provider.watchLogs(filter).map { poller ->
            RpcResponse.result(poller.mapPoller(::decodeMatchingLogs))
        }
    }

    /**
     * Subscribe to events matching this filter. This function should be used instead of [watch] if the provider supports
     * subscriptions.
     * */
    fun subscribe(): RpcSubscribe<T> {
        return provider.subscribeLogs(filter).map { stream ->
            // safe cast because we filtered nulls
            @Suppress("UNCHECKED_CAST")
            val ret = stream.map { factory.decode(it) }.filter { it != null } as SubscriptionStream<T>
            RpcResponse.result(ret)
        }
    }

    /**
     * Query for events matching this filter.
     * */
    fun query(): RpcRequest<List<T>> {
        return provider.getLogs(filter).map { RpcResponse.result(decodeMatchingLogs(it)) }
    }

    /**
     * Decode matching logs into events. It's possible for the log to have the same topic0 but represent a different
     * event.
     *
     * The following two events are decoded differently but have the same topic0:
     * ```solidity
     * event Transfer(address indexed from, address indexed to, uint256 value);
     * event Transfer(address indexed from, address indexed to, uint256 indexed tokenId);
     * ```
     * */
    private fun decodeMatchingLogs(logs: List<Log>): List<T> {
        val ret = ArrayList<T>(logs.size)
        for (i in logs.indices) {
            val event = factory.decode(logs[i]) ?: continue
            ret.add(event)
        }
        return ret
    }

    protected abstract val self: F

    fun blockRange(from: Long, to: Long): F {
        filter.blockRange(from, to)
        return self
    }

    fun blockRange(from: BlockId.Number, to: BlockId.Number): F {
        filter.blockRange(from, to)
        return self
    }

    fun blockRange(from: BlockId.Name, to: BlockId.Name): F {
        filter.blockRange(from, to)
        return self
    }

    fun blockRange(from: BlockId.Number, to: BlockId.Name): F {
        filter.blockRange(from, to)
        return self
    }

    fun blockRange(from: BlockId.Name, to: BlockId.Number): F {
        filter.blockRange(from, to)
        return self
    }

    fun atBlock(hash: Hash): F {
        filter.atBlock(hash)
        return self
    }

    fun atBlock(hash: BlockId.Hash): F {
        filter.atBlock(hash)
        return self
    }

    fun address(address: Address): F {
        filter.address(address)
        return self
    }

    fun address(addresses: List<Address>): F {
        filter.address(addresses)
        return self
    }

    fun topic1(value: Hash): F {
        filter.topic1(value)
        return self
    }

    fun topic1(vararg value: Hash): F {
        filter.topic1(*value)
        return self
    }

    fun topic1(value: Topic): F {
        filter.topic1(value)
        return self
    }

    fun topic2(value: Hash): F {
        filter.topic2(value)
        return self
    }

    fun topic2(vararg value: Hash): F {
        filter.topic2(*value)
        return self
    }

    fun topic2(value: Topic): F {
        filter.topic2(value)
        return self
    }

    fun topic3(value: Hash): F {
        filter.topic3(value)
        return self
    }

    fun topic3(vararg value: Hash): F {
        filter.topic3(*value)
        return self
    }

    fun topic3(value: Topic): F {
        filter.topic3(value)
        return self
    }
}
