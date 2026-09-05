package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.PublicKey
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

class TransferInstruction(from: PublicKey, to: PublicKey, lamports: BigInteger) : BaseInstruction(
    Programs.SYSTEM,
    listOf(AccountMeta.signerAndWritable(from), AccountMeta.writable(to)),
    byteArrayOf(2, 0, 0, 0) + littleEndian(requireU64(lamports), 8),
) {
    constructor(from: PublicKey, to: PublicKey, lamports: Long) : this(from, to, bigIntegerOf(lamports))
}
