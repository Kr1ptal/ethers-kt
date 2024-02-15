package io.ethers.core.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class GasUtilsTest : FunSpec({
    context("getEffectiveGasTip") {
        test("tip cap equals max possible tip") {
            val baseFee = BigInteger("65")
            val gasTipCap = BigInteger("1")
            val gasFeeCap = BigInteger("66")

            val effectiveTip = GasUtils.getEffectiveGasTip(baseFee, gasTipCap, gasFeeCap)
            effectiveTip shouldBe BigInteger("1")
        }

        test("tip cap less than max possible tip") {
            val baseFee = BigInteger("65")
            val gasTipCap = BigInteger("4")
            val gasFeeCap = BigInteger("79")

            val effectiveTip = GasUtils.getEffectiveGasTip(baseFee, gasTipCap, gasFeeCap)
            effectiveTip shouldBe BigInteger("4")
        }

        test("tip cap less more than max possible tip") {
            val baseFee = BigInteger("65")
            val gasTipCap = BigInteger("40")
            val gasFeeCap = BigInteger("79")

            val effectiveTip = GasUtils.getEffectiveGasTip(baseFee, gasTipCap, gasFeeCap)
            effectiveTip shouldBe BigInteger("14")
        }
    }

    test("getEffectiveGasPrice") {
        val baseFee = BigInteger("65")
        val gasTipCap = BigInteger("3")
        val gasFeeCap = BigInteger("72")
        val result = GasUtils.getEffectiveGasPrice(baseFee, gasTipCap, gasFeeCap)
        result shouldBe BigInteger("68")
    }
})