package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import java.math.BigInteger

/**
 * Block override, which can be used to override certain fields of a block, such as the block number, timestamp,
 * gas limit, etc...
 * */
@JsonSerialize(using = BlockOverrideSerializer::class)
class BlockOverride() {
    constructor(other: BlockOverride) : this() {
        this.time = other.time
        this.number = other.number
        this.difficulty = other.difficulty
        this.gasLimit = other.gasLimit
        this.coinbase = other.coinbase
        this.random = other.random
        this.baseFee = other.baseFee
    }

    // make property setters unavailable from Java since we provide custom chained functions
    var number: Long = -1L
        @JvmSynthetic set

    var difficulty: BigInteger? = null
        @JvmSynthetic set(value) {
            require(value == null || value >= BigInteger.ZERO) { "Difficulty must be non-negative" }
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
            require(value == null || value >= BigInteger.ZERO) { "BaseFee must be non-negative" }
            field = value
        }

    /**
     * Set the block number.
     * */
    fun number(number: Long): BlockOverride {
        this.number = number
        return this
    }

    /**
     * Set the block difficulty.
     * */
    fun difficulty(difficulty: BigInteger): BlockOverride {
        this.difficulty = difficulty
        return this
    }

    /**
     * Set the block timestamp.
     * */
    fun time(time: Long): BlockOverride {
        this.time = time
        return this
    }

    /**
     * Set the block gas limit.
     * */
    fun gasLimit(gasLimit: Long): BlockOverride {
        this.gasLimit = gasLimit
        return this
    }

    /**
     * Set the block coinbase.
     * */
    fun coinbase(coinbase: Address?): BlockOverride {
        this.coinbase = coinbase
        return this
    }

    /**
     * Set the block random value.
     * */
    fun random(random: Hash?): BlockOverride {
        this.random = random
        return this
    }

    /**
     * Set the block base fee.
     * */
    fun baseFee(baseFee: BigInteger?): BlockOverride {
        this.baseFee = baseFee
        return this
    }

    override fun toString(): String {
        return "BlockOverride(number=$number, difficulty=$difficulty, time=$time, gasLimit=$gasLimit, coinbase=$coinbase, random=$random, baseFee=$baseFee)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

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

    companion object {
        inline operator fun invoke(builder: BlockOverride.() -> Unit): BlockOverride {
            return BlockOverride().apply(builder)
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

private class BlockOverrideSerializer : JsonSerializer<BlockOverride>() {
    override fun serialize(value: BlockOverride, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        if (value.number != -1L) {
            gen.writeStringField("number", FastHex.encodeWithPrefix(value.number))
        }
        if (value.difficulty != null) {
            gen.writeStringField("difficulty", FastHex.encodeWithPrefix(value.difficulty!!))
        }
        if (value.time != -1L) {
            gen.writeStringField("time", FastHex.encodeWithPrefix(value.time))
        }
        if (value.gasLimit != -1L) {
            gen.writeStringField("gasLimit", FastHex.encodeWithPrefix(value.gasLimit))
        }
        if (value.coinbase != null) {
            gen.writeStringField("coinbase", value.coinbase!!.toString())
        }
        if (value.random != null) {
            gen.writeStringField("random", value.random!!.toString())
        }
        if (value.baseFee != null) {
            gen.writeStringField("baseFee", FastHex.encodeWithPrefix(value.baseFee!!))
        }
        gen.writeEndObject()
    }
}

/**
 * Create a [BlockOverride] from **this** block.
 * */
fun Block<*>.toBlockOverride() = BlockOverride.from(this)
