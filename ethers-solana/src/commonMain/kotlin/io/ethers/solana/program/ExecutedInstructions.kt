@file:JvmName("ExecutedInstructions")

package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaInstructionDecoder
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.InnerInstructions
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.TransactionSimulation
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.resolveAccountMetas
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

/**
 * An instruction a transaction ran, with its accounts resolved to addresses.
 *
 * The account flags are the transaction's, not the ones the instruction was invoked with: a transaction
 * can grant an account more privileges than one instruction asked for, and an inner instruction can sign
 * for a program derived address the transaction does not mark as a signer.
 */
data class ExecutedInstruction(val position: InstructionPosition, val instruction: Instruction)

/** An instruction of one program with its [position] and the result of decoding it. */
data class DecodedInstruction<out T>(val position: InstructionPosition, val instruction: Instruction, val value: Result<T, SolanaDecodeError>)

/**
 * The instructions this transaction ran, in execution order: each top-level instruction followed by its
 * inner instructions. Addresses loaded from lookup tables are taken from the response, then from [tables].
 * Inner instructions are only included when the response recorded them.
 */
@JvmOverloads
fun SolanaRPCTransaction.executedInstructions(tables: List<AddressLookupTableAccount> = emptyList()): Result<List<ExecutedInstruction>, SolanaTransactionError> {
    val accounts = resolveAccounts(tables).unwrapOrReturn { return failure(it) }
    return executed(this, accounts, meta?.innerInstructions)
}

/**
 * The instructions [transaction] ran in this simulation, in execution order, as for
 * [SolanaRPCTransaction.executedInstructions]. Inner instructions are only included when the simulation
 * was asked to return them.
 */
@JvmOverloads
fun TransactionSimulation.executedInstructions(
    transaction: SolanaTransaction,
    tables: List<AddressLookupTableAccount> = emptyList(),
): Result<List<ExecutedInstruction>, SolanaTransactionError> {
    val accounts = resolveAccountMetas(transaction, tables, loadedAddresses).unwrapOrReturn { return failure(it) }
    return executed(transaction, accounts, innerInstructions)
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

private fun executed(
    transaction: SolanaTransaction,
    accounts: List<AccountMeta>,
    innerInstructions: List<InnerInstructions>?,
): Result<List<ExecutedInstruction>, SolanaTransactionError> {
    val inner = innerInstructions.orEmpty().associateBy { it.index }
    val result = ArrayList<ExecutedInstruction>(transaction.instructions.size)
    transaction.instructions.forEachIndexed { index, compiled ->
        result.add(ExecutedInstruction(InstructionPosition(index), resolve(compiled, accounts).unwrapOrReturn { return failure(it) }))
        inner[index]?.instructions?.forEachIndexed { innerIndex, innerCompiled ->
            val position = InstructionPosition(index, innerIndex, innerCompiled.stackHeight?.toInt())
            result.add(ExecutedInstruction(position, resolve(innerCompiled, accounts).unwrapOrReturn { return failure(it) }))
        }
    }
    return success(result)
}

private fun resolve(compiled: CompiledInstruction, accounts: List<AccountMeta>): Result<Instruction, SolanaTransactionError> {
    fun invalid(index: Int) = failure(
        SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.ACCOUNT_INDEX, "Account index $index is outside the ${accounts.size} resolved accounts"),
    )
    val program = accounts.getOrNull(compiled.programIdIndex) ?: return invalid(compiled.programIdIndex)
    val keys = compiled.accounts.map { accounts.getOrNull(it) ?: return invalid(it) }
    return success(Instruction(program.publicKey, keys, compiled.data))
}
