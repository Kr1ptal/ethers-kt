package io.ethers.core.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Represents a unit of measurement within the EVM ecosystem.
 */
data class EthUnit(val decimals: Int) {
    /**
     * Convert an [amount] of the current unit to [WEI], truncating values less than 1 Wei.
     */
    fun toWei(amount: Int): BigDecimal = this.convert(amount, WEI)

    /**
     * Convert an [amount] of the current unit to [WEI], truncating values less than 1 Wei.
     */
    fun toWei(amount: Long): BigDecimal = this.convert(amount, WEI)

    /**
     * Convert an [amount] of the current unit to [WEI], truncating values less than 1 Wei.
     */
    fun toWei(amount: Double): BigDecimal = this.convert(amount, WEI)

    /**
     * Convert an [amount] of the current unit to [WEI], truncating values less than 1 Wei.
     */
    fun toWei(amount: String): BigDecimal = this.convert(amount, WEI)

    /**
     * Convert an [amount] of the current unit to [WEI], truncating values less than 1 Wei.
     */
    fun toWei(amount: BigInteger): BigDecimal = this.convert(amount, WEI)

    /**
     * Convert an [amount] of the current unit to [WEI], truncating values less than 1 Wei.
     */
    fun toWei(amount: BigDecimal): BigDecimal = this.convert(amount, WEI)

    /**
     * Convert an [amount] of the current unit to [GWEI], truncating values less than 1 Wei.
     */
    fun toGwei(amount: Int): BigDecimal = this.convert(amount, GWEI)

    /**
     * Convert an [amount] of the current unit to [GWEI], truncating values less than 1 Wei.
     */
    fun toGwei(amount: Long): BigDecimal = this.convert(amount, GWEI)

    /**
     * Convert an [amount] of the current unit to [GWEI], truncating values less than 1 Wei.
     */
    fun toGwei(amount: Double): BigDecimal = this.convert(amount, GWEI)

    /**
     * Convert an [amount] of the current unit to [GWEI], truncating values less than 1 Wei.
     */
    fun toGwei(amount: String): BigDecimal = this.convert(amount, GWEI)

    /**
     * Convert an [amount] of the current unit to [GWEI], truncating values less than 1 Wei.
     */
    fun toGwei(amount: BigInteger): BigDecimal = this.convert(amount, GWEI)

    /**
     * Convert an [amount] of the current unit to [GWEI], truncating values less than 1 Wei.
     */
    fun toGwei(amount: BigDecimal): BigDecimal = this.convert(amount, GWEI)

    /**
     * Convert an [amount] of the current unit to [ETHER], truncating values less than 1 Wei.
     */
    fun toEther(amount: Int): BigDecimal = this.convert(amount, ETHER)

    /**
     * Convert an [amount] of the current unit to [ETHER], truncating values less than 1 Wei.
     */
    fun toEther(amount: Long): BigDecimal = this.convert(amount, ETHER)

    /**
     * Convert an [amount] of the current unit to [ETHER], truncating values less than 1 Wei.
     */
    fun toEther(amount: Double): BigDecimal = this.convert(amount, ETHER)

    /**
     * Convert an [amount] of the current unit to [ETHER], truncating values less than 1 Wei.
     */
    fun toEther(amount: String): BigDecimal = this.convert(amount, ETHER)

    /**
     * Convert an [amount] of the current unit to [ETHER], truncating values less than 1 Wei.
     */
    fun toEther(amount: BigInteger): BigDecimal = this.convert(amount, ETHER)

    /**
     * Convert an [amount] of the current unit to [ETHER], truncating values less than 1 Wei.
     */
    fun toEther(amount: BigDecimal): BigDecimal = this.convert(amount, ETHER)

    /**
     * Convert an [amount] of Wei to the current unit, truncating values less than 1 Wei.
     */
    fun fromWei(amount: Int): BigDecimal = WEI.convert(amount, this)

    /**
     * Convert an [amount] of Wei to the current unit, truncating values less than 1 Wei.
     */
    fun fromWei(amount: Long): BigDecimal = WEI.convert(amount, this)

    /**
     * Convert an [amount] of Wei to the current unit, truncating values less than 1 Wei.
     */
    fun fromWei(amount: Double): BigDecimal = WEI.convert(amount, this)

    /**
     * Convert an [amount] of Wei to the current unit, truncating values less than 1 Wei.
     */
    fun fromWei(amount: String): BigDecimal = WEI.convert(amount, this)

    /**
     * Convert an [amount] of Wei to the current unit, truncating values less than 1 Wei.
     */
    fun fromWei(amount: BigInteger): BigDecimal = WEI.convert(amount, this)

    /**
     * Convert an [amount] of Wei to the current unit, truncating values less than 1 Wei.
     */
    fun fromWei(amount: BigDecimal): BigDecimal = WEI.convert(amount, this)

    /**
     * Convert an [amount] of [GWEI] to the current unit, truncating values less than 1 Wei.
     */
    fun fromGwei(amount: Int): BigDecimal = GWEI.convert(amount, this)

    /**
     * Convert an [amount] of [GWEI] to the current unit, truncating values less than 1 Wei.
     */
    fun fromGwei(amount: Long): BigDecimal = GWEI.convert(amount, this)

    /**
     * Convert an [amount] of [GWEI] to the current unit, truncating values less than 1 Wei.
     */
    fun fromGwei(amount: Double): BigDecimal = GWEI.convert(amount, this)

    /**
     * Convert an [amount] of [GWEI] to the current unit, truncating values less than 1 Wei.
     */
    fun fromGwei(amount: String): BigDecimal = GWEI.convert(amount, this)

    /**
     * Convert an [amount] of [GWEI] to the current unit, truncating values less than 1 Wei.
     */
    fun fromGwei(amount: BigInteger): BigDecimal = GWEI.convert(amount, this)

    /**
     * Convert an [amount] of [GWEI] to the current unit, truncating values less than 1 Wei.
     */
    fun fromGwei(amount: BigDecimal): BigDecimal = GWEI.convert(amount, this)

    /**
     * Convert an [amount] of [ETHER] to the current unit, truncating values less than 1 Wei.
     */
    fun fromEther(amount: Int): BigDecimal = ETHER.convert(amount, this)

    /**
     * Convert an [amount] of [ETHER] to the current unit, truncating values less than 1 Wei.
     */
    fun fromEther(amount: Long): BigDecimal = ETHER.convert(amount, this)

    /**
     * Convert an [amount] of [ETHER] to the current unit, truncating values less than 1 Wei.
     */
    fun fromEther(amount: Double): BigDecimal = ETHER.convert(amount, this)

    /**
     * Convert an [amount] of [ETHER] to the current unit, truncating values less than 1 Wei.
     */
    fun fromEther(amount: String): BigDecimal = ETHER.convert(amount, this)

    /**
     * Convert an [amount] of [ETHER] to the current unit, truncating values less than 1 Wei.
     */
    fun fromEther(amount: BigInteger): BigDecimal = ETHER.convert(amount, this)

    /**
     * Convert an [amount] of [ETHER] to the current unit, truncating values less than 1 Wei.
     */
    fun fromEther(amount: BigDecimal): BigDecimal = ETHER.convert(amount, this)

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: Int, toUnit: EthUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: Long, toUnit: EthUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: Double, toUnit: EthUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: String, toUnit: EthUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: BigInteger, toUnit: EthUnit): BigDecimal {
        return amount.toBigDecimal(toUnit.decimals - this.decimals)
    }

    /**
     * Convert an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: BigDecimal, toUnit: EthUnit): BigDecimal {
        return amount.movePointRight(this.decimals - toUnit.decimals).setScale(toUnit.decimals, RoundingMode.DOWN)
    }

    companion object {
        @JvmField
        val WEI = EthUnit(0)

        @JvmField
        val KWEI = EthUnit(3)

        @JvmField
        val MWEI = EthUnit(6)

        @JvmField
        val GWEI = EthUnit(9)

        @JvmField
        val SZABO = EthUnit(12)

        @JvmField
        val FINNEY = EthUnit(15)

        @JvmField
        val ETHER = EthUnit(18)

        @JvmField
        val KETHER = EthUnit(21)

        @JvmField
        val METHER = EthUnit(24)

        @JvmField
        val GETHER = EthUnit(27)
    }
}
