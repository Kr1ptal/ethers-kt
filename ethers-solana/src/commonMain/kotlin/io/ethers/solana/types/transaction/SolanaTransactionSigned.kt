package io.ethers.solana.types.transaction

import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.Signature
import io.ethers.solana.types.SolanaAddress
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** A transaction with a verified signature for every required signer, in message account order. */
class SolanaTransactionSigned(val tx: SolanaTransactionUnsigned, signatures: List<Signature>) : SolanaTransaction by tx {
    private val signatureSlots = signatures.toList()
    val signatures: List<Signature> get() = signatureSlots.toList()

    /** Solana's transaction id is the fee payer's signature, not a hash of the envelope. */
    val id: Signature get() = signatureSlots.first()

    init {
        validateSignatures(tx, signatureSlots)
    }

    fun serialize(): ByteArray = encodeTransactionEnvelope(tx, signatureSlots)
    fun toBase64(): String = Base64.encode(serialize())
    override fun serializeForSimulation(): ByteArray = serialize()

    /**
     * Mutable signature collector bound to an immutable unsigned payload. Not thread-safe.
     * Every supplied signature is verified; [build] additionally requires all signer slots to be filled.
     * Built transactions and exported signature lists are independent snapshots.
     */
    class Builder @JvmOverloads constructor(
        val tx: SolanaTransactionUnsigned,
        signatures: List<Signature?> = List(tx.header.requiredSignatures) { null },
    ) {
        private var signatureSlots = signatures.toList()
        val signatures: List<Signature?> get() = signatureSlots.toList()
        val isFullySigned: Boolean get() = signatureSlots.all { it != null }
        val missingSigners: List<SolanaAddress> get() = tx.signers.filterIndexed { index, _ -> signatureSlots[index] == null }

        init {
            validateSignatures(tx, signatureSlots)
        }

        fun addSignature(signer: SolanaAddress, signature: Signature): Builder = apply {
            val index = tx.signers.indexOf(signer)
            require(index >= 0) { "Address is not a required signer" }
            val updated = signatureSlots.toMutableList().also { it[index] = signature }
            validateSignatures(tx, updated)
            signatureSlots = updated
        }

        /** Collect signatures atomically: a failure leaves the builder's previous signatures intact. */
        fun sign(vararg signers: SolanaSigner): Builder = apply {
            val requiredSigners = tx.signers
            val indices = signers.map { signer ->
                requiredSigners.indexOf(signer.publicKey).also { require(it >= 0) { "Address is not a required signer" } }
            }
            val message = tx.serializeMessage()
            val updated = signatureSlots.toMutableList()
            signers.forEachIndexed { i, signer -> updated[indices[i]] = signer.signMessage(message.copyOf()) }
            validateSignatures(tx, updated)
            signatureSlots = updated
        }

        fun clearSignatures(): Builder = apply { signatureSlots = List(tx.header.requiredSignatures) { null } }

        fun build(): SolanaTransactionSigned = SolanaTransactionSigned(tx, signatureSlots.map { requireNotNull(it) { "Missing required signatures" } })

        /** Full envelope with zeros for missing signatures, for offline exchange or simulation only. */
        fun serializePartial(): ByteArray = encodeTransactionEnvelope(tx, signatureSlots)
        fun toBase64Partial(): String = Base64.encode(serializePartial())

        companion object {
            /** Import an envelope, verifying populated slots and retaining missing ones for further signing. */
            @JvmStatic
            fun deserializePartial(bytes: ByteArray): Builder {
                val (tx, signatures) = decodeTransactionEnvelope(bytes)
                return Builder(tx, signatures)
            }

            @JvmStatic
            fun fromBase64Partial(encoded: String): Builder = deserializePartial(Base64.decode(encoded))
        }
    }

    companion object {
        /** Reject incomplete envelopes as well as invalid signatures or malformed messages. */
        @JvmStatic
        fun deserialize(bytes: ByteArray): SolanaTransactionSigned {
            val (tx, signatures) = decodeTransactionEnvelope(bytes)
            return SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) { "Missing required signatures" } })
        }

        @JvmStatic
        fun fromBase64(encoded: String): SolanaTransactionSigned = deserialize(Base64.decode(encoded))
    }
}
