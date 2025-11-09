package io.ethers.core.utils

import io.ethers.core.utils.GasUtils.getEffectiveGasTip
import java.math.BigInteger

object GasUtils {
    /**
     * Get how much will be paid as transaction gas tip based on [baseFee], [gasTipCap], and [gasFeeCap] constraints.
     * */
    @JvmStatic
    @Suppress("UnnecessaryVariable")
    fun getEffectiveGasTip(baseFee: BigInteger, gasTipCap: BigInteger, gasFeeCap: BigInteger): BigInteger {
        val possibleTip = gasFeeCap - baseFee
        val maxTip = gasTipCap

        return maxTip.min(possibleTip)
    }

    /**
     * Get how much will be paid as transaction gas price. This is the sum of [baseFee] and [getEffectiveGasTip].
     * */
    @JvmStatic
    fun getEffectiveGasPrice(baseFee: BigInteger, gasTipCap: BigInteger, gasFeeCap: BigInteger): BigInteger {
        return baseFee + getEffectiveGasTip(baseFee, gasTipCap, gasFeeCap)
    }
}
