package io.ethers.ens

import io.ethers.core.types.Address
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A TTL cache of ENS name to [Address] resolutions.
 *
 * Resolving one name costs multiple round trips (registry lookup, interface probes, and the resolution itself),
 * while ENS records change rarely, so caching for a short window removes almost all of that traffic.
 * */
class EnsNameCache(
    private val ttl: Duration = 5.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val lock = SynchronizedObject()
    private val entries = HashMap<String, Entry>()

    private class Entry(val address: Address, val resolvedAt: TimeMark)

    /**
     * Get the cached address for [name], or null if it was never cached or its ttl has elapsed.
     * */
    fun get(name: String): Address? = synchronized(lock) {
        val entry = entries[name] ?: return null
        if (entry.resolvedAt.elapsedNow() >= ttl) {
            entries.remove(name)
            return null
        }
        entry.address
    }

    /**
     * Cache [address] as the resolution of [name].
     * */
    fun put(name: String, address: Address): Unit = synchronized(lock) {
        entries[name] = Entry(address, timeSource.markNow())
    }

    /**
     * Drop every cached entry.
     * */
    fun clear(): Unit = synchronized(lock) { entries.clear() }
}
