@file:JvmName("ExecutedInstructions")

package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaInstructionDecoder
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.TransactionSimulation
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionError
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Where an instruction ran within its transaction: top-level instruction [instructionIndex], and for an
 * inner instruction its [innerIndex] among that instruction's inner instructions. [stackHeight] is 1 for a
 * top-level instruction and null when the node did not report it for an inner one.
 */
data class InstructionPosition @JvmOverloads constructor(val instructionIndex: Int, val innerIndex: Int? = null, val stackHeight: Int? = 1) {
    val isInner: Boolean get() = innerIndex != null
}

/** An instruction a transaction ran, with its accounts resolved; see [executedInstructions]. */
data class ExecutedInstruction(val position: InstructionPosition, val instruction: Instruction)

/** An instruction of one program with its [position] and the result of decoding it. */
data class DecodedInstruction<out T>(val position: InstructionPosition, val instruction: Instruction, val value: Result<T, SolanaDecodeError>)

/**
 * The instructions this transaction ran, in execution order: each top-level instruction followed by its
 * inner instructions, which are only included when the response recorded them. Addresses loaded from lookup
 * tables are taken from the response, then from [tables].
 *
 * The account flags are the transaction's, not the ones an instruction was invoked with: a transaction can
 * grant an account more privileges than one instruction asked for, and an inner instruction can sign for a
 * program derived address the transaction does not mark as a signer.
 */
@JvmOverloads
fun SolanaRPCTransaction.executedInstructions(tables: List<AddressLookupTableAccount> = emptyList()): Result<List<ExecutedInstruction>, SolanaTransactionError> {
    return SolanaExecution.of(this, tables).andThen { it.executedInstructions() }
}

/**
 * The instructions [transaction] ran in this simulation, in execution order. Inner instructions are only
 * included when the simulation was asked to return them.
 */
@JvmOverloads
fun TransactionSimulation.executedInstructions(
    transaction: SolanaTransaction,
    tables: List<AddressLookupTableAccount> = emptyList(),
): Result<List<ExecutedInstruction>, SolanaTransactionError> {
    return SolanaExecution.of(transaction, loadedAddresses, innerInstructions, logs, err, null, null, tables).andThen { it.executedInstructions() }
}

/**
 * Decode the instructions that target [programId] with [decoder], keeping their positions. Signer
 * privileges are only checked for top-level instructions.
 */
fun <T> List<ExecutedInstruction>.decodeInstructions(programId: SolanaAddress, decoder: SolanaInstructionDecoder<T>): List<DecodedInstruction<T>> {
    return mapNotNull { executed ->
        if (executed.instruction.programId != programId) return@mapNotNull null
        DecodedInstruction(executed.position, executed.instruction, decoder.decode(executed.instruction, !executed.position.isInner))
    }
}
