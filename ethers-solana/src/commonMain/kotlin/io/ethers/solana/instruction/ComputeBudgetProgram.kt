package io.ethers.solana.instruction

import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The ComputeBudget program, which owns the discriminants and value widths of its four instructions.
 *
 * The transaction layer reads these settings back off a compiled message, and does so against the
 * same definitions used to write them, so encoding and decoding cannot drift apart.
 */
object ComputeBudgetProgram {
    @JvmField val ID = Programs.COMPUTE_BUDGET

    internal const val REQUEST_HEAP_FRAME = 1
    internal const val SET_COMPUTE_UNIT_LIMIT = 2
    internal const val SET_COMPUTE_UNIT_PRICE = 3
    internal const val SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT = 4

    /**
     * Bytes this instruction's value occupies after the discriminant byte, or -1 for a discriminant
     * this program does not define.
     */
    internal fun payloadWidth(discriminant: Int): Int = when (discriminant) {
        REQUEST_HEAP_FRAME, SET_COMPUTE_UNIT_LIMIT, SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> 4
        SET_COMPUTE_UNIT_PRICE -> 8
        else -> -1
    }

    /** Cap the transaction at [units] compute units. */
    @JvmStatic
    fun setComputeUnitLimit(units: Long): Instruction = encode(SET_COMPUTE_UNIT_LIMIT, bigIntegerOf(units))

    /** Request [bytes] of heap space, a multiple of 1 KiB in 32..256 KiB. */
    @JvmStatic
    fun requestHeapFrame(bytes: Long): Instruction = encode(REQUEST_HEAP_FRAME, bigIntegerOf(bytes))

    /** Cap the combined size of the accounts the transaction loads at [bytes]. */
    @JvmStatic
    fun setLoadedAccountsDataSizeLimit(bytes: Long): Instruction = encode(SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT, bigIntegerOf(bytes))

    /** Bid [microLamports] per compute unit as a prioritization fee. */
    @JvmStatic
    fun setComputeUnitPrice(microLamports: BigInteger): Instruction = encode(SET_COMPUTE_UNIT_PRICE, requireU64(microLamports))

    @JvmStatic
    fun setComputeUnitPrice(microLamports: Long): Instruction = setComputeUnitPrice(bigIntegerOf(microLamports))

    /**
     * The value an instruction of this program carries, read at the width [payloadWidth] states.
     * The caller has already established, through [Instruction.data], that this is such an instruction.
     */
    internal fun decodeValue(discriminant: Int, data: SolanaBytes): BigInteger {
        val decoder = SolanaMessageDecoder(data.asByteArray())
        decoder.readByte()
        return decoder.readUnsignedLittleEndian(payloadWidth(discriminant))
    }

    private fun encode(discriminant: Int, value: BigInteger): Instruction = Instruction(ID, emptyList(), byteArrayOf(discriminant.toByte()) + littleEndian(value, payloadWidth(discriminant)))
}
