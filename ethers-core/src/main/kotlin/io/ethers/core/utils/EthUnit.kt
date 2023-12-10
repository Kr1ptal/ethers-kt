package io.ethers.core.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class EthUnit(val decimals: Int) {
    fun toWei(amount: String): BigDecimal = this.toUnit(amount, WEI)

    fun fromWei(amount: String): BigDecimal = WEI.toUnit(amount, this)

    // Values lower than 1 WEI are truncated
    fun toUnit(amount: BigDecimal, targetUnit: EthUnit): BigDecimal {
        return amount.movePointRight(this.decimals - targetUnit.decimals)
            .setScale(targetUnit.decimals, RoundingMode.DOWN)
    }

    fun toUnit(amount: BigInteger, targetUnit: EthUnit): BigDecimal {
        return amount.toBigDecimal(targetUnit.decimals - this.decimals)
    }

    fun toUnit(amount: String, targetUnit: EthUnit): BigDecimal {
        return toUnit(amount.toBigDecimal(), targetUnit)
    }

    companion object {
        val WEI = EthUnit(0)
        val KWEI = EthUnit(3)
        val MWEI = EthUnit(6)
        val GWEI = EthUnit(9)
        val SZABO = EthUnit(12)
        val FINNEY = EthUnit(15)
        val ETHER = EthUnit(18)
        val KETHER = EthUnit(21)
        val METHER = EthUnit(24)
        val GETHER = EthUnit(27)

        fun toWei(amount: String, sourceUnit: EthUnit): BigDecimal =
            sourceUnit.toUnit(amount, WEI)

        fun fromWei(number: String, targetUnit: EthUnit): BigDecimal =
            WEI.toUnit(number, targetUnit)

        fun toUnit(amount: String, sourceUnit: EthUnit, targetUnit: EthUnit) =
            sourceUnit.toUnit(amount, targetUnit)

        fun toUnit(amount: BigDecimal, sourceUnit: EthUnit, targetUnit: EthUnit) =
            sourceUnit.toUnit(amount, targetUnit)

        fun toUnit(amount: BigInteger, sourceUnit: EthUnit, targetUnit: EthUnit) =
            sourceUnit.toUnit(amount, targetUnit)
    }
}
