package io.ethers.solana.utils

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

// Public-data-only field arithmetic. This must never be used to implement secret-key signing.
// Solana PDA checks use Edwards decompression, not prime-subgroup/signature validity.
private val P = bigIntegerOf(2).pow(255).subtract(bigIntegerOf(19))
private val ONE = bigIntegerOf(1)
private val D = bigIntegerOf(-121665).multiply(bigIntegerOf(121666).modInverse(P)).mod(P)
private val LEGENDRE_EXPONENT = P.subtract(ONE).shiftRight(1)

internal fun isEd25519Point(bytes: ByteArray): Boolean {
    if (bytes.size != 32) return false
    val yBytes = bytes.copyOf()
    yBytes[31] = (yBytes[31].toInt() and 0x7f).toByte()
    // Match field-element decoding used by Solana's curve25519-dalek: reduce the encoded y modulo p.
    val y = BigInteger(1, yBytes.reversedArray()).mod(P)
    val y2 = y.multiply(y).mod(P)
    val numerator = y2.subtract(ONE).mod(P)
    val denominator = D.multiply(y2).add(ONE).mod(P)
    if (denominator.signum() == 0) return false
    val x2 = numerator.multiply(denominator.modInverse(P)).mod(P)
    return x2.signum() == 0 || x2.modPow(LEGENDRE_EXPONENT, P) == ONE
}
