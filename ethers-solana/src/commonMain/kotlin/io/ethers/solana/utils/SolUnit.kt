package io.ethers.solana.utils

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmStatic

/** Exact decimal unit conversion. No floating point rounding is applied. */
object SolUnit {
    @JvmStatic fun lamportsToSol(amount: BigInteger): BigDecimal = BigDecimal(amount.toString()).movePointLeft(9)
    @JvmStatic fun solToLamports(amount: String): BigInteger = requireU64(BigDecimal(amount).movePointRight(9).toBigIntegerExact())
    @JvmStatic fun microLamportsToLamports(amount: BigInteger): BigDecimal = BigDecimal(amount.toString()).movePointLeft(6)
    @JvmStatic fun lamportsToMicroLamports(amount: BigInteger): BigInteger = amount.multiply(BigInteger("1000000"))
}
