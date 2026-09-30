package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import kotlin.io.encoding.Base64

internal fun SolanaMessageEncoder.writeMessageBody(tx: SolanaTransactionUnsigned) {
    writeByte(tx.header.requiredSignatures).writeByte(tx.header.readonlySignedAccounts).writeByte(tx.header.readonlyUnsignedAccounts)
    writeShortVecLength(tx.accounts.size)
    tx.accounts.forEach { writeBytes(it.asByteArray()) }
    writeBytes(tx.recentBlockhash.asByteArray()).writeShortVecLength(tx.instructions.size)
    tx.instructions.forEach { instruction ->
        writeByte(instruction.programIdIndex).writeShortVecLength(instruction.accounts.size).writeU8List(instruction.accounts)
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
internal fun SolanaMessageDecoder.readMessageBody(requiredSignatures: Int): Result<DecodedMessageBody, SolanaTransactionError> {
    val header = MessageHeader(requiredSignatures, readByte(), readByte())
    val count = readShortVecLength()
    if (failed) return Result.failure(malformed())
    if (count !in 1..256) return Result.failure(SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, count, 1..256))
    val accounts = readList(count) { SolanaAddress(readBytes(32)) }
    val blockhash = SolanaBlockhash(readBytes(32))
    val instructions = readList(readShortVecLength()) {
        val program = readByte()
        val indices = readU8List(readShortVecLength())
        MessageInstruction(program, indices, SolanaBytes.fromBytes(readBytes(readShortVecLength())))
    }
    if (failed) return Result.failure(malformed())
    return Result.success(DecodedMessageBody(header, accounts, blockhash, instructions))
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
    val message = tx.messageBytes()
    val encoder = SolanaMessageEncoder(shortVecSize(signatures.size).toInt() + 64 * signatures.size + message.size)
        .writeShortVecLength(signatures.size)
    signatures.forEach { if (it == null) encoder.writeZeros(64) else encoder.writeBytes(it.asByteArray()) }
    return encoder.writeBytes(message).finish()
}

/**
 * View slots the caller has checked are all filled as non-null signatures. The decoded list is not
 * shared with anyone, so it is kept rather than copied.
 */
@Suppress("UNCHECKED_CAST")
internal fun List<SolanaSignature?>.filled(): List<SolanaSignature> = this as List<SolanaSignature>

/** Map a decoder that stopped on malformed input into the error it recorded. */
internal fun SolanaMessageDecoder.malformed(): SolanaTransactionError.MalformedBytes = SolanaTransactionError.MalformedBytes(error ?: "Malformed transaction bytes")

internal fun decodeMessage(bytes: ByteArray): Result<SolanaTransactionUnsigned, SolanaTransactionError> {
    val decoder = SolanaMessageDecoder(bytes)
    val tx = decoder.readVersionedMessage().unwrapOrReturn { return Result.failure(it) }
    decoder.requireDone()
    if (decoder.failed) return Result.failure(decoder.malformed())
    return Result.success(tx)
}

internal fun decodeTransactionEnvelope(bytes: ByteArray): Result<Pair<SolanaTransactionUnsigned, List<SolanaSignature?>>, SolanaTransactionError> {
    val decoder = SolanaMessageDecoder(bytes)
    if (bytes.firstOrNull() == 129.toByte()) {
        if (bytes.size > SolanaTxV1.MAX_TRANSACTION_SIZE) {
            return Result.failure(SolanaTransactionError.EnvelopeTooLarge(SolanaTxType.V1, bytes.size.toLong(), SolanaTxV1.MAX_TRANSACTION_SIZE))
        }
        decoder.readByte()
        val tx = SolanaTxV1.decodeBody(decoder, 0).unwrapOrReturn { return Result.failure(it) }
        val signatures = decoder.readList(tx.header.requiredSignatures) { decoder.readSignatureSlot() }
        decoder.requireDone()
        if (decoder.failed) return Result.failure(decoder.malformed())
        return Result.success(tx to signatures)
    }
    val count = decoder.readShortVecLength()
    if (decoder.failed) return Result.failure(decoder.malformed())
    if (count !in 1..127) return Result.failure(SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.SIGNERS, count, 1..127))
    val signatures = decoder.readList(count) { decoder.readSignatureSlot() }
    val tx = decoder.readSignaturesFirstMessage().unwrapOrReturn { return Result.failure(it) }
    decoder.requireDone()
    if (decoder.failed) return Result.failure(decoder.malformed())
    if (tx.header.requiredSignatures != count) {
        return Result.failure(
            SolanaTransactionError.InvalidMessage(
                SolanaTransactionError.Reason.SIGNATURE_COUNT,
                "Envelope carries $count signatures, but the message requires ${tx.header.requiredSignatures}",
            ),
        )
    }
    return Result.success(tx to signatures)
}

/** Dispatch on the version prefix, delegating the body to the type that owns that layout. */
private fun SolanaMessageDecoder.readVersionedMessage(): Result<SolanaTransactionUnsigned, SolanaTransactionError> {
    val start = position
    val prefix = readByte()
    if (failed) return Result.failure(malformed())
    return when {
        prefix == 129 -> SolanaTxV1.decodeBody(this, start)
        prefix == 128 -> SolanaTxV0.decodeBody(this, start)
        prefix <= 127 -> SolanaTxLegacy.decodeBody(this, prefix, start)
        else -> Result.failure(SolanaTransactionError.UnsupportedVersion(prefix))
    }
}

/** As [readVersionedMessage], but rejects v1, whose signatures follow the message instead of preceding it. */
private fun SolanaMessageDecoder.readSignaturesFirstMessage(): Result<SolanaTransactionUnsigned, SolanaTransactionError> {
    val start = position
    val prefix = readByte()
    if (failed) return Result.failure(malformed())
    return when {
        prefix == 128 -> SolanaTxV0.decodeBody(this, start)
        prefix <= 127 -> SolanaTxLegacy.decodeBody(this, prefix, start)
        prefix == 129 -> Result.failure(SolanaTransactionError.MalformedBytes("V1 signatures must follow the message"))
        else -> Result.failure(SolanaTransactionError.UnsupportedVersion(prefix))
    }
}

private fun SolanaMessageDecoder.readSignatureSlot(): SolanaSignature? {
    if (peekZeros(64)) {
        skip(64)
        return null
    }
    val signature = readBytes(64)
    // a failed read zero-fills, which is still an empty slot
    return if (failed) null else SolanaSignature(signature)
}

/**
 * Base64 is the only step of decoding an envelope that reports failure by throwing, since it belongs
 * to the standard library. Isolating it here keeps the wire decoders themselves exception-free.
 */
internal fun decodeBase64(encoded: String): Result<ByteArray, SolanaTransactionError> = try {
    Result.success(Base64.decode(encoded))
} catch (e: IllegalArgumentException) {
    Result.failure(SolanaTransactionError.MalformedBytes(e.message ?: "Malformed base64 payload", e))
}
