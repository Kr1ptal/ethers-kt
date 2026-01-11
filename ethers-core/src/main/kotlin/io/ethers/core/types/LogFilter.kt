package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize

private typealias TopicHashes = Array<out Hash>

/**
 * Filter configuration for logs emitted by smart contracts.
 * */
@JsonSerialize(using = LogFilterSerializer::class)
class LogFilter() {
    /**
     * Create a copy of provided [filter].
     * */
    constructor(filter: LogFilter) : this() {
        blocks = filter.blocks
        addresses = filter.addresses?.copyOf()
        // inner arrays are not copied since they are replaced on each set
        topics = filter.topics?.copyOf()
    }

    /**
     * Target range of blocks to filter logs from. Defaults to latest block.
     * */
    var blocks: BlockSelector = BlockSelector.LATEST_BLOCK
        private set

    /**
     * Filter logs emitted by set addresses. If null, logs emitted by any addresses are included.
     * */
    var addresses: Array<out Address>? = null
        private set

    /**
     * Filter logs by set topics. If null, logs with any topics are included.
     *
     * Topics are ordered by index, where index 0 is topic0, index 1 is topic1, and so on. Each topic filter is an
     * array of hashes, where hashes are matched by OR, and topics are matched by AND.
     *
     * Example:
     * ```
     * [[h1, h2], [h3], null, null] ==> topic0 == (h1 || h2) && topic1 == (h3)
     * [[h1], null, [h2, h3], null] ==> topic0 == (h1) && topic2 == (h2 || h3)
     * ```
     */
    var topics: Array<TopicHashes?>? = null
        private set

    /**
     * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: Long, to: Long): LogFilter = blockRange(BlockId.Number(from), BlockId.Number(to))

    /**
     * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Number, to: BlockId.Number): LogFilter {
        blocks = BlockSelector.Range(from, to)
        return this
    }

    /**
     * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Name, to: BlockId.Name): LogFilter {
        blocks = BlockSelector.Range(from, to)
        return this
    }

    /**
     * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Number, to: BlockId.Name): LogFilter {
        blocks = BlockSelector.Range(from, to)
        return this
    }

    /**
     * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
     */
    fun blockRange(from: BlockId.Name, to: BlockId.Number): LogFilter {
        blocks = BlockSelector.Range(from, to)
        return this
    }

    /**
     * Filter logs at provided block [hash].
     */
    fun atBlock(hash: Hash): LogFilter {
        blocks = BlockSelector.Hash(hash)
        return this
    }

    /**
     * Filter logs at provided block [number].
     */
    fun atBlock(number: Long): LogFilter {
        return blockRange(number, number)
    }

    /**
     * Filter logs at provided block [blockId].
     */
    fun atBlock(blockId: BlockId): LogFilter {
        return when (blockId) {
            is BlockId.Hash -> atBlock(blockId.hash)
            is BlockId.Number -> blockRange(blockId, blockId)
            is BlockId.Name -> blockRange(blockId, blockId)
        }
    }

    /**
     * Filter logs emitted from provided [address].
     */
    fun address(address: Address): LogFilter {
        this.addresses = arrayOf(address)
        return this
    }

    /**
     * Filter logs emitted from provided array of [addresses].
     */
    fun address(vararg addresses: Address): LogFilter {
        this.addresses = arrayOf(*addresses)
        return this
    }

    /**
     * Filter logs emitted from provided list of [addresses].
     */
    fun address(addresses: Collection<Address>): LogFilter {
        this.addresses = addresses.toTypedArray()
        return this
    }

    /**
     * Get the topic0 filter, if any set.
     * */
    val topic0: TopicHashes?
        get() = topics?.get(0)

    /**
     * Filter logs matching provided topic0 [hash].
     */
    fun topic0(hash: Hash): LogFilter {
        getOrCreateTopics()[0] = arrayOf(hash)
        return this
    }

    /**
     * Filter logs matching any of provided topic0 [hashes].
     */
    fun topic0(vararg hashes: Hash): LogFilter {
        getOrCreateTopics()[0] = hashes
        return this
    }

    /**
     * Filter logs matching any of provided topic0 [hashes].
     */
    fun topic0(hashes: Collection<Hash>): LogFilter {
        getOrCreateTopics()[0] = hashes.toTypedArray()
        return this
    }

    /**
     * Get the topic1 filter, if any set.
     * */
    val topic1: TopicHashes?
        get() = topics?.get(1)

    /**
     * Filter logs matching provided topic1 [hash].
     */
    fun topic1(hash: Hash): LogFilter {
        getOrCreateTopics()[1] = arrayOf(hash)
        return this
    }

    /**
     * Filter logs matching any of provided topic1 [hashes].
     */
    fun topic1(vararg hashes: Hash): LogFilter {
        getOrCreateTopics()[1] = hashes
        return this
    }

    /**
     * Filter logs matching any of provided topic1 [hashes].
     */
    fun topic1(hashes: Collection<Hash>): LogFilter {
        getOrCreateTopics()[1] = hashes.toTypedArray()
        return this
    }

    /**
     * Get the topic2 filter, if any set.
     * */
    val topic2: TopicHashes?
        get() = topics?.get(2)

    /**
     * Filter logs matching provided topic2 [hash].
     */
    fun topic2(hash: Hash): LogFilter {
        getOrCreateTopics()[2] = arrayOf(hash)
        return this
    }

    /**
     * Filter logs matching any of provided topic2 [hashes].
     */
    fun topic2(vararg hashes: Hash): LogFilter {
        getOrCreateTopics()[2] = hashes
        return this
    }

    /**
     * Filter logs matching any of provided topic2 [hashes].
     */
    fun topic2(hashes: Collection<Hash>): LogFilter {
        getOrCreateTopics()[2] = hashes.toTypedArray()
        return this
    }

    /**
     * Get the topic3 filter, if any set.
     * */
    val topic3: TopicHashes?
        get() = topics?.get(3)

    /**
     * Filter logs matching provided topic3 [hash].
     */
    fun topic3(hash: Hash): LogFilter {
        getOrCreateTopics()[3] = arrayOf(hash)
        return this
    }

    /**
     * Filter logs matching any of provided topic3 [hashes].
     */
    fun topic3(vararg hashes: Hash): LogFilter {
        getOrCreateTopics()[3] = hashes
        return this
    }

    /**
     * Filter logs matching any of provided topic3 [hashes].
     */
    fun topic3(hashes: Collection<Hash>): LogFilter {
        getOrCreateTopics()[3] = hashes.toTypedArray()
        return this
    }

    private fun getOrCreateTopics(): Array<TopicHashes?> {
        val topics = topics
        if (topics != null) {
            return topics
        }

        return arrayOfNulls<TopicHashes>(4).also { this.topics = it }
    }

    override fun toString(): String {
        return "LogFilter(blockSelector=$blocks, address=${addresses?.contentToString()}, topics=${topics?.contentDeepToString()})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as LogFilter

        if (blocks != other.blocks) return false
        if (addresses != null) {
            if (other.addresses == null) return false
            if (!addresses.contentEquals(other.addresses)) return false
        } else if (other.addresses != null) {
            return false
        }
        if (topics != null) {
            if (other.topics == null) return false
            if (!topics.contentDeepEquals(other.topics)) return false
        } else if (other.topics != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = blocks.hashCode()
        result = 31 * result + (addresses?.contentHashCode() ?: 0)
        result = 31 * result + (topics?.contentDeepHashCode() ?: 0)
        return result
    }

    companion object {
        inline operator fun invoke(builder: LogFilter.() -> Unit): LogFilter {
            return LogFilter().apply(builder)
        }
    }
}

sealed interface BlockSelector {
    val from: BlockId
    val to: BlockId

    data class Hash(val hash: io.ethers.core.types.Hash) : BlockSelector {
        override val from = BlockId.Hash(hash)
        override val to = from
    }

    // must be either block number or name, or combination
    class Range private constructor(override val from: BlockId, override val to: BlockId) : BlockSelector {
        constructor(from: BlockId.Number, to: BlockId.Number) : this(from as BlockId, to as BlockId)
        constructor(from: BlockId.Number, to: BlockId.Name) : this(from as BlockId, to as BlockId)
        constructor(from: BlockId.Name, to: BlockId.Name) : this(from as BlockId, to as BlockId)
        constructor(from: BlockId.Name, to: BlockId.Number) : this(from as BlockId, to as BlockId)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Range

            if (from != other.from) return false
            if (to != other.to) return false

            return true
        }

        override fun hashCode(): Int {
            var result = from.hashCode()
            result = 31 * result + to.hashCode()
            return result
        }

        override fun toString(): String {
            return "Range(from=$from, to=$to)"
        }
    }

    companion object {
        val LATEST_BLOCK = Range(BlockId.LATEST, BlockId.LATEST)
    }
}

private class LogFilterSerializer : JsonSerializer<LogFilter>() {
    override fun serialize(value: LogFilter, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        when (val blocks = value.blocks) {
            is BlockSelector.Hash -> gen.writeStringField("blockHash", blocks.from.id)
            is BlockSelector.Range -> {
                gen.writeStringField("fromBlock", blocks.from.id)
                gen.writeStringField("toBlock", blocks.to.id)
            }
        }

        val address = value.addresses
        if (address != null) {
            gen.writeArrayFieldStart("address")
            for (i in address.indices) {
                gen.writeString(address[i].toString())
            }
            gen.writeEndArray()
        }

        val topics = value.topics
        if (topics != null) {
            val lastValidIndex = getIndexOfLastNonNullTopic(topics)
            if (lastValidIndex != -1) {
                gen.writeArrayFieldStart("topics")

                for (i in 0..lastValidIndex) {
                    val hashes = topics[i]
                    when {
                        hashes == null -> gen.writeNull()
                        hashes.size == 1 -> gen.writeString(hashes[0].toString())
                        else -> {
                            gen.writeStartArray()
                            for (j in hashes.indices) {
                                gen.writeString(hashes[j].toString())
                            }
                            gen.writeEndArray()
                        }
                    }
                }

                gen.writeEndArray()
            }
        }

        gen.writeEndObject()
    }
}

private fun getIndexOfLastNonNullTopic(topics: Array<TopicHashes?>): Int {
    for (i in topics.indices.reversed()) {
        if (topics[i] != null) {
            return i
        }
    }
    return -1
}
