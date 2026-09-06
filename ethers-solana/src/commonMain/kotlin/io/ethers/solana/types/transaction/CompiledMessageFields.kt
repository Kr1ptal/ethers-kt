package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash

internal class CompiledMessageFields(
    val header: MessageHeader,
    val accounts: List<SolanaAddress>,
    val recentBlockhash: SolanaBlockhash,
    val instructions: List<CompiledInstruction>,
    val lookups: List<CompiledAddressLookupTable>,
)

/**
 * Choose which of [lookupTables] to compile against, so a v0 message is never larger for having been
 * given a table it does not need.
 *
 * Moving an account out of the inline list saves its 32 address bytes and costs one index byte, a net
 * 31; naming a table costs its 32 key bytes plus two vector lengths, a net 34. A table therefore only
 * pays for itself once it covers two accounts this transaction can move, which rules out the accounts
 * that must stay inline: the fee payer, every other signer, and every invoked program.
 *
 * Tables are taken greedily by how many not-yet-covered accounts they hold, so overlapping tables
 * concentrate into as few as possible. Ties keep the earlier of the supplied tables, so the choice is
 * deterministic and follows the caller's own ordering.
 */
private fun selectLookupTables(movable: List<SolanaAddress>, lookupTables: List<AddressLookupTableAccount>): List<Int> {
    if (lookupTables.isEmpty() || movable.isEmpty()) return emptyList()

    val remaining = movable.toMutableSet()
    val selected = mutableListOf<Int>()
    while (true) {
        var best = -1
        var bestCovered = 1 // a table covering a single account would cost three bytes more than it saves
        lookupTables.forEachIndexed { index, table ->
            if (index in selected) return@forEachIndexed
            val covered = remaining.count { it in table.addresses }
            if (covered > bestCovered) {
                best = index
                bestCovered = covered
            }
        }
        if (best < 0) return selected
        selected.add(best)
        remaining.removeAll { it in lookupTables[best].addresses }
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
    val compiled = instructions.map { CompiledInstruction(index.getValue(it.programId), it.keys.map { key -> index.getValue(key.publicKey) }, it.data) }
    val lookups = lookupTables.indices.filter { writable[it].isNotEmpty() || readonly[it].isNotEmpty() }.map {
        CompiledAddressLookupTable(lookupTables[it].key, writable[it], readonly[it])
    }
    return Result.success(
        CompiledMessageFields(MessageHeader(signedWritable.size + signedReadonly.size, signedReadonly.size, unsignedReadonly.size), static, blockhash, compiled, lookups),
    )
}

private class KeyMeta(var signer: Boolean = false, var writable: Boolean = false, var invoked: Boolean = false)
