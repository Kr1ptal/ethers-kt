package io.ethers.core.types

import io.ethers.core.FastHex
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * An immutable block override, which can be used to override certain fields of a block, such as the block number,
 * timestamp, gas limit, etc...
 *
 * Build one with the DSL or a [Builder], and derive a changed copy with [copy] or [toBuilder]:
 * ```kotlin
 * val override = BlockOverride {
 *     number = 100
 *     time = 1_700_000_000
 * }
 *
 * val later = override.copy { time = 1_700_000_012 }
 * ```
 * */
@Serializable(with = BlockOverrideSerializer::class)
class BlockOverride private constructor(builder: Builder) {
    val number: Long = builder.number
    val difficulty: BigInteger? = builder.difficulty
    val time: Long = builder.time
    val gasLimit: Long = builder.gasLimit
    val coinbase: Address? = builder.coinbase
    val random: Hash? = builder.random
    val baseFee: BigInteger? = builder.baseFee

    /**
     * Create a [Builder] initialized with the fields of **this** override.
     * */
    fun toBuilder(): Builder = Builder(this)

    /**
     * Return a copy of **this** override with the changes applied by [builder]. **This** override is not modified.
     * */
    @JvmSynthetic
    inline fun copy(builder: Builder.() -> Unit): BlockOverride = Builder(this).apply(builder).build()

    override fun toString(): String {
        return "BlockOverride(number=$number, difficulty=$difficulty, time=$time, gasLimit=$gasLimit, coinbase=$coinbase, random=$random, baseFee=$baseFee)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BlockOverride

        if (number != other.number) return false
        if (difficulty != other.difficulty) return false
        if (time != other.time) return false
        if (gasLimit != other.gasLimit) return false
        if (coinbase != other.coinbase) return false
        if (random != other.random) return false
        if (baseFee != other.baseFee) return false

        return true
    }

    override fun hashCode(): Int {
        var result = number.hashCode()
        result = 31 * result + (difficulty?.hashCode() ?: 0)
        result = 31 * result + time.hashCode()
        result = 31 * result + gasLimit.hashCode()
        result = 31 * result + (coinbase?.hashCode() ?: 0)
        result = 31 * result + (random?.hashCode() ?: 0)
        result = 31 * result + (baseFee?.hashCode() ?: 0)
        return result
    }

    /**
     * Mutable builder of [BlockOverride]. Each [build] creates a new [BlockOverride] and leaves the builder
     * untouched, so it can keep being changed and built again. Not thread-safe.
     * */
    class Builder() {
        /**
         * Create a builder initialized with the fields of [override].
         * */
        constructor(override: BlockOverride) : this() {
            this.number = override.number
            this.difficulty = override.difficulty
            this.time = override.time
            this.gasLimit = override.gasLimit
            this.coinbase = override.coinbase
            this.random = override.random
            this.baseFee = override.baseFee
        }

        // make property setters unavailable from Java since we provide custom chained functions
        var number: Long = -1L
            @JvmSynthetic set

        var difficulty: BigInteger? = null
            @JvmSynthetic set(value) {
                require(value == null || value >= bigIntegerOf(0)) { "Difficulty must be non-negative" }
                field = value
            }

        var time: Long = -1L
            @JvmSynthetic set

        var gasLimit: Long = -1L
            @JvmSynthetic set

        var coinbase: Address? = null
            @JvmSynthetic set

        var random: Hash? = null
            @JvmSynthetic set

        var baseFee: BigInteger? = null
            @JvmSynthetic set(value) {
                require(value == null || value >= bigIntegerOf(0)) { "BaseFee must be non-negative" }
                field = value
            }

        /**
         * Set the block number.
         * */
        fun number(number: Long) = apply { this.number = number }

        /**
         * Set the block difficulty.
         * */
        fun difficulty(difficulty: BigInteger?) = apply { this.difficulty = difficulty }

        /**
         * Set the block timestamp.
         * */
        fun time(time: Long) = apply { this.time = time }

        /**
         * Set the block gas limit.
         * */
        fun gasLimit(gasLimit: Long) = apply { this.gasLimit = gasLimit }

        /**
         * Set the block coinbase.
         * */
        fun coinbase(coinbase: Address?) = apply { this.coinbase = coinbase }

        /**
         * Set the block random value.
         * */
        fun random(random: Hash?) = apply { this.random = random }

        /**
         * Set the block base fee.
         * */
        fun baseFee(baseFee: BigInteger?) = apply { this.baseFee = baseFee }

        /**
         * Create a new [BlockOverride] from the current fields of this builder.
         * */
        fun build(): BlockOverride = BlockOverride(this)
    }

    companion object {
        /**
         * Build a [BlockOverride], configuring its fields with [builder].
         * */
        @JvmSynthetic
        inline operator fun invoke(builder: Builder.() -> Unit): BlockOverride {
            return Builder().apply(builder).build()
        }

        /**
         * Create a [BlockOverride] from a provided [block].
         * */
        @JvmStatic
        fun from(block: Block<*>): BlockOverride {
            return BlockOverride {
                number = block.number
                difficulty = block.difficulty
                time = block.timestamp
                gasLimit = block.gasLimit
                coinbase = block.miner
                random = block.mixHash
                baseFee = block.baseFeePerGas
            }
        }
    }
}

object BlockOverrideSerializer : KSerializer<BlockOverride> {
    private const val NUMBER = 0
    private const val DIFFICULTY = 1
    private const val TIME = 2
    private const val GAS_LIMIT = 3
    private const val COINBASE = 4
    private const val RANDOM = 5
    private const val BASE_FEE = 6

    // Every field is optional: only the ones that are set are written, in this order.
    override val descriptor = buildClassSerialDescriptor("BlockOverride") {
        element<String>("number", isOptional = true)
        element<String>("difficulty", isOptional = true)
        element<String>("time", isOptional = true)
        element<String>("gasLimit", isOptional = true)
        element<String>("coinbase", isOptional = true)
        element<String>("random", isOptional = true)
        element<String>("baseFee", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: BlockOverride) {
        val descriptor = descriptor
        encoder.encodeStructure(descriptor) {
            val number = value.number
            if (number != -1L) encodeStringElement(descriptor, NUMBER, FastHex.encodeWithPrefix(number))
            val difficulty = value.difficulty
            if (difficulty != null) encodeStringElement(descriptor, DIFFICULTY, FastHex.encodeWithPrefix(difficulty))
            val time = value.time
            if (time != -1L) encodeStringElement(descriptor, TIME, FastHex.encodeWithPrefix(time))
            val gasLimit = value.gasLimit
            if (gasLimit != -1L) encodeStringElement(descriptor, GAS_LIMIT, FastHex.encodeWithPrefix(gasLimit))
            val coinbase = value.coinbase
            if (coinbase != null) encodeStringElement(descriptor, COINBASE, coinbase.toString())
            val random = value.random
            if (random != null) encodeStringElement(descriptor, RANDOM, random.toString())
            val baseFee = value.baseFee
            if (baseFee != null) encodeStringElement(descriptor, BASE_FEE, FastHex.encodeWithPrefix(baseFee))
        }
    }

    override fun deserialize(decoder: Decoder): BlockOverride = throw UnsupportedOperationException()
}

/**
 * Create a [BlockOverride] from **this** block.
 * */
fun Block<*>.toBlockOverride() = BlockOverride.from(this)
