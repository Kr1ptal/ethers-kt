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

/** Header, accounts, blockhash and instructions, shared by the legacy and v0 message layouts. */
internal class DecodedMessageBody(
    val header: MessageHeader,
    val accounts: List<SolanaAddress>,
    val recentBlockhash: SolanaBlockhash,
    val instructions: List<CompiledInstruction>,
)

/** The required-signature count has already been consumed, as legacy encodes it in place of a version byte. */
internal fun SolanaMessageDecoder.readMessageBody(requiredSignatures: Int): DecodedMessageBody {
    val header = MessageHeader(requiredSignatures, readByte(), readByte())
    val count = readShortVecLength()
    require(count in 1..256) { "Invalid static account count" }
    val accounts = List(count) { SolanaAddress(readBytes(32)) }
    val blockhash = SolanaBlockhash(readBytes(32))
    val instructions = List(readShortVecLength()) {
        val program = readByte()
        val indices = List(readShortVecLength()) { readByte() }
        CompiledInstruction(program, indices, readBytes(readShortVecLength()))
    }
    return DecodedMessageBody(header, accounts, blockhash, instructions)
}

/** Canonical shortvec length prefix width, rejecting counts the wire format cannot represent. */
internal fun shortVecSize(count: Int): Long {
    require(count in 0..65535) { "Shortvec length out of range" }
    return if (count < 128) 1L else if (count < 16384) 2L else 3L
}

/** Legacy and v0 envelope size, including every required signature slot. */
internal fun legacyEnvelopeSize(tx: SolanaTransactionUnsigned, lookups: List<CompiledAddressLookupTable>?): Long {
    val accounts = tx.accounts
    val instructions = tx.instructions
    var size = shortVecSize(tx.header.requiredSignatures) + 64L * tx.header.requiredSignatures +
        3L + shortVecSize(accounts.size) + 32L * accounts.size + 32L + shortVecSize(instructions.size)
    size += instructions.sumOf { 1L + shortVecSize(it.accounts.size) + it.accounts.size + shortVecSize(it.data.size) + it.data.size }
    if (lookups != null) {
        size += 1L + shortVecSize(lookups.size)
        size += lookups.sumOf { 32L + shortVecSize(it.writableIndexes.size) + it.writableIndexes.size + shortVecSize(it.readonlyIndexes.size) + it.readonlyIndexes.size }
    }
    return size
}

/** Legacy and v0 envelopes put the signature vector before the message. */
internal fun encodeSignaturesFirstEnvelope(tx: SolanaTransactionUnsigned, signatures: List<SolanaSignature?>): ByteArray {
    val encoder = SolanaMessageEncoder().writeShortVecLength(signatures.size)
    signatures.forEach { encoder.writeBytes(it?.toByteArray() ?: ByteArray(64)) }
    return encoder.writeBytes(tx.serializeMessage()).toByteArray()
}

internal fun validateSignatures(tx: SolanaTransactionUnsigned, signatures: List<SolanaSignature?>) {
    require(signatures.size == tx.header.requiredSignatures) { "Signature count does not match message" }
    val message = tx.serializeMessage()
    val signers = tx.signers
    signatures.forEachIndexed { index, signature ->
        require(signature == null || signers[index].verify(signature, message)) { "Invalid transaction signature" }
    }
}

internal fun decodeMessage(bytes: ByteArray): SolanaTransactionUnsigned {
    val decoder = SolanaMessageDecoder(bytes)
    val tx = decoder.readVersionedMessage()
    decoder.requireDone()
    return tx
}

internal fun decodeTransactionEnvelope(bytes: ByteArray): Pair<SolanaTransactionUnsigned, List<SolanaSignature?>> {
    val decoder = SolanaMessageDecoder(bytes)
    if (bytes.firstOrNull() == 129.toByte()) {
        require(bytes.size <= SolanaTxV1.MAX_TRANSACTION_SIZE) { "V1 transaction exceeds ${SolanaTxV1.MAX_TRANSACTION_SIZE} bytes" }
        decoder.readByte()
        val tx = SolanaTxV1.decodeBody(decoder)
        val signatures = List(tx.header.requiredSignatures) { decoder.readSignatureSlot() }
        decoder.requireDone()
        return tx to signatures
    }
    val count = decoder.readShortVecLength()
    require(count in 1..127) { "Invalid signature count" }
    val signatures = List(count) { decoder.readSignatureSlot() }
    val tx = decoder.readSignaturesFirstMessage()
    decoder.requireDone()
    require(tx.header.requiredSignatures == count) { "Signature count does not match message" }
    return tx to signatures
}

/** Dispatch on the version prefix, delegating the body to the type that owns that layout. */
private fun SolanaMessageDecoder.readVersionedMessage(): SolanaTransactionUnsigned {
    val prefix = readByte()
    return when {
        prefix == 129 -> SolanaTxV1.decodeBody(this)
        prefix == 128 -> SolanaTxV0.decodeBody(this)
        prefix <= 127 -> SolanaTxLegacy.decodeBody(this, prefix)
        else -> throw IllegalArgumentException("Unsupported transaction message version")
    }
}

/** As [readVersionedMessage], but rejects v1, whose signatures follow the message instead of preceding it. */
private fun SolanaMessageDecoder.readSignaturesFirstMessage(): SolanaTransactionUnsigned {
    val prefix = readByte()
    return when {
        prefix == 128 -> SolanaTxV0.decodeBody(this)
        prefix <= 127 -> SolanaTxLegacy.decodeBody(this, prefix)
        prefix == 129 -> throw IllegalArgumentException("V1 signatures must follow the message")
        else -> throw IllegalArgumentException("Unsupported transaction message version")
    }
}

private fun SolanaMessageDecoder.readSignatureSlot(): SolanaSignature? {
    val signature = readBytes(64)
    return if (signature.all { it == 0.toByte() }) null else SolanaSignature(signature)
}
