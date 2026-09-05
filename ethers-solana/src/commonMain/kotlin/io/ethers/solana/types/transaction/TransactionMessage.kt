package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.BaseInstruction
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.PublicKey
import io.ethers.solana.utils.BinaryReader
import io.ethers.solana.utils.BinaryWriter
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

enum class MessageVersion { LEGACY, V0 }

data class MessageHeader(val requiredSignatures: Int, val readonlySignedAccounts: Int, val readonlyUnsignedAccounts: Int)

class AddressLookupTableAccount(val key: PublicKey, addresses: List<PublicKey>) {
    private val entries = addresses.toList().also { require(it.size <= 256) { "Lookup table exceeds 256 addresses" } }
    val addresses: List<PublicKey> get() = entries.toList()
}

class CompiledInstruction(val programIdIndex: Int, accounts: List<Int>, data: ByteArray) {
    private val indices = accounts.toList()
    private val payload = data.copyOf()
    val accounts: List<Int> get() = indices.toList()
    val data: ByteArray get() = payload.copyOf()
}

class CompiledAddressLookupTable(val key: PublicKey, writableIndexes: List<Int>, readonlyIndexes: List<Int>) {
    private val writable = writableIndexes.toList()
    private val readonly = readonlyIndexes.toList()
    val writableIndexes: List<Int> get() = writable.toList()
    val readonlyIndexes: List<Int> get() = readonly.toList()
}

/** Immutable compiled legacy/v0 message. Lookup table addresses are supplied by the caller at compilation. */
class TransactionMessage internal constructor(
    val version: MessageVersion,
    val header: MessageHeader,
    accounts: List<PublicKey>,
    val recentBlockhash: Blockhash,
    instructions: List<CompiledInstruction>,
    addressLookupTables: List<CompiledAddressLookupTable>,
) {
    private val staticAccounts = accounts.toList()
    private val compiledInstructions = instructions.toList()
    private val lookups = addressLookupTables.toList()
    val accounts: List<PublicKey> get() = staticAccounts.toList()
    val instructions: List<CompiledInstruction> get() = compiledInstructions.toList()
    val addressLookupTables: List<CompiledAddressLookupTable> get() = lookups.toList()
    val signers: List<PublicKey> get() = staticAccounts.take(header.requiredSignatures)

    init {
        require(staticAccounts.size in 1..256 && staticAccounts.distinct().size == staticAccounts.size) { "Invalid static accounts" }
        require(header.requiredSignatures in 1..minOf(127, staticAccounts.size)) { "Invalid required signature count" }
        require(header.readonlySignedAccounts in 0 until header.requiredSignatures) { "Fee payer must be writable" }
        require(header.readonlyUnsignedAccounts in 0..(staticAccounts.size - header.requiredSignatures)) { "Invalid readonly account count" }
        require(version == MessageVersion.V0 || lookups.isEmpty()) { "Legacy messages cannot use lookup tables" }
        val totalAccounts = staticAccounts.size + lookups.sumOf { it.writableIndexes.size + it.readonlyIndexes.size }
        require(totalAccounts <= 256) { "Too many transaction accounts" }
        require(lookups.all { table -> (table.writableIndexes + table.readonlyIndexes).all { it in 0..255 } }) { "Lookup index out of range" }
        require(compiledInstructions.all { it.programIdIndex in 1 until staticAccounts.size && it.accounts.all { index -> index in 0 until totalAccounts } }) { "Instruction account index out of range" }
    }

    fun withNewBlockhash(blockhash: Blockhash): TransactionMessage = TransactionMessage(version, header, staticAccounts, blockhash, compiledInstructions, lookups)

    fun serialize(): ByteArray {
        val writer = BinaryWriter()
        if (version == MessageVersion.V0) writer.byte(128)
        writer.byte(header.requiredSignatures).byte(header.readonlySignedAccounts).byte(header.readonlyUnsignedAccounts)
        writer.length(staticAccounts.size)
        staticAccounts.forEach { writer.bytes(it.toByteArray()) }
        writer.bytes(recentBlockhash.toByteArray()).length(compiledInstructions.size)
        compiledInstructions.forEach { instruction ->
            writer.byte(instruction.programIdIndex).length(instruction.accounts.size)
            instruction.accounts.forEach { writer.byte(it) }
            writer.length(instruction.data.size).bytes(instruction.data)
        }
        if (version == MessageVersion.V0) {
            writer.length(lookups.size)
            lookups.forEach { table ->
                writer.bytes(table.key.toByteArray()).length(table.writableIndexes.size)
                table.writableIndexes.forEach { writer.byte(it) }
                writer.length(table.readonlyIndexes.size)
                table.readonlyIndexes.forEach { writer.byte(it) }
            }
        }
        return writer.toByteArray()
    }

    companion object {
        @JvmStatic
        fun deserialize(bytes: ByteArray): TransactionMessage {
            val reader = BinaryReader(bytes)
            val prefix = reader.byte()
            require(prefix < 128 || prefix == 128) { "Unsupported transaction message version" }
            val version = if (prefix == 128) MessageVersion.V0 else MessageVersion.LEGACY
            val header = MessageHeader(if (version == MessageVersion.V0) reader.byte() else prefix, reader.byte(), reader.byte())
            val count = reader.length()
            require(count in 1..256) { "Invalid static account count" }
            val accounts = List(count) { PublicKey(reader.bytes(32)) }
            val blockhash = Blockhash(reader.bytes(32))
            val instructions = List(reader.length()) {
                val program = reader.byte()
                val indices = List(reader.length()) { reader.byte() }
                CompiledInstruction(program, indices, reader.bytes(reader.length()))
            }
            val lookups = if (version == MessageVersion.LEGACY) emptyList() else List(reader.length()) {
                val key = PublicKey(reader.bytes(32))
                val writable = List(reader.length()) { reader.byte() }
                val readonly = List(reader.length()) { reader.byte() }
                CompiledAddressLookupTable(key, writable, readonly)
            }
            reader.requireDone()
            return TransactionMessage(version, header, accounts, blockhash, instructions, lookups)
        }

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: PublicKey, blockhash: Blockhash, instruction: Instruction, lookupTables: List<AddressLookupTableAccount> = emptyList(), version: MessageVersion = MessageVersion.V0): TransactionMessage = compile(feePayer, blockhash, listOf(instruction), lookupTables, version)

        /** Preserves sol4k's deterministic signed-byte ordering within each account privilege group. */
        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: PublicKey, blockhash: Blockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList(), version: MessageVersion = MessageVersion.V0): TransactionMessage {
            require(version == MessageVersion.V0 || lookupTables.isEmpty()) { "Legacy messages cannot use lookup tables" }
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
            val signedReadonly = mutableListOf<PublicKey>()
            val unsignedWritable = mutableListOf<PublicKey>()
            val unsignedReadonly = mutableListOf<PublicKey>()
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
            return TransactionMessage(version, MessageHeader(signedWritable.size + signedReadonly.size, signedReadonly.size, unsignedReadonly.size), static, blockhash, compiled, lookups)
        }
    }
}

private class KeyMeta(var signer: Boolean = false, var writable: Boolean = false, var invoked: Boolean = false)
