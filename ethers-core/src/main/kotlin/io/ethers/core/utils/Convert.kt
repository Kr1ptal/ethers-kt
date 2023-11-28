package io.ethers.core.utils

import java.math.BigDecimal
import java.math.BigInteger

data class Unit(val decimals: Int)
object Convert {
    val WEI = Unit(0)
    val KWEI = Unit(3)
    val MWEI = Unit(6)
    val GWEI = Unit(9)
    val SZABO = Unit(12)
    val FINNEY = Unit(15)
    val ETHER = Unit(18)
    val KETHER = Unit(21)
    val METHER = Unit(24)
    val GETHER = Unit(27)

    // Number, Unit
    fun toWei(number: BigDecimal, unit: Unit = ETHER): BigInteger =
        number.movePointRight(unit.decimals).toBigInteger()

    fun fromWei(number: BigInteger, unit: Unit = ETHER): BigDecimal =
        number.toBigDecimal(unit.decimals)

    // String, Unit
    fun toWei(number: String, unit: Unit = ETHER): BigInteger =
        toWei(number.toBigDecimal(), unit)

    fun fromWei(number: String, unit: Unit = ETHER): BigDecimal =
        fromWei(number.toBigInteger(), unit)

    // Number, Decimals
    fun toWei(number: BigDecimal, decimals: Int = ETHER.decimals): BigInteger =
        toWei(number, Unit(decimals))

    fun fromWei(number: BigInteger, decimals: Int = ETHER.decimals): BigDecimal =
        fromWei(number, Unit(decimals))

    // String, Decimals
    fun toWei(number: String, decimals: Int = ETHER.decimals): BigInteger =
        toWei(number.toBigDecimal(), Unit(decimals))

    fun fromWei(number: String, decimals: Int = ETHER.decimals): BigDecimal =
        fromWei(number.toBigInteger(), Unit(decimals))

    // Int, Unit
    fun toWei(number: Int, unit: Unit = ETHER): BigInteger =
        toWei(number.toBigDecimal(), unit)

    fun fromWei(number: Int, unit: Unit = ETHER): BigDecimal =
        fromWei(number.toBigInteger(), unit)

    // Int, Decimals
    fun toWei(number: Int, decimals: Int = ETHER.decimals): BigInteger =
        toWei(number.toBigDecimal(), decimals)

    fun fromWei(number: Int, decimals: Int = ETHER.decimals): BigDecimal =
        fromWei(number.toBigInteger(), decimals)
}
