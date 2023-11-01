package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize

@JsonSerialize(using = LogFilterSerializer::class)
class LogFilter {
    var blocks: BlockSelector? = null
        private set

    private var _address: ArrayList<Address>? = null
    val address: List<Address>?
        get() = _address

    var topics: Array<Topic?>? = null
        private set

    /**
     * Filter logs within provided [from] (inclusive) [to] (exclusive) block range.
     */
    fun blockRange(from: Long, to: Long): LogFilter = blockRange(BlockId.Number(from), BlockId.Number(to))

    /**
     * Filter logs within provided [from] (inclusive) [to] (exclusive) block range.
     */
    fun blockRange(from: BlockId.Number, to: BlockId.Number): LogFilter {
        blocks = BlockSelector.Range(from, to)
        return this
    }

    /**
     * Filter logs within provided [from] (inclusive) [to] (exclusive) block range.
     */
    fun blockRange(from: BlockId.Name, to: BlockId.Name): LogFilter {
        blocks = BlockSelector.Range(from, to)
        return this
    }

    /**
     * Filter logs within provided [from] (inclusive) [to] (exclusive) block range.
     */
    fun blockRange(from: BlockId.Number, to: BlockId.Name): LogFilter {
        blocks = BlockSelector.Range(from, to)
        return this
    }

    /**
     * Filter logs within provided [from] (inclusive) [to] (exclusive) block range.
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
     * Filter logs at provided block [hash].
     */
    fun atBlock(hash: BlockId.Hash): LogFilter {
        blocks = BlockSelector.Hash(hash.hash)
        return this
    }

    /**
     * Filter logs emitted from provided [address].
     */
    fun address(address: Address): LogFilter {
        getOrCreateAddress().add(address)
        return this
    }

    /**
     * Filter logs emitted from provided list of [addresses].
     */
    fun address(addresses: List<Address>): LogFilter {
        getOrCreateAddress(addresses.size).addAll(addresses)
        return this
    }

    /**
     * Filter logs matching provided topic0 [hash].
     */
    fun topic0(hash: Hash): LogFilter = topic0(Topic.Single(hash))

    /**
     * Filter logs matching any of provided topic0 [hashes].
     */
    fun topic0(vararg hashes: Hash): LogFilter = topic0(Topic.Array(hashes as Array<Hash>))

    /**
     * Filter logs matching provided topic0 [topic].
     */
    fun topic0(topic: Topic): LogFilter {
        getOrCreateTopics()[0] = topic
        return this
    }

    /**
     * Filter logs matching provided topic1 [hash].
     */
    fun topic1(hash: Hash): LogFilter = topic1(Topic.Single(hash))

    /**
     * Filter logs matching any of provided topic1 [hashes].
     */
    fun topic1(vararg hashes: Hash): LogFilter = topic1(Topic.Array(hashes as Array<Hash>))

    /**
     * Filter logs matching provided topic1 [topic].
     */
    fun topic1(topic: Topic): LogFilter {
        getOrCreateTopics()[1] = topic
        return this
    }

    /**
     * Filter logs matching provided topic2 [hash].
     */
    fun topic2(hashes: Hash): LogFilter = topic2(Topic.Single(hashes))

    /**
     * Filter logs matching any of provided topic2 [hashes].
     */
    fun topic2(vararg hashes: Hash): LogFilter = topic2(Topic.Array(hashes as Array<Hash>))

    /**
     * Filter logs matching provided topic2 [topic].
     */
    fun topic2(topic: Topic): LogFilter {
        getOrCreateTopics()[2] = topic
        return this
    }

    /**
     * Filter logs matching provided topic3 [hash].
     */
    fun topic3(hash: Hash): LogFilter = topic3(Topic.Single(hash))

    /**
     * Filter logs matching any of provided topic3 [hashes].
     */
    fun topic3(vararg hashes: Hash): LogFilter = topic3(Topic.Array(hashes as Array<Hash>))

    /**
     * Filter logs matching provided topic3 [topic].
     */
    fun topic3(topic: Topic): LogFilter {
        getOrCreateTopics()[3] = topic
        return this
    }

    private fun getOrCreateAddress(sizeHint: Int = 10): ArrayList<Address> {
        val address = _address
        if (address != null) {
            return address
        }
        return ArrayList<Address>(sizeHint).also { this._address = it }
    }

    private fun getOrCreateTopics(): Array<Topic?> {
        val topics = topics
        if (topics != null) {
            return topics
        }
        return arrayOfNulls<Topic>(4).also { this.topics = it }
    }

    override fun toString(): String {
        return "LogFilter(blockSelector=$blocks, address=$_address, topics=${topics?.contentToString()})"
    }

    companion object {
        inline operator fun invoke(builder: LogFilter.() -> Unit): LogFilter {
            return LogFilter().apply(builder)
        }
    }
}

sealed interface BlockSelector {
    data class Hash(val hash: io.ethers.core.types.Hash) : BlockSelector

    // must be either block number or name, or combination
    class Range private constructor(val from: BlockId, val to: BlockId) : BlockSelector {
        constructor(from: BlockId.Number, to: BlockId.Number) : this(from as BlockId, to as BlockId)
        constructor(from: BlockId.Number, to: BlockId.Name) : this(from as BlockId, to as BlockId)
        constructor(from: BlockId.Name, to: BlockId.Name) : this(from as BlockId, to as BlockId)
        constructor(from: BlockId.Name, to: BlockId.Number) : this(from as BlockId, to as BlockId)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

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
}

sealed interface Topic {
    data class Single(val hash: Hash) : Topic

    class Array(val hashes: kotlin.Array<Hash>) : Topic {
        constructor(value: List<Hash>) : this(value.toTypedArray())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Array

            return hashes.contentEquals(other.hashes)
        }

        override fun hashCode(): Int {
            return hashes.contentHashCode()
        }

        override fun toString(): String {
            return "Array(hashes=${hashes.contentToString()})"
        }
    }
}

private class LogFilterSerializer : JsonSerializer<LogFilter>() {
    override fun serialize(value: LogFilter, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        when (val blocks = value.blocks) {
            is BlockSelector.Hash -> gen.writeStringField("blockHash", blocks.hash.toString())
            is BlockSelector.Range -> {
                gen.writeStringField("fromBlock", blocks.from.id)
                gen.writeStringField("toBlock", blocks.to.id)
            }

            null -> {}
        }

        val address = value.address
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
                    when (val topic = topics[i]) {
                        is Topic.Single -> gen.writeString(topic.hash.toString())
                        is Topic.Array -> {
                            gen.writeStartArray()

                            for (j in topic.hashes.indices) {
                                gen.writeString(topic.hashes[j].toString())
                            }

                            gen.writeEndArray()
                        }

                        null -> gen.writeNull()
                    }
                }

                gen.writeEndArray()
            }
        }

        gen.writeEndObject()
    }
}

private fun getIndexOfLastNonNullTopic(topics: Array<Topic?>): Int {
    for (i in topics.indices.reversed()) {
        if (topics[i] != null) {
            return i
        }
    }
    return -1
}
