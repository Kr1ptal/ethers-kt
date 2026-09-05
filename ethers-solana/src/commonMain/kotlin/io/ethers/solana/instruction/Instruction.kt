package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ktor.websocket.ChannelOverflow

interface Instruction {
    val programId: SolanaAddress
    val keys: List<AccountMeta>
    val data: ByteArray
}

/** An instruction for any program; inputs and exposed byte arrays are copied. */
open class BaseInstruction(
    override val programId: SolanaAddress,
    override val keys: List<AccountMeta>,
    override val data: ByteArray,
) : Instruction
