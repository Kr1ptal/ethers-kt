@file:JvmName("SolanaProgramFailures")

package io.ethers.solana.program

import io.ethers.solana.codec.SolanaProgramError
import io.ethers.solana.codec.SolanaProgramErrorDecoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.InstructionError
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.TransactionError
import io.ethers.solana.types.rpc.TransactionSimulation
import io.ethers.solana.types.transaction.SolanaTransaction
import kotlin.jvm.JvmName

/**
 * A custom program error that failed top-level instruction [instructionIndex] with [code], attributed to
 * the program that raised it.
 *
 * The error a transaction reports names only the top-level instruction, but the program that raised it may
 * have been invoked by a CPI further down. [programId] is that program, when the logs or inner instructions
 * identify it, and null when they do not; [stackHeight] is its invocation depth, when known.
 */
data class SolanaProgramFailure(
    val instructionIndex: Int,
    val code: Long,
    val programId: SolanaAddress?,
    val stackHeight: Int?,
) {
    internal companion object {
        /**
         * The custom program error [err] reports, attributed to a program when [logs] name one that failed
         * with the same instruction and code. Null when [err] is not a custom program error.
         */
        fun fromLogs(err: TransactionError?, logs: List<String>?): SolanaProgramFailure? {
            val failure = err as? TransactionError.InstructionFailure ?: return null
            val code = (failure.instructionError as? InstructionError.Custom)?.code ?: return null
            val index = failure.instructionIndex
            val logged = logs?.let { ProgramLogs.parse(it, collectData = false).failure }
            if (logged == null || logged.code != code || logged.instructionIndex != index) return SolanaProgramFailure(index, code, null, null)
            return SolanaProgramFailure(index, code, logged.programId, logged.stackHeight)
        }
    }
}

/**
 * The error this program declares for [failure], or null when [failure] was not attributed to [programId]
 * or the program declares no error with its code. Codes are only meaningful within one program, so a
 * failure without an attributed program never resolves.
 */
fun SolanaProgramErrorDecoder.decode(failure: SolanaProgramFailure, programId: SolanaAddress): SolanaProgramError? {
    return if (failure.programId == programId) decode(failure.code) else null
}

/**
 * The custom program error this transaction failed with, or null when it succeeded or failed otherwise.
 *
 * It is attributed first by the logs, whose first `failed` line names the program that raised it, then by
 * the inner instructions: a top-level instruction that made no CPIs raised it itself.
 */
fun SolanaRPCTransaction.programFailure(): SolanaProgramFailure? {
    // attribution by the logs does not need the accounts, so it survives a message whose accounts do not resolve
    return SolanaExecution.of(this, emptyList()).unwrapOrNull()?.programFailure() ?: meta?.let { SolanaProgramFailure.fromLogs(it.err, it.logMessages) }
}

/** The custom program error [transaction] failed with in this simulation, or null when it did not. */
fun ContextValue<TransactionSimulation>.programFailure(transaction: SolanaTransaction): SolanaProgramFailure? {
    return SolanaExecution.of(this, transaction, emptyList()).unwrapOrNull()?.programFailure() ?: SolanaProgramFailure.fromLogs(value.err, value.logs)
}

/**
 * The custom program error the notified transaction failed with, or null when it did not. Only the logs
 * are available to attribute it.
 */
fun ContextValue<LogsNotification>.programFailure(): SolanaProgramFailure? = SolanaProgramFailure.fromLogs(value.err, value.logs)
