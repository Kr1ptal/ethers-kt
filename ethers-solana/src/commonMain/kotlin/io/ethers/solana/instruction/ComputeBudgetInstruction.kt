package io.ethers.solana.instruction

import io.ethers.solana.types.Programs
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

class SetComputeUnitLimitInstruction(units: Long) : BaseInstruction(
    Programs.COMPUTE_BUDGET,
    emptyList(),
    byteArrayOf(2) + littleEndian(bigIntegerOf(units), 4),
)

class SetComputeUnitPriceInstruction(microLamports: BigInteger) : BaseInstruction(
    Programs.COMPUTE_BUDGET,
    emptyList(),
    byteArrayOf(3) + littleEndian(requireU64(microLamports), 8),
) {
    constructor(microLamports: Long) : this(bigIntegerOf(microLamports))
}
