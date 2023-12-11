package io.ethers.core.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.BigInteger

class EthUnitTest : FunSpec({
    data class ConversionTestData(
        val amount1: BigDecimal,
        val unit1: EthUnit,
        val amount2: BigDecimal,
        val unit2: EthUnit,
    )

    test("Usage examples") {
        // To and from wei (String only)
        // ETHER -> WEI
        EthUnit.ETHER.toWei("1")
        EthUnit.toWei("1", EthUnit.ETHER)
        // WEI -> ETHER
        EthUnit.ETHER.fromWei("1000000000000000000")
        EthUnit.fromWei("1000000000000000000", EthUnit.ETHER)

        // Between units (BigDecimal, BigInteger, String)
        EthUnit.ETHER.convert(BigDecimal("1.234"), EthUnit.KWEI)
        EthUnit.ETHER.convert(BigInteger("1"), EthUnit.GWEI)
        EthUnit.ETHER.convert("1.234", EthUnit.GETHER)

        EthUnit.convert(BigDecimal("1.234"), fromUnit = EthUnit.ETHER, toUnit = EthUnit.KWEI)
        EthUnit.convert(BigInteger("1"), fromUnit = EthUnit.ETHER, toUnit = EthUnit.GWEI)
        EthUnit.convert("1.234", fromUnit = EthUnit.ETHER, toUnit = EthUnit.GETHER)
    }

    /**
     * Testing toUnit with amount as BigDecimal and String
     */
    fun testToUnit(amount1: BigDecimal, unit1: EthUnit, amount2: BigDecimal, unit2: EthUnit) {
        unit1.convert(amount1, unit2) shouldBeEqualComparingTo amount2
        unit1.convert(amount1.toPlainString(), unit2) shouldBeEqualComparingTo amount2
        EthUnit.convert(amount1, unit1, unit2) shouldBeEqualComparingTo amount2
        EthUnit.convert(amount1.toPlainString(), unit1, unit2) shouldBeEqualComparingTo amount2
    }

    /**
     * Testing toUnit with amount as BigInteger
     */
    fun testToUnit(amount1: BigInteger, unit1: EthUnit, amount2: BigDecimal, unit2: EthUnit) {
        unit1.convert(amount1, unit2) shouldBeEqualComparingTo amount2
        EthUnit.convert(amount1, unit1, unit2) shouldBeEqualComparingTo amount2
    }

    context("Manual tests") {
        context("Amount is BigDecimal") {
            withData(
                listOf(
                    ConversionTestData(
                        BigDecimal("33"),
                        EthUnit.ETHER,
                        BigDecimal("33000000000000000000"),
                        EthUnit.WEI,
                    ),
                    ConversionTestData(
                        BigDecimal("65.12345"),
                        EthUnit.ETHER,
                        BigDecimal("65123450000000000"),
                        EthUnit.KWEI,
                    ),
                    ConversionTestData(
                        BigDecimal("0.000001"),
                        EthUnit.KETHER,
                        BigDecimal("0.001"),
                        EthUnit.ETHER,
                    ),
                    // Negative
                    ConversionTestData(
                        BigDecimal("-65.12345"),
                        EthUnit.ETHER,
                        BigDecimal("-65123450000000000"),
                        EthUnit.KWEI,
                    ),
                ),
            ) {
                // test in both directions
                testToUnit(it.amount1, it.unit1, it.amount2, it.unit2)
                testToUnit(it.amount2, it.unit2, it.amount1, it.unit1)
            }
        }

        context("Amount is BigInteger") {
            withData(
                listOf(
                    ConversionTestData(
                        BigDecimal("33"),
                        EthUnit.ETHER,
                        BigDecimal("33000000000000000000"),
                        EthUnit.WEI,
                    ),
                    ConversionTestData(BigDecimal("1"), EthUnit.ETHER, BigDecimal("0.001"), EthUnit.KETHER),
                    ConversionTestData(
                        BigDecimal("-65"),
                        EthUnit.ETHER,
                        BigDecimal("-65000000000000000"),
                        EthUnit.KWEI,
                    ),
                    ConversionTestData(
                        BigDecimal("-123"),
                        EthUnit.ETHER,
                        BigDecimal("-0.123"),
                        EthUnit.KETHER,
                    ),
                ),
            ) {
                // test in one direction
                testToUnit(it.amount1.toBigInteger(), it.unit1, it.amount2, it.unit2)
            }
        }

        context("Loss of decimals") {
            withData(
                listOf(
                    // Too small value has loss of decimals
                    ConversionTestData(BigDecimal("0.00001"), EthUnit.KWEI, BigDecimal("0"), EthUnit.WEI),
                    ConversionTestData(BigDecimal("-0.00001"), EthUnit.KWEI, BigDecimal("0"), EthUnit.WEI),
                ),
            ) {
                testToUnit(it.amount1, it.unit1, it.amount2, it.unit2)
            }
        }

        context("Edge cases") {
            withData(
                listOf(
                    ConversionTestData(BigDecimal("0"), EthUnit.WEI, BigDecimal("0"), EthUnit.WEI),
                    ConversionTestData(BigDecimal("-0"), EthUnit.WEI, BigDecimal("0"), EthUnit.WEI),
                    ConversionTestData(BigDecimal("0"), EthUnit.KETHER, BigDecimal("0"), EthUnit.KETHER),
                    ConversionTestData(BigDecimal("0"), EthUnit.KETHER, BigDecimal("0"), EthUnit.WEI),
                    ConversionTestData(BigDecimal("0"), EthUnit.WEI, BigDecimal("0"), EthUnit.KETHER),
                ),
            ) {
                testToUnit(it.amount1, it.unit1, it.amount2, it.unit2)
                testToUnit(it.amount1.toBigInteger(), it.unit1, it.amount2, it.unit2)
            }
        }
    }

    test("Bulk test") {
        for (fromDecimals in 0..30) {
            for (toDecimals in 0..30) {
                Arb.bigInt(0, 256).checkAll(100) {
                    val weiAmount = it.toBigDecimal()

                    val fromAmountDec = weiAmount.divide(BigDecimal.TEN.pow(fromDecimals))
                    val fromUnit = EthUnit(fromDecimals)
                    val toUnit = EthUnit(toDecimals)
                    val toAmount = weiAmount.divide(BigDecimal.TEN.pow(toDecimals))

                    // # To and from wei (String only)
                    fromUnit.toWei(fromAmountDec.toString()) shouldBeEqualComparingTo weiAmount
                    EthUnit.toWei(fromAmountDec.toString(), fromUnit) shouldBeEqualComparingTo weiAmount

                    // WEI -> source unit
                    fromUnit.fromWei(weiAmount.toString()) shouldBeEqualComparingTo fromAmountDec
                    EthUnit.fromWei(weiAmount.toString(), fromUnit) shouldBeEqualComparingTo fromAmountDec

                    // # Between units (BigDecimal, BigInteger, String)
                    testToUnit(fromAmountDec, fromUnit, toAmount, toUnit)
                    testToUnit(toAmount, toUnit, fromAmountDec, fromUnit)

                    // toUnit using BigInteger
                    // calculate sourceAmount without decimals
                    val fromAmountInt = weiAmount
                        .divide(BigDecimal.TEN.pow(fromDecimals)).toBigInteger()

                    // When input is BigInteger we can't calculate toAmount from wei,
                    // because of loss of decimals when calculating sourceAmount
                    val toAmountInt =
                        if (toDecimals > fromDecimals)
                            fromAmountInt.toBigDecimal().divide(BigDecimal.TEN.pow(toDecimals - fromDecimals))
                        else
                            fromAmountInt.toBigDecimal().multiply(BigDecimal.TEN.pow(fromDecimals - toDecimals))

                    testToUnit(fromAmountInt, fromUnit, toAmountInt, toUnit)
                }
            }
        }
    }
})
