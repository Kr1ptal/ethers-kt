package io.ethers.ens

import io.ethers.core.types.Address
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A cache of ENS name to [Address] resolutions, used by [EnsResolver].
 *
 * Resolving one name costs multiple round trips (registry lookup, interface probes, and the resolution itself),
 * while ENS records change rarely, so caching for a short window removes almost all of that traffic.
 *
 * The default implementation is [TtlEnsNameCache]. Implement this interface to back the cache with something
 * else, e.g. a size-bounded cache or one shared across processes. Implementations must be thread-safe, since a
 * single [EnsResolver] resolves names concurrently.
 * */
interface EnsNameCache {
    /**
     * Get the cached address for [name], or null if there is no valid cached entry.
     * */
    fun get(name: String): Address?

    /**
     * Cache [address] as the resolution of [name].
     * */
    fun put(name: String, address: Address)

    /**
     * Drop every cached entry.
     * */
    fun clear()

    companion object {
        /**
         * Create the default [TtlEnsNameCache], which keeps each entry for [ttl].
         * */
        operator fun invoke(
            ttl: Duration = 5.minutes,
            timeSource: TimeSource = TimeSource.Monotonic,
        ): EnsNameCache = TtlEnsNameCache(ttl, timeSource)
    }
}

/**
 * An unbounded [EnsNameCache] that keeps each entry for [ttl]. Expired entries are evicted when read.
 * */
class TtlEnsNameCache(
    private val ttl: Duration = 5.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : EnsNameCache {
    private val lock = SynchronizedObject()
    private val entries = HashMap<String, Entry>()

    private class Entry(val address: Address, val resolvedAt: TimeMark)

    override fun get(name: String): Address? = synchronized(lock) {
        val entry = entries[name] ?: return null
        if (entry.resolvedAt.elapsedNow() >= ttl) {
            entries.remove(name)
            return null
        }
        entry.address
    }

    override fun put(name: String, address: Address): Unit = synchronized(lock) {
        entries[name] = Entry(address, timeSource.markNow())
    }

    override fun clear(): Unit = synchronized(lock) { entries.clear() }
}
