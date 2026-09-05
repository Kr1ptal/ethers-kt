package io.ethers.solana.types.transaction

import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Signature
import io.ethers.solana.types.SolanaAddress

internal fun validateMessage(header: MessageHeader, accounts: List<SolanaAddress>, instructions: List<CompiledInstruction>, lookups: List<CompiledAddressLookupTable>) {
    require(accounts.size in 1..256 && accounts.distinct().size == accounts.size) { "Invalid static accounts" }
    require(header.requiredSignatures in 1..minOf(127, accounts.size)) { "Invalid required signature count" }
    require(header.readonlySignedAccounts in 0 until header.requiredSignatures) { "Fee payer must be writable" }
    require(header.readonlyUnsignedAccounts in 0..(accounts.size - header.requiredSignatures)) { "Invalid readonly account count" }
    val totalAccounts = accounts.size + lookups.sumOf { it.writableIndexes.size + it.readonlyIndexes.size }
    require(totalAccounts <= 256) { "Too many transaction accounts" }
    require(lookups.all { table -> (table.writableIndexes + table.readonlyIndexes).all { it in 0..255 } }) { "Lookup index out of range" }
    require(instructions.all { it.programIdIndex in 1 until accounts.size && it.accounts.all { index -> index in 0 until totalAccounts } }) { "Instruction account index out of range" }
}

internal fun SolanaMessageEncoder.writeMessageBody(tx: SolanaTransactionUnsigned) {
    writeByte(tx.header.requiredSignatures).writeByte(tx.header.readonlySignedAccounts).writeByte(tx.header.readonlyUnsignedAccounts)
    writeShortVecLength(tx.accounts.size)
    tx.accounts.forEach { writeBytes(it.toByteArray()) }
    writeBytes(tx.recentBlockhash.toByteArray()).writeShortVecLength(tx.instructions.size)
    tx.instructions.forEach { instruction ->
        writeByte(instruction.programIdIndex).writeShortVecLength(instruction.accounts.size)
        instruction.accounts.forEach { writeByte(it) }
        writeShortVecLength(instruction.data.size).writeBytes(instruction.data)
    }
}

internal fun decodeMessage(bytes: ByteArray): SolanaTransactionUnsigned {
    val decoder = SolanaMessageDecoder(bytes)
    val prefix = decoder.readByte()
    require(prefix <= 128) { "Unsupported transaction message version" }
    val versioned = prefix == 128
    val header = MessageHeader(if (versioned) decoder.readByte() else prefix, decoder.readByte(), decoder.readByte())
    val count = decoder.readShortVecLength()
    require(count in 1..256) { "Invalid static account count" }
    val accounts = List(count) { SolanaAddress(decoder.readBytes(32)) }
    val blockhash = Blockhash(decoder.readBytes(32))
    val instructions = List(decoder.readShortVecLength()) {
        val program = decoder.readByte()
        val indices = List(decoder.readShortVecLength()) { decoder.readByte() }
        CompiledInstruction(program, indices, decoder.readBytes(decoder.readShortVecLength()))
    }
    val tx = if (versioned) {
        val lookups = List(decoder.readShortVecLength()) {
            val key = SolanaAddress(decoder.readBytes(32))
            val writable = List(decoder.readShortVecLength()) { decoder.readByte() }
            val readonly = List(decoder.readShortVecLength()) { decoder.readByte() }
            CompiledAddressLookupTable(key, writable, readonly)
        }
        SolanaTxV0(header, accounts, blockhash, instructions, lookups)
    } else {
        SolanaTxLegacy(header, accounts, blockhash, instructions)
    }
    decoder.requireDone()
    return tx
}

internal fun validateSignatures(tx: SolanaTransactionUnsigned, signatures: List<Signature?>) {
    require(signatures.size == tx.header.requiredSignatures) { "Signature count does not match message" }
    val message = tx.serializeMessage()
    val signers = tx.signers
    signatures.forEachIndexed { index, signature ->
        require(signature == null || signers[index].verify(signature, message)) { "Invalid transaction signature" }
    }
}

internal fun encodeTransactionEnvelope(tx: SolanaTransactionUnsigned, signatures: List<Signature?>): ByteArray {
    val encoder = SolanaMessageEncoder().writeShortVecLength(signatures.size)
    signatures.forEach { encoder.writeBytes(it?.toByteArray() ?: ByteArray(64)) }
    return encoder.writeBytes(tx.serializeMessage()).toByteArray()
}

internal fun decodeTransactionEnvelope(bytes: ByteArray): Pair<SolanaTransactionUnsigned, List<Signature?>> {
    val decoder = SolanaMessageDecoder(bytes)
    val count = decoder.readShortVecLength()
    require(count in 1..127) { "Invalid signature count" }
    val signatures = List(count) {
        val signature = decoder.readBytes(64)
        if (signature.all { it == 0.toByte() }) null else Signature(signature)
    }
    val tx = decodeMessage(decoder.readBytes(decoder.remaining))
    require(tx.header.requiredSignatures == count) { "Signature count does not match message" }
    return tx to signatures
}
