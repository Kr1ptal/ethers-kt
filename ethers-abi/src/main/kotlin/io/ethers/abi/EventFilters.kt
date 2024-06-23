package io.ethers.abi

import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.providers.RpcError
import io.ethers.providers.SubscriptionStream
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.FilterPoller
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import java.math.BigInteger

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

    /**
     * Filter events matching provided topic0 [hash].
     */
    fun topic0(hash: Hash): AnonymousEventFilter<T> {
        filter.topic0(hash)
        return this
    }

    /**
     * Filter events matching provided topic0 [value].
     */
    fun topic0(value: BigInteger): AnonymousEventFilter<T> {
        filter.topic0(Hash(value))
        return this
    }

    /**
     * Filter events matching provided topic0 [address].
     */
    fun topic0(address: Address): AnonymousEventFilter<T> {
        filter.topic0(Hash(address))
        return this
    }

    /**
     * Filter events matching any of provided topic0 [hashes].
     */
    fun topic0(vararg hashes: Hash): AnonymousEventFilter<T> {
        filter.topic0(*hashes)
        return this
    }

    /**
     * Filter events matching any of provided topic0 [values].
     */
    fun topic0(vararg values: BigInteger): AnonymousEventFilter<T> {
        filter.topic0(*Array(values.size) { Hash(values[it]) })
        return this
    }

    /**
     * Filter events matching any of provided topic0 [addresses].
     */
    fun topic0(vararg addresses: Address): AnonymousEventFilter<T> {
        filter.topic0(*Array(addresses.size) { Hash(addresses[it]) })
        return this
    }

    /**
     * Filter events matching any of provided topic0 [hashes].
     */
    fun topic0(hashes: Collection<Hash>): AnonymousEventFilter<T> {
        filter.topic0(hashes)
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
    fun watch(): RpcRequest<FilterPoller<T>, RpcError> {
        return provider.watchLogs(filter).map { it.mapPoller(::decodeMatchingLogs) }
    }

    /**
     * Subscribe to events matching this filter. This function should be used instead of [watch] if the provider supports
     * subscriptions.
     * */
    fun subscribe(): RpcSubscribe<T, RpcError> {
        return provider.subscribeLogs(filter).map { stream ->
            // safe cast because we filtered nulls
            @Suppress("UNCHECKED_CAST")
            stream.map { factory.decode(it) }.filter { it != null } as SubscriptionStream<T>
        }
    }

    /**
     * Query for events matching this filter.
     * */
    fun query(): RpcRequest<List<T>, RpcError> {
        return provider.getLogs(filter).map(::decodeMatchingLogs)
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

    /**
     * Filter events within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: Long, to: Long): F {
        filter.blockRange(from, to)
        return self
    }

    /**
     * Filter events within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Number, to: BlockId.Number): F {
        filter.blockRange(from, to)
        return self
    }

    /**
     * Filter events within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Name, to: BlockId.Name): F {
        filter.blockRange(from, to)
        return self
    }

    /**
     * Filter events within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Number, to: BlockId.Name): F {
        filter.blockRange(from, to)
        return self
    }

    /**
     * Filter events within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Name, to: BlockId.Number): F {
        filter.blockRange(from, to)
        return self
    }

    /**
     * Filter events at provided block [hash].
     */
    fun atBlock(hash: Hash): F {
        filter.atBlock(hash)
        return self
    }

    /**
     * Filter events at provided block [hash].
     */
    fun atBlock(hash: BlockId.Hash): F {
        filter.atBlock(hash)
        return self
    }

    /**
     * Filter events at provided block [number].
     */
    fun atBlock(number: Long): F {
        return blockRange(number, number)
    }

    /**
     * Filter events at provided block [number].
     */
    fun atBlock(number: BlockId.Number): F {
        return blockRange(number, number)
    }

    /**
     * Filter events at provided block [name].
     */
    fun atBlock(name: BlockId.Name): F {
        return blockRange(name, name)
    }

    /**
     * Filter events emitted from provided [address].
     */
    fun address(address: Address): F {
        filter.address(address)
        return self
    }

    /**
     * Filter events emitted from provided array of [addresses].
     */
    fun address(vararg addresses: Address): F {
        filter.address(*addresses)
        return self
    }

    /**
     * Filter events emitted from provided list of [addresses].
     */
    fun address(addresses: Collection<Address>): F {
        filter.address(addresses)
        return self
    }

    /**
     * Filter events matching provided topic1 [hash].
     */
    fun topic1(hash: Hash): F {
        filter.topic1(hash)
        return self
    }

    /**
     * Filter events matching provided topic1 [value].
     */
    fun topic1(value: BigInteger): F {
        filter.topic1(Hash(value))
        return self
    }

    /**
     * Filter events matching provided topic1 [address].
     */
    fun topic1(address: Address): F {
        filter.topic1(Hash(address))
        return self
    }

    /**
     * Filter events matching any of provided topic1 [hashes].
     */
    fun topic1(vararg hashes: Hash): F {
        filter.topic1(*hashes)
        return self
    }

    /**
     * Filter events matching any of provided topic1 [values].
     */
    fun topic1(vararg values: BigInteger): F {
        filter.topic1(*Array(values.size) { Hash(values[it]) })
        return self
    }

    /**
     * Filter events matching any of provided topic1 [addresses]
     */
    fun topic1(vararg addresses: Address): F {
        filter.topic1(*Array(addresses.size) { Hash(addresses[it]) })
        return self
    }

    /**
     * Filter events matching any of provided topic1 [hashes].
     */
    fun topic1(hashes: Collection<Hash>): F {
        filter.topic1(hashes)
        return self
    }

    /**
     * Filter events matching provided topic2 [hash].
     */
    fun topic2(hash: Hash): F {
        filter.topic2(hash)
        return self
    }

    /**
     * Filter events matching provided topic2 [value].
     */
    fun topic2(value: BigInteger): F {
        filter.topic2(Hash(value))
        return self
    }

    /**
     * Filter events matching provided topic2 [address].
     */
    fun topic2(address: Address): F {
        filter.topic2(Hash(address))
        return self
    }

    /**
     * Filter events matching any of provided topic2 [hashes].
     */
    fun topic2(vararg hashes: Hash): F {
        filter.topic2(*hashes)
        return self
    }

    /**
     * Filter events matching any of provided topic2 [values].
     */
    fun topic2(vararg values: BigInteger): F {
        filter.topic2(*Array(values.size) { Hash(values[it]) })
        return self
    }

    /**
     * Filter events matching any of provided topic2 [addresses]
     */
    fun topic2(vararg addresses: Address): F {
        filter.topic2(*Array(addresses.size) { Hash(addresses[it]) })
        return self
    }

    /**
     * Filter events matching any of provided topic2 [hashes].
     */
    fun topic2(hashes: Collection<Hash>): F {
        filter.topic2(hashes)
        return self
    }

    /**
     * Filter events matching provided topic3 [hash].
     */
    fun topic3(hash: Hash): F {
        filter.topic3(hash)
        return self
    }

    /**
     * Filter events matching provided topic3 [value].
     */
    fun topic3(value: BigInteger): F {
        filter.topic3(Hash(value))
        return self
    }

    /**
     * Filter events matching provided topic3 [address].
     */
    fun topic3(address: Address): F {
        filter.topic3(Hash(address))
        return self
    }

    /**
     * Filter events matching any of provided topic3 [hashes].
     */
    fun topic3(vararg hashes: Hash): F {
        filter.topic3(*hashes)
        return self
    }

    /**
     * Filter events matching any of provided topic3 [values].
     */
    fun topic3(vararg values: BigInteger): F {
        filter.topic3(*Array(values.size) { Hash(values[it]) })
        return self
    }

    /**
     * Filter events matching any of provided topic3 [addresses]
     */
    fun topic3(vararg addresses: Address): F {
        filter.topic3(*Array(addresses.size) { Hash(addresses[it]) })
        return self
    }

    /**
     * Filter events matching any of provided topic3 [hashes].
     */
    fun topic3(hashes: Collection<Hash>): F {
        filter.topic3(hashes)
        return self
    }
}
