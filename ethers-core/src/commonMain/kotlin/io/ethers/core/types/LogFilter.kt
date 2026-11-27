package io.ethers.core.types

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeCollection
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.jvm.JvmSynthetic

/**
 * An immutable filter for logs emitted by smart contracts.
 *
 * Build one with the DSL or a [Builder], and derive a changed copy with [copy] or [toBuilder]:
 * ```kotlin
 * val filter = LogFilter {
 *     blockRange(100, 200)
 *     address(token)
 *     topic0(transferTopic)
 * }
 *
 * val nextRange = filter.copy { blockRange(201, 300) }
 * ```
 * */
@Serializable(with = LogFilterSerializer::class)
class LogFilter private constructor(builder: Builder) {
    /**
     * Target range of blocks to filter logs from. Defaults to latest block.
     * */
    val blocks: BlockSelector = builder.blocks

    /**
     * Filter logs emitted by set addresses. If null, logs emitted by any addresses are included.
     * */
    val addresses: List<Address>? = builder.addresses

    /**
     * The topic0 filter, if any set. A log matches if its topic0 is any of these hashes.
     * */
    val topic0: List<Hash>? = builder.topic0

    /**
     * The topic1 filter, if any set. A log matches if its topic1 is any of these hashes.
     * */
    val topic1: List<Hash>? = builder.topic1

    /**
     * The topic2 filter, if any set. A log matches if its topic2 is any of these hashes.
     * */
    val topic2: List<Hash>? = builder.topic2

    /**
     * The topic3 filter, if any set. A log matches if its topic3 is any of these hashes.
     * */
    val topic3: List<Hash>? = builder.topic3

    /**
     * Filter logs by set topics. If null, logs with any topics are included.
     *
     * Topics are ordered by index, where index 0 is topic0, index 1 is topic1, and so on. Each topic filter is a
     * list of hashes, where hashes are matched by OR, and topics are matched by AND.
     *
     * Example:
     * ```
     * [[h1, h2], [h3], null, null] ==> topic0 == (h1 || h2) && topic1 == (h3)
     * [[h1], null, [h2, h3], null] ==> topic0 == (h1) && topic2 == (h2 || h3)
     * ```
     */
    val topics: List<List<Hash>?>?
        get() = when {
            topic0 == null && topic1 == null && topic2 == null && topic3 == null -> null
            else -> listOf(topic0, topic1, topic2, topic3)
        }

    /**
     * Create a [Builder] initialized with the fields of **this** filter.
     * */
    fun toBuilder(): Builder = Builder(this)

    /**
     * Return a copy of **this** filter with the changes applied by [builder]. **This** filter is not modified.
     * */
    @JvmSynthetic
    inline fun copy(builder: Builder.() -> Unit): LogFilter = Builder(this).apply(builder).build()

    override fun toString(): String {
        return "LogFilter(blockSelector=$blocks, address=$addresses, topics=$topics)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as LogFilter

        if (blocks != other.blocks) return false
        if (addresses != other.addresses) return false
        if (topic0 != other.topic0) return false
        if (topic1 != other.topic1) return false
        if (topic2 != other.topic2) return false
        if (topic3 != other.topic3) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blocks.hashCode()
        result = 31 * result + (addresses?.hashCode() ?: 0)
        result = 31 * result + (topic0?.hashCode() ?: 0)
        result = 31 * result + (topic1?.hashCode() ?: 0)
        result = 31 * result + (topic2?.hashCode() ?: 0)
        result = 31 * result + (topic3?.hashCode() ?: 0)
        return result
    }

    /**
     * Mutable builder of [LogFilter]. Each [build] creates a new [LogFilter] and leaves the builder untouched, so it
     * can keep being changed and built again. Not thread-safe.
     *
     * Hashes and addresses passed as arrays or collections are copied, so changing them afterwards does not affect
     * the filter.
     * */
    class Builder() {
        /**
         * Create a builder initialized with the fields of [filter].
         * */
        constructor(filter: LogFilter) : this() {
            this.blocks = filter.blocks
            this.addresses = filter.addresses
            this.topic0 = filter.topic0
            this.topic1 = filter.topic1
            this.topic2 = filter.topic2
            this.topic3 = filter.topic3
        }

        /**
         * Target range of blocks to filter logs from. Defaults to latest block.
         * */
        var blocks: BlockSelector = BlockSelector.LATEST_BLOCK
            private set

        /**
         * Filter logs emitted by set addresses. If null, logs emitted by any addresses are included.
         * */
        var addresses: List<Address>? = null
            private set

        var topic0: List<Hash>? = null
            private set

        var topic1: List<Hash>? = null
            private set

        var topic2: List<Hash>? = null
            private set

        var topic3: List<Hash>? = null
            private set

        /**
         * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
         */
        fun blockRange(from: Long, to: Long): Builder = blockRange(BlockId.Number(from), BlockId.Number(to))

        /**
         * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
         */
        fun blockRange(from: BlockId.Number, to: BlockId.Number): Builder = apply { blocks = BlockSelector.Range(from, to) }

        /**
         * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
         */
        fun blockRange(from: BlockId.Name, to: BlockId.Name): Builder = apply { blocks = BlockSelector.Range(from, to) }

        /**
         * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
         */
        fun blockRange(from: BlockId.Number, to: BlockId.Name): Builder = apply { blocks = BlockSelector.Range(from, to) }

        /**
         * Filter logs within provided [from] / [to] block range. Both bounds are inclusive.
         */
        fun blockRange(from: BlockId.Name, to: BlockId.Number): Builder = apply { blocks = BlockSelector.Range(from, to) }

        /**
         * Filter logs at provided block [hash].
         */
        fun atBlock(hash: Hash): Builder = apply { blocks = BlockSelector.Hash(hash) }

        /**
         * Filter logs at provided block [number].
         */
        fun atBlock(number: Long): Builder = blockRange(number, number)

        /**
         * Filter logs at provided block [blockId].
         */
        fun atBlock(blockId: BlockId): Builder = when (blockId) {
            is BlockId.Hash -> atBlock(blockId.hash)
            is BlockId.Number -> blockRange(blockId, blockId)
            is BlockId.Name -> blockRange(blockId, blockId)
        }

        /**
         * Filter logs emitted from provided [address].
         */
        fun address(address: Address): Builder = apply { addresses = listOf(address) }

        /**
         * Filter logs emitted from provided array of [addresses].
         */
        fun address(vararg addresses: Address): Builder = apply { this.addresses = addresses.toList() }

        /**
         * Filter logs emitted from provided list of [addresses].
         */
        fun address(addresses: Collection<Address>): Builder = apply { this.addresses = addresses.toList() }

        /**
         * Filter logs matching provided topic0 [hash].
         */
        fun topic0(hash: Hash): Builder = apply { topic0 = listOf(hash) }

        /**
         * Filter logs matching any of provided topic0 [hashes].
         */
        fun topic0(vararg hashes: Hash): Builder = apply { topic0 = hashes.toList() }

        /**
         * Filter logs matching any of provided topic0 [hashes].
         */
        fun topic0(hashes: Collection<Hash>): Builder = apply { topic0 = hashes.toList() }

        /**
         * Filter logs matching provided topic1 [hash].
         */
        fun topic1(hash: Hash): Builder = apply { topic1 = listOf(hash) }

        /**
         * Filter logs matching any of provided topic1 [hashes].
         */
        fun topic1(vararg hashes: Hash): Builder = apply { topic1 = hashes.toList() }

        /**
         * Filter logs matching any of provided topic1 [hashes].
         */
        fun topic1(hashes: Collection<Hash>): Builder = apply { topic1 = hashes.toList() }

        /**
         * Filter logs matching provided topic2 [hash].
         */
        fun topic2(hash: Hash): Builder = apply { topic2 = listOf(hash) }

        /**
         * Filter logs matching any of provided topic2 [hashes].
         */
        fun topic2(vararg hashes: Hash): Builder = apply { topic2 = hashes.toList() }

        /**
         * Filter logs matching any of provided topic2 [hashes].
         */
        fun topic2(hashes: Collection<Hash>): Builder = apply { topic2 = hashes.toList() }

        /**
         * Filter logs matching provided topic3 [hash].
         */
        fun topic3(hash: Hash): Builder = apply { topic3 = listOf(hash) }

        /**
         * Filter logs matching any of provided topic3 [hashes].
         */
        fun topic3(vararg hashes: Hash): Builder = apply { topic3 = hashes.toList() }

        /**
         * Filter logs matching any of provided topic3 [hashes].
         */
        fun topic3(hashes: Collection<Hash>): Builder = apply { topic3 = hashes.toList() }

        /**
         * Create a new [LogFilter] from the current fields of this builder.
         * */
        fun build(): LogFilter = LogFilter(this)
    }

    companion object {
        /**
         * Build a [LogFilter], configuring its fields with [builder].
         * */
        @JvmSynthetic
        inline operator fun invoke(builder: Builder.() -> Unit): LogFilter {
            return Builder().apply(builder).build()
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

object LogFilterSerializer : KSerializer<LogFilter> {
    private val addressesSerializer = ListSerializer(AddressSerializer)

    private const val BLOCK_HASH = 0
    private const val FROM_BLOCK = 1
    private const val TO_BLOCK = 2
    private const val ADDRESS = 3
    private const val TOPICS = 4

    // Either blockHash, or fromBlock and toBlock, then the optional address and topics, in this order.
    override val descriptor = buildClassSerialDescriptor("LogFilter") {
        element<String>("blockHash", isOptional = true)
        element<String>("fromBlock", isOptional = true)
        element<String>("toBlock", isOptional = true)
        element("address", addressesSerializer.descriptor, isOptional = true)
        element("topics", TopicsSerializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: LogFilter) {
        val descriptor = descriptor
        encoder.encodeStructure(descriptor) {
            when (val blocks = value.blocks) {
                is BlockSelector.Hash -> encodeStringElement(descriptor, BLOCK_HASH, blocks.from.id)
                is BlockSelector.Range -> {
                    encodeStringElement(descriptor, FROM_BLOCK, blocks.from.id)
                    encodeStringElement(descriptor, TO_BLOCK, blocks.to.id)
                }
            }

            val addresses = value.addresses
            if (addresses != null) encodeSerializableElement(descriptor, ADDRESS, addressesSerializer, addresses)

            if (topicCount(value) != 0) encodeSerializableElement(descriptor, TOPICS, TopicsSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): LogFilter = throw UnsupportedOperationException()

    /** The number of topics written: up to the last one that is set. */
    private fun topicCount(value: LogFilter): Int = when {
        value.topic3 != null -> 4
        value.topic2 != null -> 3
        value.topic1 != null -> 2
        value.topic0 != null -> 1
        else -> 0
    }

    /**
     * Writes the topics of a filter up to the last one that is set, which must exist: a topic that isn't set as `null`,
     * a topic of one hash as that hash, and any other as an array of its hashes.
     */
    private object TopicsSerializer : SerializationStrategy<LogFilter> {
        private val hashesSerializer = ListSerializer(HashSerializer)

        // only its LIST kind is read by the JSON encoder; the elements are of the three kinds above
        override val descriptor = ListSerializer(JsonElement.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: LogFilter) {
            val descriptor = descriptor
            val size = topicCount(value)
            encoder.encodeCollection(descriptor, size) {
                for (i in 0 until size) {
                    val hashes = when (i) {
                        0 -> value.topic0
                        1 -> value.topic1
                        2 -> value.topic2
                        else -> value.topic3
                    }
                    when {
                        hashes == null -> encodeSerializableElement(descriptor, i, JsonNull.serializer(), JsonNull)
                        hashes.size == 1 -> encodeStringElement(descriptor, i, hashes[0].toString())
                        else -> encodeSerializableElement(descriptor, i, hashesSerializer, hashes)
                    }
                }
            }
        }
    }
}
