package io.ethers.solana.utils

import io.ethers.solana.utils.SolUnit.Companion.LAMPORT
import io.ethers.solana.utils.SolUnit.Companion.MICRO_LAMPORT
import io.ethers.solana.utils.SolUnit.Companion.SOL
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.RoundingMode
import io.github.artificialpb.bignum.toBigDecimal
import kotlin.jvm.JvmField

/**
 * Represents a unit of measurement within the Solana ecosystem.
 *
 * [decimals] is the number of decimal places relative to a micro-lamport, the smallest unit used here
 * to preserve priority-fee precision: [MICRO_LAMPORT] has 0, [LAMPORT] has 6, and [SOL] has 15.
 * Decimal conversions truncate towards zero below one micro-lamport, following EthUnit's design.
 * Conversions support signed amounts and are not constrained to u64; RPC and instruction APIs validate amounts.
 */
data class SolUnit(val decimals: Int) {
    /**
     * Convert an [amount] of the current unit to [MICRO_LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toMicroLamports(amount: Int): BigDecimal = this.convert(amount, MICRO_LAMPORT)

    /**
     * Convert an [amount] of the current unit to [MICRO_LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toMicroLamports(amount: Long): BigDecimal = this.convert(amount, MICRO_LAMPORT)

    /**
     * Convert an [amount] of the current unit to [MICRO_LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toMicroLamports(amount: Double): BigDecimal = this.convert(amount, MICRO_LAMPORT)

    /**
     * Convert an [amount] of the current unit to [MICRO_LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toMicroLamports(amount: String): BigDecimal = this.convert(amount, MICRO_LAMPORT)

    /**
     * Convert an [amount] of the current unit to [MICRO_LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toMicroLamports(amount: BigInteger): BigDecimal = this.convert(amount, MICRO_LAMPORT)

    /**
     * Convert an [amount] of the current unit to [MICRO_LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toMicroLamports(amount: BigDecimal): BigDecimal = this.convert(amount, MICRO_LAMPORT)

    /**
     * Convert an [amount] of the current unit to [LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toLamports(amount: Int): BigDecimal = this.convert(amount, LAMPORT)

    /**
     * Convert an [amount] of the current unit to [LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toLamports(amount: Long): BigDecimal = this.convert(amount, LAMPORT)

    /**
     * Convert an [amount] of the current unit to [LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toLamports(amount: Double): BigDecimal = this.convert(amount, LAMPORT)

    /**
     * Convert an [amount] of the current unit to [LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toLamports(amount: String): BigDecimal = this.convert(amount, LAMPORT)

    /**
     * Convert an [amount] of the current unit to [LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toLamports(amount: BigInteger): BigDecimal = this.convert(amount, LAMPORT)

    /**
     * Convert an [amount] of the current unit to [LAMPORT], truncating values less than 1 micro-lamport.
     */
    fun toLamports(amount: BigDecimal): BigDecimal = this.convert(amount, LAMPORT)

    /**
     * Convert an [amount] of the current unit to [SOL], truncating values less than 1 micro-lamport.
     */
    fun toSol(amount: Int): BigDecimal = this.convert(amount, SOL)

    /**
     * Convert an [amount] of the current unit to [SOL], truncating values less than 1 micro-lamport.
     */
    fun toSol(amount: Long): BigDecimal = this.convert(amount, SOL)

    /**
     * Convert an [amount] of the current unit to [SOL], truncating values less than 1 micro-lamport.
     */
    fun toSol(amount: Double): BigDecimal = this.convert(amount, SOL)

    /**
     * Convert an [amount] of the current unit to [SOL], truncating values less than 1 micro-lamport.
     */
    fun toSol(amount: String): BigDecimal = this.convert(amount, SOL)

    /**
     * Convert an [amount] of the current unit to [SOL], truncating values less than 1 micro-lamport.
     */
    fun toSol(amount: BigInteger): BigDecimal = this.convert(amount, SOL)

    /**
     * Convert an [amount] of the current unit to [SOL], truncating values less than 1 micro-lamport.
     */
    fun toSol(amount: BigDecimal): BigDecimal = this.convert(amount, SOL)

    /**
     * Convert an [amount] of micro-lamport to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromMicroLamports(amount: Int): BigDecimal = MICRO_LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of micro-lamport to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromMicroLamports(amount: Long): BigDecimal = MICRO_LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of micro-lamport to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromMicroLamports(amount: Double): BigDecimal = MICRO_LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of micro-lamport to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromMicroLamports(amount: String): BigDecimal = MICRO_LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of micro-lamport to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromMicroLamports(amount: BigInteger): BigDecimal = MICRO_LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of micro-lamport to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromMicroLamports(amount: BigDecimal): BigDecimal = MICRO_LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of [LAMPORT] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromLamports(amount: Int): BigDecimal = LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of [LAMPORT] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromLamports(amount: Long): BigDecimal = LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of [LAMPORT] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromLamports(amount: Double): BigDecimal = LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of [LAMPORT] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromLamports(amount: String): BigDecimal = LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of [LAMPORT] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromLamports(amount: BigInteger): BigDecimal = LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of [LAMPORT] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromLamports(amount: BigDecimal): BigDecimal = LAMPORT.convert(amount, this)

    /**
     * Convert an [amount] of [SOL] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromSol(amount: Int): BigDecimal = SOL.convert(amount, this)

    /**
     * Convert an [amount] of [SOL] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromSol(amount: Long): BigDecimal = SOL.convert(amount, this)

    /**
     * Convert an [amount] of [SOL] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromSol(amount: Double): BigDecimal = SOL.convert(amount, this)

    /**
     * Convert an [amount] of [SOL] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromSol(amount: String): BigDecimal = SOL.convert(amount, this)

    /**
     * Convert an [amount] of [SOL] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromSol(amount: BigInteger): BigDecimal = SOL.convert(amount, this)

    /**
     * Convert an [amount] of [SOL] to the current unit, truncating values less than 1 micro-lamport.
     */
    fun fromSol(amount: BigDecimal): BigDecimal = SOL.convert(amount, this)

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 micro-lamport.
     */
    fun convert(amount: Int, toUnit: SolUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 micro-lamport.
     */
    fun convert(amount: Long, toUnit: SolUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 micro-lamport.
     */
    fun convert(amount: Double, toUnit: SolUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 micro-lamport.
     */
    fun convert(amount: String, toUnit: SolUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 micro-lamport.
     */
    fun convert(amount: BigInteger, toUnit: SolUnit): BigDecimal {
        return amount.toBigDecimal(toUnit.decimals - this.decimals)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 micro-lamport.
     */
    fun convert(amount: BigDecimal, toUnit: SolUnit): BigDecimal {
        return amount.movePointRight(this.decimals - toUnit.decimals).setScale(toUnit.decimals, RoundingMode.DOWN)
    }

    companion object {
        @JvmField
        val MICRO_LAMPORT = SolUnit(0)

        @JvmField
        val LAMPORT = SolUnit(6)

        @JvmField
        val SOL = SolUnit(15)
    }
}
