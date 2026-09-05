package io.ethers.solana.instruction

import io.ethers.solana.types.Programs
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

object ComputeBudgetProgram {
    @JvmField val ID = Programs.COMPUTE_BUDGET

    /** Cap the transaction at [units] compute units. */
    @JvmStatic
    fun setComputeUnitLimit(units: Long): Instruction = Instruction(ID, emptyList(), byteArrayOf(2) + littleEndian(bigIntegerOf(units), 4))

    /** Bid [microLamports] per compute unit as a prioritization fee. */
    @JvmStatic
    fun setComputeUnitPrice(microLamports: BigInteger): Instruction = Instruction(ID, emptyList(), byteArrayOf(3) + littleEndian(requireU64(microLamports), 8))

    @JvmStatic
    fun setComputeUnitPrice(microLamports: Long): Instruction = setComputeUnitPrice(bigIntegerOf(microLamports))
}
