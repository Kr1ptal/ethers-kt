package io.ethers.solana.codec

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction

/**
 * Decodes the instructions of one program. A generated program's `decodeInstruction` decodes any of its
 * instructions. [checkSigners] is false for inner instructions, whose signer flags are incomplete; see
 * [SolanaAccountsReader].
 */
fun interface SolanaInstructionDecoder<out T> {
    fun decode(instruction: Instruction, checkSigners: Boolean): Result<T, SolanaDecodeError>
}
