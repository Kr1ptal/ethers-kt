package io.ethers.solana

import io.ethers.core.utils.EthUnit
import io.ethers.solana.utils.SolUnit
import io.ethers.solana.utils.SolUnit.Companion.LAMPORT
import io.ethers.solana.utils.SolUnit.Companion.MICRO_LAMPORT
import io.ethers.solana.utils.SolUnit.Companion.SOL
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe

class SolUnitTest : FunSpec({
    fun assertAmounts(expected: String, vararg actual: BigDecimal) {
        actual.forEach { it shouldBeEqualComparingTo BigDecimal(expected) }
    }

    test("named units use micro-lamports as the precision baseline") {
        MICRO_LAMPORT shouldBe SolUnit(0)
        LAMPORT shouldBe SolUnit(6)
        SOL shouldBe SolUnit(15)
        SOL.copy(decimals = 6) shouldBe LAMPORT
        assertAmounts("1000000000", SOL.toLamports(1), LAMPORT.fromSol(1))
        assertAmounts("1000000", LAMPORT.toMicroLamports(1), MICRO_LAMPORT.fromLamports(1))
        assertAmounts("1000000000000000", SOL.toMicroLamports(1), MICRO_LAMPORT.fromSol(1))
        assertAmounts("0.000001", MICRO_LAMPORT.toLamports(1), LAMPORT.fromMicroLamports(1))
    }

    test("all conversion methods support Int, Long, Double, String, BigInteger and BigDecimal") {
        val integer = bigIntegerOf(1)
        val decimal = BigDecimal("1.0")
        assertAmounts(
            "1000000000000000",
            SOL.toMicroLamports(1),
            SOL.toMicroLamports(1L),
            SOL.toMicroLamports(1.0),
            SOL.toMicroLamports("1"),
            SOL.toMicroLamports(integer),
            SOL.toMicroLamports(decimal),
        )
        assertAmounts(
            "1000000000",
            SOL.toLamports(1),
            SOL.toLamports(1L),
            SOL.toLamports(1.0),
            SOL.toLamports("1"),
            SOL.toLamports(integer),
            SOL.toLamports(decimal),
        )
        assertAmounts(
            "0.000000001",
            LAMPORT.toSol(1),
            LAMPORT.toSol(1L),
            LAMPORT.toSol(1.0),
            LAMPORT.toSol("1"),
            LAMPORT.toSol(integer),
            LAMPORT.toSol(decimal),
        )
        assertAmounts(
            "0.000000000000001",
            SOL.fromMicroLamports(1),
            SOL.fromMicroLamports(1L),
            SOL.fromMicroLamports(1.0),
            SOL.fromMicroLamports("1"),
            SOL.fromMicroLamports(integer),
            SOL.fromMicroLamports(decimal),
        )
        assertAmounts(
            "0.000000001",
            SOL.fromLamports(1),
            SOL.fromLamports(1L),
            SOL.fromLamports(1.0),
            SOL.fromLamports("1"),
            SOL.fromLamports(integer),
            SOL.fromLamports(decimal),
        )
        assertAmounts(
            "1000000000",
            LAMPORT.fromSol(1),
            LAMPORT.fromSol(1L),
            LAMPORT.fromSol(1.0),
            LAMPORT.fromSol("1"),
            LAMPORT.fromSol(integer),
            LAMPORT.fromSol(decimal),
        )
        assertAmounts(
            "1000000000",
            SOL.convert(1, LAMPORT),
            SOL.convert(1L, LAMPORT),
            SOL.convert(1.0, LAMPORT),
            SOL.convert("1", LAMPORT),
            SOL.convert(integer, LAMPORT),
            SOL.convert(decimal, LAMPORT),
        )
    }

    test("conversions retain fractional lamports and truncate towards zero below one micro-lamport") {
        assertAmounts("0.1", SOL.toLamports("0.0000000001"))
        assertAmounts("0.000001", MICRO_LAMPORT.toLamports("1.9"))
        assertAmounts("-0.000001", MICRO_LAMPORT.toLamports("-1.9"))
        assertAmounts("1", SOL.toMicroLamports("0.0000000000000019"))
        assertAmounts("-1", SOL.toMicroLamports("-0.0000000000000019"))
        assertAmounts("0", SOL.toMicroLamports("0.0000000000000009"), SOL.toMicroLamports("-0.0000000000000009"))
        assertAmounts("1.123456789012345", SOL.toSol("1.1234567890123459"))
        shouldThrow<ArithmeticException> { SOL.toLamports("0.0000000001").toBigIntegerExact() }
    }

    test("decimal inputs avoid floating point artifacts and integer conversion preserves large amounts") {
        assertAmounts("100000000", SOL.toLamports(0.1))
        assertAmounts("300000000", SOL.toLamports(0.3))
        val max = BigInteger("18446744073709551615")
        SOL.toLamports("18446744073.709551615").toBigIntegerExact() shouldBe max
        assertAmounts("18446744073.709551615", LAMPORT.toSol(max))
        // Unit conversion, like EthUnit, is not responsible for transaction amount validation.
        assertAmounts("18446744073709551616", SOL.toLamports("18446744073.709551616"))
        assertAmounts("-1000000000", SOL.toLamports(-1))
    }

    test("custom units match EthUnit conversion behavior") {
        for (fromDecimals in 0..18) {
            for (toDecimals in 0..18) {
                val from = SolUnit(fromDecimals)
                val to = SolUnit(toDecimals)
                val ethFrom = EthUnit(fromDecimals)
                val ethTo = EthUnit(toDecimals)
                for (text in listOf("0", "-0.0000019", "123456789.123456789012345678", "-42.9")) {
                    from.convert(text, to) shouldBeEqualComparingTo ethFrom.convert(text, ethTo)
                    from.convert(BigDecimal(text), to) shouldBeEqualComparingTo ethFrom.convert(BigDecimal(text), ethTo)
                }
                for (integer in listOf(bigIntegerOf(-42), BigInteger("18446744073709551615"))) {
                    from.convert(integer, to) shouldBeEqualComparingTo ethFrom.convert(integer, ethTo)
                }
            }
        }
    }
})
