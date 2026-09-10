package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature

internal fun SolanaMessageEncoder.writeMessageBody(tx: SolanaTransactionUnsigned) {
    writeByte(tx.header.requiredSignatures).writeByte(tx.header.readonlySignedAccounts).writeByte(tx.header.readonlyUnsignedAccounts)
    writeShortVecLength(tx.accounts.size)
    tx.accounts.forEach { writeBytes(it.asByteArray()) }
    writeBytes(tx.recentBlockhash.asByteArray()).writeShortVecLength(tx.instructions.size)
    tx.instructions.forEach { instruction ->
        writeByte(instruction.programIdIndex).writeShortVecLength(instruction.accounts.size)
        instruction.accounts.forEach { writeByte(it) }
        writeShortVecLength(instruction.data.size).writeBytes(instruction.data.asByteArray())
    }
}

/** Header, accounts, blockhash and instructions, shared by the legacy and v0 message layouts. */
internal class DecodedMessageBody(
    val header: MessageHeader,
    val accounts: List<SolanaAddress>,
    val recentBlockhash: SolanaBlockhash,
    val instructions: List<MessageInstruction>,
)

/** The required-signature count has already been consumed, as legacy encodes it in place of a version byte. */
internal fun SolanaMessageDecoder.readMessageBody(requiredSignatures: Int): DecodedMessageBody {
    val header = MessageHeader(requiredSignatures, readByte(), readByte())
    val count = readShortVecLength()
    if (count !in 1..256) throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, count, 1..256).toException()
    val accounts = List(count) { SolanaAddress(readBytes(32)) }
    val blockhash = SolanaBlockhash(readBytes(32))
    val instructions = List(readShortVecLength()) {
        val program = readByte()
        val indices = List(readShortVecLength()) { readByte() }
        MessageInstruction(program, indices, SolanaBytes.fromBytes(readBytes(readShortVecLength())))
    }
    return DecodedMessageBody(header, accounts, blockhash, instructions)
}

/** Canonical shortvec length prefix width, rejecting counts the wire format cannot represent. */
internal fun shortVecSize(count: Int): Long {
    require(count in 0..65535) { "Shortvec length out of range" }
    return if (count < 128) 1L else if (count < 16384) 2L else 3L
}

/**
 * Legacy and v0 envelope size, including every required signature slot. Takes the raw fields rather
 * than a transaction so that it can run before one is constructed.
 */
internal fun legacyEnvelopeSize(
    header: MessageHeader,
    accounts: List<SolanaAddress>,
    instructions: List<MessageInstruction>,
    lookups: List<CompiledAddressLookupTable>?,
): Long {
    var size = shortVecSize(header.requiredSignatures) + 64L * header.requiredSignatures +
        3L + shortVecSize(accounts.size) + 32L * accounts.size + 32L + shortVecSize(instructions.size)
    size += instructions.sumOf { 1L + shortVecSize(it.accounts.size) + it.accounts.size + shortVecSize(it.data.size) + it.data.size }
    if (lookups != null) {
        size += 1L + shortVecSize(lookups.size)
        size += lookups.sumOf { 32L + shortVecSize(it.writableIndexes.size) + it.writableIndexes.size + shortVecSize(it.readonlyIndexes.size) + it.readonlyIndexes.size }
    }
    return size
}

/** The envelope exceeds the version's wire limit. */
internal fun envelopeSizeError(type: SolanaTxType, size: Long, max: Int): SolanaTransactionError? = if (size > max) SolanaTransactionError.EnvelopeTooLarge(type, size, max) else null

/** Legacy and v0 envelopes put the signature vector before the message. */
internal fun encodeSignaturesFirstEnvelope(tx: SolanaTransactionUnsigned, signatures: List<SolanaSignature?>): ByteArray {
    val encoder = SolanaMessageEncoder().writeShortVecLength(signatures.size)
    signatures.forEach { encoder.writeBytes(it?.asByteArray() ?: ByteArray(64)) }
    return encoder.writeBytes(tx.serializeMessage()).toByteArray()
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
        if (bytes.size > SolanaTxV1.MAX_TRANSACTION_SIZE) {
            throw SolanaTransactionError.EnvelopeTooLarge(SolanaTxType.V1, bytes.size.toLong(), SolanaTxV1.MAX_TRANSACTION_SIZE).toException()
        }
        decoder.readByte()
        val tx = SolanaTxV1.decodeBody(decoder)
        val signatures = List(tx.header.requiredSignatures) { decoder.readSignatureSlot() }
        decoder.requireDone()
        return tx to signatures
    }
    val count = decoder.readShortVecLength()
    if (count !in 1..127) throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.SIGNERS, count, 1..127).toException()
    val signatures = List(count) { decoder.readSignatureSlot() }
    val tx = decoder.readSignaturesFirstMessage()
    decoder.requireDone()
    if (tx.header.requiredSignatures != count) {
        throw SolanaTransactionError.InvalidMessage(
            SolanaTransactionError.Reason.SIGNATURE_COUNT,
            "Envelope carries $count signatures, but the message requires ${tx.header.requiredSignatures}",
        ).toException()
    }
    return tx to signatures
}

/** Dispatch on the version prefix, delegating the body to the type that owns that layout. */
private fun SolanaMessageDecoder.readVersionedMessage(): SolanaTransactionUnsigned {
    val prefix = readByte()
    return when {
        prefix == 129 -> SolanaTxV1.decodeBody(this)
        prefix == 128 -> SolanaTxV0.decodeBody(this)
        prefix <= 127 -> SolanaTxLegacy.decodeBody(this, prefix)
        else -> throw SolanaTransactionError.UnsupportedVersion(prefix).toException()
    }
}

/** As [readVersionedMessage], but rejects v1, whose signatures follow the message instead of preceding it. */
private fun SolanaMessageDecoder.readSignaturesFirstMessage(): SolanaTransactionUnsigned {
    val prefix = readByte()
    return when {
        prefix == 128 -> SolanaTxV0.decodeBody(this)
        prefix <= 127 -> SolanaTxLegacy.decodeBody(this, prefix)
        prefix == 129 -> throw SolanaTransactionError.MalformedBytes("V1 signatures must follow the message").toException()
        else -> throw SolanaTransactionError.UnsupportedVersion(prefix).toException()
    }
}

private fun SolanaMessageDecoder.readSignatureSlot(): SolanaSignature? {
    val signature = readBytes(64)
    return if (signature.all { it == 0.toByte() }) null else SolanaSignature(signature)
}

/**
 * Run a construction or decoding step, returning its typed failure as a value.
 *
 * The wire decoder reports truncated and non-canonical input by throwing, so anything that is not
 * already a [SolanaTransactionException] is surfaced as [SolanaTransactionError.MalformedBytes].
 */
internal inline fun <T> catchTransactionError(block: () -> T): Result<T, SolanaTransactionError> = try {
    Result.success(block())
} catch (e: SolanaTransactionException) {
    Result.failure(e.error)
} catch (e: IllegalArgumentException) {
    Result.failure(SolanaTransactionError.MalformedBytes(e.message ?: "Malformed transaction bytes", e))
} catch (e: IndexOutOfBoundsException) {
    Result.failure(SolanaTransactionError.MalformedBytes(e.message ?: "Malformed transaction bytes", e))
}
