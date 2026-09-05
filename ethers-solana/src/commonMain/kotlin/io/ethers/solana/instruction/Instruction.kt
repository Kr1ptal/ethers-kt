package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress

interface Instruction {
    val programId: SolanaAddress
    val keys: List<AccountMeta>
    val data: ByteArray
}

/** An instruction for any program; inputs and exposed byte arrays are copied. */
open class BaseInstruction(override val programId: SolanaAddress, keys: List<AccountMeta>, data: ByteArray) : Instruction {
    private val accountKeys = keys.toList()
    private val payload = data.copyOf()
    final override val keys: List<AccountMeta> get() = accountKeys.toList()
    final override val data: ByteArray get() = payload.copyOf()
}
