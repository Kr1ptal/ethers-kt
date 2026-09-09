package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash

internal class CompiledMessageFields(
    val header: MessageHeader,
    val accounts: List<SolanaAddress>,
    val recentBlockhash: SolanaBlockhash,
    val instructions: List<MessageInstruction>,
    val lookups: List<CompiledAddressLookupTable>,
)

/** Bytes saved by moving one account out of the inline list: its 32-byte key, less a one-byte index. */
private const val SAVED_PER_ACCOUNT = 31

/** Bytes spent naming one table: its 32-byte key, plus a length for each of its two index vectors. */
private const val COST_PER_TABLE = 34

/** Beyond this many tables, searching every subset stops being free, so fall back to a greedy pick. */
private const val EXHAUSTIVE_TABLE_LIMIT = 12

/**
 * Choose which of [lookupTables] to compile against, so a v0 message is never larger for having been
 * given a table it does not need.
 *
 * Moving an account out of the inline list saves [SAVED_PER_ACCOUNT] bytes and naming a table costs
 * [COST_PER_TABLE], so a table only pays for itself once it holds two accounts this transaction can
 * move. Only accounts that may leave the inline list count: not the fee payer, any other signer, or
 * any invoked program.
 *
 * Picking tables one at a time by how many accounts they hold is not enough, because the widest table
 * can straddle two narrower ones that together cover more: given `(a b)`, `(a d)` and `(b c)`, taking
 * `(a b)` first strands `c` and `d` in tables that no longer pay for themselves, while `(a d)` and
 * `(b c)` move all four. Every subset is therefore scored, up to [EXHAUSTIVE_TABLE_LIMIT] tables;
 * beyond that the count is far past what a transaction can afford to name, and the greedy pick is used
 * instead. Ties keep the earlier of the supplied tables, so the choice is deterministic and follows
 * the caller's own ordering.
 *
 * The two constants hold while the vectors stay under 128 entries, where every length is a single
 * byte; past that a chosen set can be a byte or two off the true optimum.
 */
private fun selectLookupTables(movable: List<SolanaAddress>, lookupTables: List<AddressLookupTableAccount>): List<Int> {
    if (lookupTables.isEmpty() || movable.isEmpty()) return emptyList()

    val coverage = lookupTables.map { table -> movable.filterTo(mutableSetOf()) { it in table.addresses } }
    if (lookupTables.size > EXHAUSTIVE_TABLE_LIMIT) return greedySelection(coverage)

    var best = emptyList<Int>()
    var bestSaving = 0
    for (subset in 1 until (1 shl lookupTables.size)) {
        val selected = lookupTables.indices.filter { subset shr it and 1 == 1 }
        val covered = mutableSetOf<SolanaAddress>()
        selected.forEach { covered.addAll(coverage[it]) }
        val saving = covered.size * SAVED_PER_ACCOUNT - selected.size * COST_PER_TABLE
        // a strict improvement only, so the earliest subset wins any tie
        if (saving > bestSaving) {
            best = selected
            bestSaving = saving
        }
    }
    return best
}

/** Repeatedly take the table holding the most accounts no chosen table holds yet. */
private fun greedySelection(coverage: List<Set<SolanaAddress>>): List<Int> {
    val remaining = coverage.flatten().toMutableSet()
    val selected = mutableListOf<Int>()
    while (true) {
        var best = -1
        var bestCovered = COST_PER_TABLE / SAVED_PER_ACCOUNT // a table below the break-even costs more than it saves
        coverage.forEachIndexed { index, covered ->
            if (index in selected) return@forEachIndexed
            val marginal = remaining.count { it in covered }
            if (marginal > bestCovered) {
                best = index
                bestCovered = marginal
            }
        }
        if (best < 0) return selected
        selected.add(best)
        remaining.removeAll(coverage[best])
    }
}

/** Preserves sol4k's deterministic signed-byte ordering within each account privilege group. */
internal fun compileMessage(
    feePayer: SolanaAddress,
    blockhash: SolanaBlockhash,
    instructions: List<Instruction>,
    lookupTables: List<AddressLookupTableAccount>,
): Result<CompiledMessageFields, SolanaTransactionError> {
    val metas = linkedMapOf(feePayer to KeyMeta(signer = true, writable = true))
    instructions.forEach { instruction ->
        metas.getOrPut(instruction.programId) { KeyMeta() }.invoked = true
        instruction.keys.forEach { account ->
            val meta = metas.getOrPut(account.publicKey) { KeyMeta() }
            meta.signer = meta.signer || account.signer
            meta.writable = meta.writable || account.writable
        }
    }
    val sorted = metas.keys.filter { it != feePayer }.sortedWith { a, b ->
        val x = a.asByteArray()
        val y = b.asByteArray()
        x.indices.firstOrNull { x[it] != y[it] }?.let { x[it].compareTo(y[it]) } ?: 0
    }
    val movable = sorted.filter {
        val meta = metas.getValue(it)
        !meta.signer && !meta.invoked
    }
    val selected = selectLookupTables(movable, lookupTables)

    val signedWritable = mutableListOf(feePayer)
    val signedReadonly = mutableListOf<SolanaAddress>()
    val unsignedWritable = mutableListOf<SolanaAddress>()
    val unsignedReadonly = mutableListOf<SolanaAddress>()
    val writable = List(lookupTables.size) { mutableListOf<Int>() }
    val readonly = List(lookupTables.size) { mutableListOf<Int>() }
    val tableAddresses = lookupTables.map { it.addresses }
    for (key in sorted) {
        val meta = metas.getValue(key)
        if (!meta.signer && !meta.invoked) {
            val table = selected.firstOrNull { key in tableAddresses[it] } ?: -1
            if (table >= 0) {
                (if (meta.writable) writable[table] else readonly[table]).add(tableAddresses[table].indexOf(key))
                continue
            }
        }
        when {
            meta.signer && meta.writable -> signedWritable
            meta.signer -> signedReadonly
            meta.writable -> unsignedWritable
            else -> unsignedReadonly
        }.add(key)
    }
    val static = signedWritable + signedReadonly + unsignedWritable + unsignedReadonly
    val all = static + writable.flatMapIndexed { table, indices -> indices.map { tableAddresses[table][it] } } + readonly.flatMapIndexed { table, indices -> indices.map { tableAddresses[table][it] } }
    if (all.size > 256) return Result.failure(SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, all.size, 1..256))
    val index = all.withIndex().associate { it.value to it.index }
    val compiled = instructions.map { MessageInstruction(index.getValue(it.programId), it.keys.map { key -> index.getValue(key.publicKey) }, it.data) }
    val lookups = lookupTables.indices.filter { writable[it].isNotEmpty() || readonly[it].isNotEmpty() }.map {
        CompiledAddressLookupTable(lookupTables[it].key, writable[it], readonly[it])
    }
    return Result.success(
        CompiledMessageFields(MessageHeader(signedWritable.size + signedReadonly.size, signedReadonly.size, unsignedReadonly.size), static, blockhash, compiled, lookups),
    )
}

private class KeyMeta(var signer: Boolean = false, var writable: Boolean = false, var invoked: Boolean = false)
