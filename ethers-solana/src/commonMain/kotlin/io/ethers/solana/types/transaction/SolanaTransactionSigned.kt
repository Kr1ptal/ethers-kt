package io.ethers.solana.types.transaction

import io.ethers.core.Result
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
class SolanaTransactionSigned(val tx: SolanaTransactionUnsigned, val signatures: List<SolanaSignature>) :
    SolanaTransactionCompiled by tx {

    /** Solana's transaction id is the fee payer's signature, not a hash of the envelope. */
    val id: SolanaSignature get() = signatures.first()

    init {
        validateSignatures(tx, signatures)
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

        init {
            validateSignatures(tx, this.signatures)
        }

        /** A rejected signature leaves the builder's previous signatures intact. */
        fun addSignature(signer: SolanaAddress, signature: SolanaSignature): Builder = apply {
            val index = tx.signers.indexOf(signer)
            require(index >= 0) { "Address is not a required signer" }
            if (!signer.verify(signature, tx.serializeMessage())) {
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
            val message = tx.serializeMessage()
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

        fun build(): SolanaTransactionSigned = SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) { "Missing required signatures" } })

        /** Full envelope with zeros for missing signatures, for offline exchange or simulation only. */
        fun serializePartial(): ByteArray = tx.encodeEnvelope(signatures)
        fun toBase64Partial(): String = Base64.encode(serializePartial())

        companion object {
            /** Import an envelope, verifying populated slots and retaining missing ones for further signing. */
            @JvmStatic
            fun deserializePartial(bytes: ByteArray): Builder {
                val (tx, signatures) = decodeTransactionEnvelope(bytes)
                return Builder(tx, signatures)
            }

            /** As [deserializePartial], returning the reason the bytes could not be decoded instead of throwing. */
            @JvmStatic
            fun tryDeserializePartial(bytes: ByteArray): Result<Builder, SolanaTransactionError> = catchTransactionError { deserializePartial(bytes) }

            @JvmStatic
            fun fromBase64Partial(encoded: String): Builder = deserializePartial(Base64.decode(encoded))

            /** As [fromBase64Partial], returning the reason the input could not be decoded instead of throwing. */
            @JvmStatic
            fun tryFromBase64Partial(encoded: String): Result<Builder, SolanaTransactionError> = catchTransactionError { fromBase64Partial(encoded) }
        }
    }

    companion object {
        /** Reject incomplete envelopes as well as invalid signatures or malformed messages. */
        @JvmStatic
        fun deserialize(bytes: ByteArray): SolanaTransactionSigned {
            val (tx, signatures) = decodeTransactionEnvelope(bytes)
            return SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) { "Missing required signatures" } })
        }

        /** As [deserialize], returning the reason the bytes could not be decoded instead of throwing. */
        @JvmStatic
        fun tryDeserialize(bytes: ByteArray): Result<SolanaTransactionSigned, SolanaTransactionError> = catchTransactionError { deserialize(bytes) }

        @JvmStatic
        fun fromBase64(encoded: String): SolanaTransactionSigned = deserialize(Base64.decode(encoded))

        /** As [fromBase64], returning the reason the input could not be decoded instead of throwing. */
        @JvmStatic
        fun tryFromBase64(encoded: String): Result<SolanaTransactionSigned, SolanaTransactionError> = catchTransactionError { fromBase64(encoded) }
    }
}
