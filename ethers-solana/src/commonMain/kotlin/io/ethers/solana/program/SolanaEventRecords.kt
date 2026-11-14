@file:JvmName("SolanaEventRecords")

package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaEventContext
import io.ethers.solana.codec.SolanaEventDecoder
import io.ethers.solana.codec.SolanaEventSource
import io.ethers.solana.codec.SolanaExecutionContext
import io.ethers.solana.codec.SolanaExecutionStatus
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.TransactionSimulation
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionError
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Event data a program emitted, before decoding: the Solana counterpart of an EVM log. [data] starts with
 * the event's discriminator; [context] names the emitting program and where and in which execution the
 * event was emitted.
 */
data class SolanaEventRecord(val data: SolanaBytes, val context: SolanaEventContext) {
    val programId: SolanaAddress get() = context.programId
}

/** Decode [record] with this decoder. */
fun <T> SolanaEventDecoder<T>.decode(record: SolanaEventRecord): Result<T, SolanaDecodeError> = decode(record.data.asByteArray(), record.context)

/**
 * Decode the records [programId] emitted with [decoder], in order. Records that do not decode, such as
 * events this decoder does not know, are skipped.
 */
fun <T> List<SolanaEventRecord>.decodeEvents(programId: SolanaAddress, decoder: SolanaEventDecoder<T>): List<T> {
    return mapNotNull { record -> if (record.programId == programId) decoder.decode(record).unwrapOrNull() else null }
}

/**
 * The events this transaction's programs emitted, both to the logs (Anchor's `emit!`) and through self-CPIs
 * (Anchor's `emit_cpi!`), ordered by top-level instruction. Within one instruction, log events come before
 * CPI events, since the response does not say how the two interleave.
 *
 * A self-CPI only counts as an event when its first account is the program's event authority PDA, which only
 * that program can sign for. Events of a failed transaction are included, marked
 * [SolanaExecutionStatus.FAILED]: they were emitted, but nothing they describe was committed. Their context
 * is marked incomplete when logs or inner instructions were not recorded, or the logs were truncated.
 *
 * Transactions from other sources, such as a Geyser/Yellowstone stream, carry the same fields: build a
 * [SolanaRPCTransaction] from them to extract their events the same way.
 */
@JvmOverloads
fun SolanaRPCTransaction.eventRecords(tables: List<AddressLookupTableAccount> = emptyList()): Result<List<SolanaEventRecord>, SolanaTransactionError> {
    return SolanaExecution.of(this, tables).andThen { it.eventRecords() }
}

/**
 * The events [transaction] emitted in this simulation, as [SolanaRPCTransaction.eventRecords] describes, as of
 * the simulation's slot and without a signature. CPI events are only included when the simulation was asked
 * to return inner instructions.
 */
@JvmOverloads
fun ContextValue<TransactionSimulation>.eventRecords(
    transaction: SolanaTransaction,
    tables: List<AddressLookupTableAccount> = emptyList(),
): Result<List<SolanaEventRecord>, SolanaTransactionError> {
    return SolanaExecution.of(this, transaction, tables).andThen { it.eventRecords() }
}

/**
 * The events a logs notification carries. Only log events are available: the notification has no inner
 * instructions, so events emitted through self-CPIs cannot be seen here.
 */
fun ContextValue<LogsNotification>.eventRecords(): List<SolanaEventRecord> = logEventRecords(null)

/** As [eventRecords], decoding only the data [onlyProgram] logged when it is set. */
internal fun ContextValue<LogsNotification>.logEventRecords(onlyProgram: SolanaAddress?): List<SolanaEventRecord> {
    val logs = ProgramLogs.parse(value.logs, onlyProgram = onlyProgram)
    val status = if (value.err == null) SolanaExecutionStatus.SUCCEEDED else SolanaExecutionStatus.FAILED
    val execution = SolanaExecutionContext(value.signature, context.slot.toLong(), status, incomplete = logs.truncated)
    return logs.data.map { SolanaEventRecord(it.data, SolanaEventContext(it.programId, SolanaEventSource.Log(it.instructionIndex, it.logIndex, it.stackHeight), execution)) }
}
