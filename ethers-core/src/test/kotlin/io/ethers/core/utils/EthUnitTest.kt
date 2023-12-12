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

    /**
     * Testing [EthUnit.convert] with amount as BigDecimal and String
     */
    fun testConvert(fromAmount: BigDecimal, fromUnit: EthUnit, expectedAmount: BigDecimal, toUnit: EthUnit) {
        fromUnit.convert(fromAmount, toUnit) shouldBeEqualComparingTo expectedAmount
        fromUnit.convert(fromAmount.toPlainString(), toUnit) shouldBeEqualComparingTo expectedAmount
    }

    /**
     * Testing [EthUnit.convert] with amount as BigInteger
     */
    fun testConvert(fromAmount: BigInteger, fromUnit: EthUnit, expectedAmount: BigDecimal, toUnit: EthUnit) {
        fromUnit.convert(fromAmount, toUnit) shouldBeEqualComparingTo expectedAmount
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
                testConvert(it.amount1, it.unit1, it.amount2, it.unit2)
                testConvert(it.amount2, it.unit2, it.amount1, it.unit1)
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
                testConvert(it.amount1.toBigInteger(), it.unit1, it.amount2, it.unit2)
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
                testConvert(it.amount1, it.unit1, it.amount2, it.unit2)
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
                testConvert(it.amount1, it.unit1, it.amount2, it.unit2)
                testConvert(it.amount1.toBigInteger(), it.unit1, it.amount2, it.unit2)
            }
        }
    }

    test("Bulk test") {
        for (fromDecimals in 0..30) {
            for (toDecimals in 0..30) {
                Arb.bigInt(0, 256).checkAll(100) {
                    val weiAmount = it.toBigDecimal()
                    val gweiAmount = it.toBigDecimal().divide(BigDecimal.TEN.pow(9))
                    val etherAmount = it.toBigDecimal().divide(BigDecimal.TEN.pow(18))

                    val fromAmountDec = weiAmount.divide(BigDecimal.TEN.pow(fromDecimals))
                    val fromUnit = EthUnit(fromDecimals)
                    val toUnit = EthUnit(toDecimals)
                    val toAmount = weiAmount.divide(BigDecimal.TEN.pow(toDecimals))

                    // # To and from wei (String only)
                    fromUnit.toWei(fromAmountDec.toString()) shouldBeEqualComparingTo weiAmount
                    // WEI -> source unit
                    fromUnit.fromWei(weiAmount.toString()) shouldBeEqualComparingTo fromAmountDec

                    // # To and from gwei (String only)
                    fromUnit.toGwei(fromAmountDec.toString()) shouldBeEqualComparingTo gweiAmount
                    // GWEI -> source unit
                    fromUnit.fromGwei(gweiAmount.toString()) shouldBeEqualComparingTo fromAmountDec

                    // # To and from ethers (String only)
                    fromUnit.toEther(fromAmountDec.toString()) shouldBeEqualComparingTo etherAmount
                    // ETHER -> source unit
                    fromUnit.fromEther(etherAmount.toString()) shouldBeEqualComparingTo fromAmountDec

                    // # Between units (BigDecimal, BigInteger, String)
                    testConvert(fromAmountDec, fromUnit, toAmount, toUnit)
                    testConvert(toAmount, toUnit, fromAmountDec, fromUnit)

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

                    testConvert(fromAmountInt, fromUnit, toAmountInt, toUnit)
                }
            }
        }
    }
})
