package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.BaseInstruction
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.SolanaAddress

internal class CompiledMessageFields(
    val header: MessageHeader,
    val accounts: List<SolanaAddress>,
    val recentBlockhash: Blockhash,
    val instructions: List<CompiledInstruction>,
    val lookups: List<CompiledAddressLookupTable>,
)

/** Preserves sol4k's deterministic signed-byte ordering within each account privilege group. */
internal fun compileMessage(
    feePayer: SolanaAddress,
    blockhash: Blockhash,
    instructions: List<Instruction>,
    lookupTables: List<AddressLookupTableAccount>,
): CompiledMessageFields {
    val frozen = instructions.map { BaseInstruction(it.programId, it.keys, it.data) }
    val metas = linkedMapOf(feePayer to KeyMeta(signer = true, writable = true))
    frozen.forEach { instruction ->
        metas.getOrPut(instruction.programId) { KeyMeta() }.invoked = true
        instruction.keys.forEach { account ->
            val meta = metas.getOrPut(account.publicKey) { KeyMeta() }
            meta.signer = meta.signer || account.signer
            meta.writable = meta.writable || account.writable
        }
    }
    val sorted = metas.keys.filter { it != feePayer }.sortedWith { a, b ->
        val x = a.toByteArray()
        val y = b.toByteArray()
        x.indices.firstOrNull { x[it] != y[it] }?.let { x[it].compareTo(y[it]) } ?: 0
    }
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
            val table = tableAddresses.indexOfFirst { key in it }
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
    require(all.size <= 256) { "Too many transaction accounts" }
    val index = all.withIndex().associate { it.value to it.index }
    val compiled = frozen.map { CompiledInstruction(index.getValue(it.programId), it.keys.map { key -> index.getValue(key.publicKey) }, it.data) }
    val lookups = lookupTables.indices.filter { writable[it].isNotEmpty() || readonly[it].isNotEmpty() }.map {
        CompiledAddressLookupTable(lookupTables[it].key, writable[it], readonly[it])
    }
    return CompiledMessageFields(MessageHeader(signedWritable.size + signedReadonly.size, signedReadonly.size, unsignedReadonly.size), static, blockhash, compiled, lookups)
}

private class KeyMeta(var signer: Boolean = false, var writable: Boolean = false, var invoked: Boolean = false)
