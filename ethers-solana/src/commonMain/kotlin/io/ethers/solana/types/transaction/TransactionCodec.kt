package io.ethers.solana.types.transaction

import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature

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
    if (prefix == 129) {
        val tx = decoder.readV1Message()
        decoder.requireDone()
        return tx
    }
    require(prefix <= 128) { "Unsupported transaction message version" }
    val versioned = prefix == 128
    val header = MessageHeader(if (versioned) decoder.readByte() else prefix, decoder.readByte(), decoder.readByte())
    val count = decoder.readShortVecLength()
    require(count in 1..256) { "Invalid static account count" }
    val accounts = List(count) { SolanaAddress(decoder.readBytes(32)) }
    val blockhash = SolanaBlockhash(decoder.readBytes(32))
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

internal fun validateSignatures(tx: SolanaTransactionUnsigned, signatures: List<SolanaSignature?>) {
    require(signatures.size == tx.header.requiredSignatures) { "Signature count does not match message" }
    val message = tx.serializeMessage()
    val signers = tx.signers
    signatures.forEachIndexed { index, signature ->
        require(signature == null || signers[index].verify(signature, message)) { "Invalid transaction signature" }
    }
}

internal fun encodeTransactionEnvelope(tx: SolanaTransactionUnsigned, signatures: List<SolanaSignature?>): ByteArray {
    if (tx is SolanaTxV1) {
        val encoder = SolanaMessageEncoder().writeBytes(tx.serializeMessage())
        signatures.forEach { encoder.writeBytes(it?.toByteArray() ?: ByteArray(64)) }
        return encoder.toByteArray()
    }
    val encoder = SolanaMessageEncoder().writeShortVecLength(signatures.size)
    signatures.forEach { encoder.writeBytes(it?.toByteArray() ?: ByteArray(64)) }
    return encoder.writeBytes(tx.serializeMessage()).toByteArray()
}

internal fun decodeTransactionEnvelope(bytes: ByteArray): Pair<SolanaTransactionUnsigned, List<SolanaSignature?>> {
    val decoder = SolanaMessageDecoder(bytes)
    if (bytes.firstOrNull() == 129.toByte()) {
        require(bytes.size <= SolanaTxV1.MAX_TRANSACTION_SIZE) { "V1 transaction exceeds ${SolanaTxV1.MAX_TRANSACTION_SIZE} bytes" }
        decoder.readByte()
        val tx = decoder.readV1Message()
        val signatures = List(tx.header.requiredSignatures) { decoder.readSignatureSlot() }
        decoder.requireDone()
        return tx to signatures
    }
    val count = decoder.readShortVecLength()
    require(count in 1..127) { "Invalid signature count" }
    val signatures = List(count) { decoder.readSignatureSlot() }
    val tx = decodeMessage(decoder.readBytes(decoder.remaining))
    require(tx !is SolanaTxV1) { "V1 signatures must follow the message" }
    require(tx.header.requiredSignatures == count) { "Signature count does not match message" }
    return tx to signatures
}

private fun SolanaMessageDecoder.readSignatureSlot(): SolanaSignature? {
    val signature = readBytes(64)
    return if (signature.all { it == 0.toByte() }) null else SolanaSignature(signature)
}

/** The version byte has already been consumed. Leaves trailing signature bytes for the envelope reader. */
private fun SolanaMessageDecoder.readV1Message(): SolanaTxV1 {
    val header = MessageHeader(readByte(), readByte(), readByte())
    val mask = readUnsignedLittleEndian(4).toLong()
    require(mask and 31L == mask) { "Unsupported v1 config mask" }
    require(mask and 3L == 0L || mask and 3L == 3L) { "Both priority-fee config bits must be set" }
    val blockhash = SolanaBlockhash(readBytes(32))
    val instructionCount = readByte()
    val accountCount = readByte()
    require(instructionCount <= 64 && accountCount in 1..64 && header.requiredSignatures in 1..12) { "Invalid v1 counts" }
    val accounts = List(accountCount) { SolanaAddress(readBytes(32)) }
    val config = SolanaTransactionConfig(
        priorityFee = if (mask and 3L != 0L) readUnsignedLittleEndian(8) else null,
        computeUnitLimit = if (mask and 4L != 0L) readUnsignedLittleEndian(4).toLong() else null,
        loadedAccountsDataSizeLimit = if (mask and 8L != 0L) readUnsignedLittleEndian(4).toLong() else null,
        heapSize = if (mask and 16L != 0L) readUnsignedLittleEndian(4).toLong() else null,
    )
    val headers = List(instructionCount) { Triple(readByte(), readByte(), readUnsignedLittleEndian(2).toInt()) }
    val instructions = headers.map { (program, count, size) ->
        CompiledInstruction(program, List(count) { readByte() }, readBytes(size))
    }
    return SolanaTxV1(header, accounts, blockhash, instructions, config)
}
