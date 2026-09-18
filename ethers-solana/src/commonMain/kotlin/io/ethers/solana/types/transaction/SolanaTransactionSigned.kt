package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A transaction with a verified signature for every required signer, in message account order.
 *
 * [signatures] is kept as given rather than copied, so pass an immutable list.
 */
class SolanaTransactionSigned private constructor(
    val tx: SolanaTransactionUnsigned,
    val signatures: List<SolanaSignature>,
    validated: Boolean,
) : SolanaTransactionCompiled by tx {

    /**
     * Verify every signature against the message, throwing [SolanaTransactionException] if one does
     * not hold. [signatures] is kept as given rather than copied, so pass an immutable list.
     */
    constructor(tx: SolanaTransactionUnsigned, signatures: List<SolanaSignature>) : this(tx, signatures, false)

    /** Solana's transaction id is the fee payer's signature, not a hash of the envelope. */
    val id: SolanaSignature get() = signatures.first()

    init {
        if (!validated) validateSignatures(tx, signatures)
    }

    /** Changing the blockhash discards every signature, returning an unsigned transaction. */
    fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTransactionUnsigned = tx.withNewBlockhash(blockhash)

    fun serialize(): ByteArray = tx.encodeEnvelope(signatures)
    fun toBase64(): String = Base64.encode(serialize())
    override fun serializeForSimulation(): ByteArray = serialize()

    /**
     * Mutable signature collector bound to an unsigned payload. Not thread-safe.
     * Every supplied signature is verified; [build] additionally requires all signer slots to be filled.
     *
     * [signatures] is the collector's own list, not a snapshot: it reflects later [sign] and
     * [addSignature] calls. Take a copy if you need a stable view. [build] does produce an
     * independent transaction.
     */
    class Builder @JvmOverloads constructor(
        val tx: SolanaTransactionUnsigned,
        signatures: List<SolanaSignature?> = List(tx.header.requiredSignatures) { null },
    ) {
        val signatures: List<SolanaSignature?>
            field = signatures.toMutableList()

        val isFullySigned: Boolean get() = signatures.all { it != null }
        val missingSigners: List<SolanaAddress> get() = tx.signers.filterIndexed { index, _ -> signatures[index] == null }

        /** Encoded once here: every signature this builder collects is checked against these bytes. */
        private val message: ByteArray = tx.serializeMessage()

        init {
            validateSignatures(tx, this.signatures, message)
        }

        /** A rejected signature leaves the builder's previous signatures intact. */
        fun addSignature(signer: SolanaAddress, signature: SolanaSignature): Builder = apply {
            val index = tx.signers.indexOf(signer)
            require(index >= 0) { "Address is not a required signer" }
            if (!signer.verify(signature, message)) {
                throw SolanaTransactionError.InvalidSignature(index, signer).toException()
            }
            signatures[index] = signature
        }

        /**
         * Collect signatures atomically: a failure leaves the builder's previous signatures intact.
         * Each signature is verified as it is produced, so nothing is written until all of them hold.
         */
        fun sign(vararg signers: SolanaSigner): Builder = apply {
            val requiredSigners = tx.signers
            val indices = signers.map { signer ->
                requiredSigners.indexOf(signer.publicKey)
                    .also { require(it >= 0) { "Address is not a required signer" } }
            }
            val collected = arrayOfNulls<SolanaSignature>(signers.size)
            signers.forEachIndexed { i, signer ->
                val signature = signer.signMessage(message)
                val signerAddress = requiredSigners[indices[i]]
                if (!signerAddress.verify(signature, message)) {
                    throw SolanaTransactionError.InvalidSignature(indices[i], signerAddress).toException()
                }
                collected[i] = signature
            }
            collected.forEachIndexed { i, signature -> signatures[indices[i]] = signature }
        }

        /** Reset every slot to empty, keeping one slot per required signer. */
        fun clearSignatures(): Builder = apply {
            this@Builder.signatures.indices.forEach { this@Builder.signatures[it] = null }
        }

        /**
         * Every signature was verified as it was collected, so this does not check them again -
         * repeating N Ed25519 verifications is what the builder exists to avoid. An unfilled slot is
         * reported as [SolanaTransactionError.PartiallySigned], naming how many are still missing.
         */
        fun build(): Result<SolanaTransactionSigned, SolanaTransactionError> {
            val missing = signatures.count { it == null }
            if (missing > 0) return Result.failure(SolanaTransactionError.PartiallySigned(missing, signatures.size))
            return Result.success(SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) }, validated = true))
        }

        /** Full envelope with zeros for missing signatures, for offline exchange or simulation only. */
        fun serializePartial(): ByteArray = tx.encodeEnvelope(signatures)
        fun toBase64Partial(): String = Base64.encode(serializePartial())

        companion object {
            /** Import an envelope, verifying populated slots and retaining missing ones for further signing. */
            @JvmStatic
            fun deserializePartial(bytes: ByteArray): Result<Builder, SolanaTransactionError> = decodeTransactionEnvelope(bytes).map { (tx, signatures) -> Builder(tx, signatures) }

            /** As [deserializePartial], from a base64 envelope. */
            @JvmStatic
            fun fromBase64Partial(encoded: String): Result<Builder, SolanaTransactionError> = decodeBase64(encoded).andThen { deserializePartial(it) }
        }
    }

    companion object {
        /** Reject incomplete envelopes as well as invalid signatures or malformed messages. */
        @JvmStatic
        fun deserialize(bytes: ByteArray): Result<SolanaTransactionSigned, SolanaTransactionError> {
            val (tx, signatures) = decodeTransactionEnvelope(bytes).unwrapOrReturn { return Result.failure(it) }
            val missing = signatures.count { it == null }
            if (missing > 0) return Result.failure(SolanaTransactionError.PartiallySigned(missing, signatures.size))
            // checked as a value, so the validating constructor below can never be the one to report it
            signatureError(tx, signatures)?.let { return Result.failure(it) }
            return Result.success(SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) }))
        }

        /** As [deserialize], from a base64 envelope. */
        @JvmStatic
        fun fromBase64(encoded: String): Result<SolanaTransactionSigned, SolanaTransactionError> = decodeBase64(encoded).andThen { deserialize(it) }
    }
}
