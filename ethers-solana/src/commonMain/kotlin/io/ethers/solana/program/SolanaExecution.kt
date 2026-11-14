package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.codec.SolanaEventContext
import io.ethers.solana.codec.SolanaEventSource
import io.ethers.solana.codec.SolanaExecutionContext
import io.ethers.solana.codec.SolanaExecutionStatus
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.InnerInstructions
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.TransactionError
import io.ethers.solana.types.rpc.TransactionSimulation
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.LoadedAddresses
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.resolveAccountMetas

/**
 * What one transaction executed, from a fetched transaction or a simulation: the single implementation
 * behind their instruction, event and failure extraction.
 *
 * [accounts] is the message's full account list with its flags, loaded addresses included, which the
 * [instructions] and [innerInstructions] index into. [innerInstructions] and [logs] are null when the source
 * did not record them; [err] is null for a transaction that succeeded.
 */
internal class SolanaExecution(
    val accounts: List<AccountMeta>,
    val instructions: List<CompiledInstruction>,
    val innerInstructions: List<InnerInstructions>?,
    val logs: List<String>?,
    val err: TransactionError?,
    val signature: SolanaSignature?,
    val slot: Long?,
) {
    val status: SolanaExecutionStatus get() = if (err == null) SolanaExecutionStatus.SUCCEEDED else SolanaExecutionStatus.FAILED

    /**
     * The instructions this transaction ran, in execution order: each top-level instruction followed by its
     * inner instructions, which are only included when the source recorded them.
     *
     * The account flags are the transaction's, not the ones an instruction was invoked with: a transaction
     * can grant an account more privileges than one instruction asked for, and an inner instruction can
     * sign for a program derived address the transaction does not mark as a signer.
     */
    fun executedInstructions(): Result<List<ExecutedInstruction>, SolanaTransactionError> {
        val inner = innerInstructions.orEmpty().associateBy { it.index }
        val result = ArrayList<ExecutedInstruction>(instructions.size)
        instructions.forEachIndexed { index, compiled ->
            result.add(ExecutedInstruction(InstructionPosition(index), resolve(compiled).unwrapOrReturn { return failure(it) }))
            inner[index]?.instructions?.forEachIndexed { innerIndex, innerCompiled ->
                val position = InstructionPosition(index, innerIndex, innerCompiled.stackHeight?.toInt())
                result.add(ExecutedInstruction(position, resolve(innerCompiled).unwrapOrReturn { return failure(it) }))
            }
        }
        return success(result)
    }

    /**
     * The events this transaction's programs emitted, both to the logs (Anchor's `emit!`) and through
     * self-CPIs (Anchor's `emit_cpi!`), ordered by top-level instruction. Within one instruction, log events
     * come before CPI events, since the sources do not say how the two interleave.
     *
     * A self-CPI only counts as an event when its first account is the program's event authority PDA, which
     * only that program can sign for. Events of a failed transaction are included, marked
     * [SolanaExecutionStatus.FAILED]: they were emitted, but nothing they describe was committed. Their
     * context is marked incomplete when logs or inner instructions were not recorded, or the logs were
     * truncated.
     */
    fun eventRecords(): Result<List<SolanaEventRecord>, SolanaTransactionError> {
        val parsed = logs?.let { ProgramLogs.parse(it) }
        val context = executionContext(incomplete = parsed == null || parsed.truncated || innerInstructions == null)

        val records = ArrayList<Pair<Int, SolanaEventRecord>>()
        parsed?.data?.forEach {
            val source = SolanaEventSource.Log(it.instructionIndex, it.logIndex, it.stackHeight)
            records.add(it.instructionIndex to SolanaEventRecord(it.data, SolanaEventContext(it.programId, source, context)))
        }
        if (innerInstructions != null) {
            val authorities = HashMap<SolanaAddress, SolanaAddress>()
            for ((position, instruction) in executedInstructions().unwrapOrReturn { return failure(it) }) {
                if (!position.isInner || !isEventCpi(instruction.data)) continue
                val authority = authorities.getOrPut(instruction.programId) { eventAuthority(instruction.programId) }
                if (instruction.keys.firstOrNull()?.publicKey != authority) continue
                val source = SolanaEventSource.Cpi(position.instructionIndex, position.innerIndex!!, position.stackHeight)
                val data = instruction.data.slice(EVENT_IX_TAG.size, instruction.data.size)
                records.add(position.instructionIndex to SolanaEventRecord(data, SolanaEventContext(instruction.programId, source, context)))
            }
        }
        return success(records.sortedBy { it.first }.map { it.second })
    }

    /**
     * The custom program error this transaction failed with, attributed first by the logs, whose first
     * `failed` line names the program that raised it, then by the inner instructions: a top-level
     * instruction that made no CPIs raised it itself. Null when the transaction succeeded or failed otherwise.
     */
    fun programFailure(): SolanaProgramFailure? {
        val failure = SolanaProgramFailure.fromLogs(err, logs) ?: return null
        if (failure.programId != null) return failure
        val index = failure.instructionIndex
        if (innerInstructions != null && innerInstructions.none { it.index == index }) {
            val program = instructions.getOrNull(index)?.let { accounts.getOrNull(it.programIdIndex) }
            if (program != null) return failure.copy(programId = program.publicKey, stackHeight = 1)
        }
        return failure
    }

    internal fun executionContext(incomplete: Boolean) = SolanaExecutionContext(signature, slot, status, incomplete)

    private fun resolve(compiled: CompiledInstruction): Result<Instruction, SolanaTransactionError> {
        fun invalid(index: Int) = failure(
            SolanaTransactionError.InvalidMessage(
                SolanaTransactionError.Reason.ACCOUNT_INDEX,
                "Account index $index is outside the ${accounts.size} resolved accounts",
            ),
        )
        val program = accounts.getOrNull(compiled.programIdIndex) ?: return invalid(compiled.programIdIndex)
        val keys = compiled.accounts.map { accounts.getOrNull(it) ?: return invalid(it) }
        return success(Instruction(program.publicKey, keys, compiled.data))
    }

    companion object {
        /** Anchor's `EVENT_IX_TAG`, little-endian: the data prefix of a self-CPI that carries an event. */
        private val EVENT_IX_TAG = byteArrayOf(0xe4.toByte(), 0x45, 0xa5.toByte(), 0x2e, 0x51, 0xcb.toByte(), 0x9a.toByte(), 0x1d)

        /**
         * What [transaction] executed. Addresses loaded from lookup tables are taken from the response, then
         * from [tables]: a table's contents can change after the transaction, so the response's own record wins.
         */
        fun of(transaction: SolanaRPCTransaction, tables: List<AddressLookupTableAccount>): Result<SolanaExecution, SolanaTransactionError> {
            val meta = transaction.meta
            return of(
                transaction = transaction,
                loadedAddresses = meta?.loadedAddresses,
                innerInstructions = meta?.innerInstructions,
                logs = meta?.logMessages,
                err = meta?.err,
                signature = transaction.transaction.signatures.firstOrNull(),
                slot = transaction.slot.toLong(),
                tables = tables,
            )
        }

        /**
         * What [transaction] executed in [simulation], as of the simulation's slot. A simulation has no
         * signature; inner instructions are only recorded when the simulation was asked to return them.
         */
        fun of(
            simulation: ContextValue<TransactionSimulation>,
            transaction: SolanaTransaction,
            tables: List<AddressLookupTableAccount>,
        ): Result<SolanaExecution, SolanaTransactionError> {
            val value = simulation.value
            return of(transaction, value.loadedAddresses, value.innerInstructions, value.logs, value.err, null, simulation.context.slot.toLong(), tables)
        }

        /**
         * The execution of [transaction], with its accounts resolved from the message, then [loadedAddresses]
         * when the source reports the addresses it loaded, then [tables].
         */
        fun of(
            transaction: SolanaTransaction,
            loadedAddresses: LoadedAddresses?,
            innerInstructions: List<InnerInstructions>?,
            logs: List<String>?,
            err: TransactionError?,
            signature: SolanaSignature?,
            slot: Long?,
            tables: List<AddressLookupTableAccount>,
        ): Result<SolanaExecution, SolanaTransactionError> {
            val accounts = resolveAccountMetas(transaction, tables, loadedAddresses).unwrapOrReturn { return failure(it) }
            return success(SolanaExecution(accounts, transaction.instructions, innerInstructions, logs, err, signature, slot))
        }

        private fun isEventCpi(data: SolanaBytes): Boolean {
            if (data.size < EVENT_IX_TAG.size) return false
            for (i in EVENT_IX_TAG.indices) if (data[i] != EVENT_IX_TAG[i]) return false
            return true
        }

        private fun eventAuthority(programId: SolanaAddress): SolanaAddress {
            return SolanaAddress.findProgramAddress(listOf("__event_authority".encodeToByteArray()), programId).address
        }
    }
}
