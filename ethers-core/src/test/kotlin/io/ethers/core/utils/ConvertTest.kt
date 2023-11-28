package io.ethers.core.utils

import io.ethers.core.utils.Convert.ETHER
import io.ethers.core.utils.Convert.MWEI
import io.ethers.core.utils.Convert.fromWei
import io.ethers.core.utils.Convert.toWei
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.lang.NumberFormatException
import java.math.BigDecimal
import java.math.BigInteger

class ConvertTest : FunSpec({
    data class ConversionTestData(val src: String, val res: String, val unit: Unit)

    context("toWei") {
        withData(
            listOf(
                ConversionTestData("33", "33000000000000000000", ETHER),
                ConversionTestData("65.12345", "65123450000000000000", ETHER),
                ConversionTestData("-65.12345", "-65123450000000000000", ETHER),
                ConversionTestData("0.000000000000000001", "1", ETHER),
                ConversionTestData("-0.000000000000000001", "-1", ETHER),
                ConversionTestData("9876543210987654321.123456789", "9876543210987654321123456789000000000", ETHER),
                ConversionTestData("33", "33000000", MWEI),
                ConversionTestData("65.12345", "65123450", MWEI),
                ConversionTestData("0.000000000000000001", "0", MWEI),
                ConversionTestData("-0.000000000000000001", "0", MWEI),
                ConversionTestData("9876543210987654321.123456789", "9876543210987654321123456", MWEI),
                ConversionTestData("33", "33", Unit(0)),
                ConversionTestData("65.12345", "65", Unit(0)),
                ConversionTestData("0.000000000000000001", "0", Unit(0)),
                ConversionTestData("9876543210987654321.123456789", "9876543210987654321", Unit(0)),
            ),
        ) {
            toWei(it.src, it.unit).toString() shouldBe it.res
        }

        test("toWei bulk test") {
            val maxUint256 = (BigInteger.TWO.pow(256) - BigInteger.ONE).toBigDecimal()

            for (decimals in 0..30) {
                Arb.bigDecimal(BigDecimal.ZERO, maxUint256).checkAll {
                    val res = it.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()
                    // Number, Unit
                    toWei(it, Unit(decimals)) shouldBe res
                    // Number, Decimals
                    toWei(it, decimals) shouldBe res
                    // String, Unit
                    toWei(it.toString(), Unit(decimals)) shouldBe res
                    // String, Decimals
                    toWei(it.toString(), decimals) shouldBe res
                }

                Arb.int(Int.MIN_VALUE..Int.MAX_VALUE).checkAll {
                    val res = it.toBigDecimal().movePointRight(decimals).toBigInteger()
                    // Int, Unit
                    toWei(it, Unit(decimals)) shouldBe res
                    // Int, Decimals
                    toWei(it, decimals) shouldBe res
                }
            }
        }
    }

    context("fromWei") {
        withData(
            listOf(
                ConversionTestData("33", "0.000000000000000033", ETHER),
                ConversionTestData("-33", "-0.000000000000000033", ETHER),
                ConversionTestData("1000000000000000000", "1", ETHER),
                ConversionTestData("-1000000000000000000", "-1", ETHER),
                ConversionTestData("33", "0.000033", MWEI),
                ConversionTestData("-33", "-0.000033", MWEI),
                ConversionTestData("1000000", "1", MWEI),
                ConversionTestData("-1000000", "-1", MWEI),
                ConversionTestData("33", "33", Unit(0)),
                ConversionTestData("-33", "-33", Unit(0)),
            ),
        ) {
            fromWei(it.src, it.unit).stripTrailingZeros().toPlainString() shouldBe it.res
        }

        withData(
            listOf(
                ConversionTestData("0.1", "", ETHER),
                ConversionTestData("0.1", "", Unit(0)),
            ),
        ) {
            shouldThrow<NumberFormatException> {
                fromWei(it.src, it.unit)
            }
        }

        test("fromWei bulk test") {
            for (decimals in 0..30) {
                // Test with 1000 random values in between
                Arb.bigInt(0, 256).checkAll {
                    val res = it.toBigDecimal().movePointLeft(decimals)
                    // Number, Unit
                    fromWei(it, Unit(decimals)) shouldBe res
                    // Number, Decimals
                    fromWei(it, decimals) shouldBe res
                    // String, Unit
                    fromWei(it.toString(), Unit(decimals)) shouldBe res
                    // String, Decimals
                    fromWei(it.toString(), decimals) shouldBe res
                }

                Arb.int(Int.MIN_VALUE..Int.MAX_VALUE).checkAll {
                    val res = it.toBigDecimal().movePointLeft(decimals)
                    // Int, Unit
                    fromWei(it, Unit(decimals)) shouldBe res
                    // Int, Decimals
                    fromWei(it, decimals) shouldBe res
                }
            }
        }
    }
})
