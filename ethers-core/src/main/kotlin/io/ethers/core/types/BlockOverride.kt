package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import java.math.BigInteger

@JsonSerialize(using = BlockOverrideSerializer::class)
class BlockOverride {
    // make property setters unavailable from Java since we provide custom chained functions
    var number: Long = -1L
        @JvmSynthetic set
    var difficulty: BigInteger? = null
        @JvmSynthetic set
    var time: Long = -1L
        @JvmSynthetic set
    var gasLimit: Long = -1L
        @JvmSynthetic set
    var coinbase: Address? = null
        @JvmSynthetic set
    var random: Hash? = null
        @JvmSynthetic set
    var baseFee: BigInteger? = null
        @JvmSynthetic set

    fun number(number: Long): BlockOverride {
        this.number = number
        return this
    }

    fun difficulty(difficulty: BigInteger): BlockOverride {
        this.difficulty = difficulty
        return this
    }

    fun time(time: Long): BlockOverride {
        this.time = time
        return this
    }

    fun gasLimit(gasLimit: Long): BlockOverride {
        this.gasLimit = gasLimit
        return this
    }

    fun coinbase(coinbase: Address): BlockOverride {
        this.coinbase = coinbase
        return this
    }

    fun random(random: Hash): BlockOverride {
        this.random = random
        return this
    }

    fun baseFee(baseFee: BigInteger): BlockOverride {
        this.baseFee = baseFee
        return this
    }

    override fun toString(): String {
        return "BlockOverride(number=$number, difficulty=$difficulty, time=$time, gasLimit=$gasLimit, coinbase=$coinbase, random=$random, baseFee=$baseFee)"
    }

    companion object {
        inline operator fun invoke(builder: BlockOverride.() -> Unit): BlockOverride {
            return BlockOverride().apply(builder)
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
