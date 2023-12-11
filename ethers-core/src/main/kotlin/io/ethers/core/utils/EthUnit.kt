package io.ethers.core.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Represents a unit of measurement within the EVM ecosystem.
 */
data class EthUnit(val decimals: Int) {
    /**
     * Converts an [amount] of the current unit to Wei, truncating values less than 1 Wei.
     */
    fun toWei(amount: String): BigDecimal = this.convert(amount, WEI)

    /**
     * Converts an [amount] of Wei to the current unit, truncating values less than 1 Wei.
     */
    fun fromWei(amount: String): BigDecimal = WEI.convert(amount, this)

    /**
     * Converts an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: BigDecimal, toUnit: EthUnit): BigDecimal {
        return amount.movePointRight(this.decimals - toUnit.decimals).setScale(toUnit.decimals, RoundingMode.DOWN)
    }

    /**
     * Converts an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: BigInteger, toUnit: EthUnit): BigDecimal {
        return amount.toBigDecimal(toUnit.decimals - this.decimals)
    }

    /**
     * Converts an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
     */
    fun convert(amount: String, toUnit: EthUnit): BigDecimal {
        return convert(amount.toBigDecimal(), toUnit)
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

        /**
         * Converts an [amount] of the current unit to Wei, truncating values less than 1 Wei.
         */
        @JvmStatic
        fun toWei(amount: String, fromUnit: EthUnit): BigDecimal = fromUnit.convert(amount, WEI)

        /**
         * Converts an [amount] of Wei to the current unit, truncating values less than 1 Wei.
         */
        @JvmStatic
        fun fromWei(amount: String, toUnit: EthUnit): BigDecimal = WEI.convert(amount, toUnit)

        /**
         * Converts an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
         */
        @JvmStatic
        fun convert(amount: String, fromUnit: EthUnit, toUnit: EthUnit) = fromUnit.convert(amount, toUnit)

        /**
         * Converts an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
         */
        @JvmStatic
        fun convert(amount: BigDecimal, fromUnit: EthUnit, toUnit: EthUnit) = fromUnit.convert(amount, toUnit)

        /**
         * Converts an [amount] from the current unit to [toUnit], truncating values less than 1 Wei.
         */
        @JvmStatic
        fun convert(amount: BigInteger, fromUnit: EthUnit, toUnit: EthUnit) = fromUnit.convert(amount, toUnit)
    }
}
