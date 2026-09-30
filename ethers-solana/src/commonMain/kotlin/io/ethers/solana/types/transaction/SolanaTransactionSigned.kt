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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SolanaTransactionSigned

        if (tx != other.tx) return false
        if (signatures != other.signatures) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tx.hashCode()
        result = 31 * result + signatures.hashCode()
        return result
    }

    override fun toString(): String {
        return "SolanaTransactionSigned(tx=$tx, signatures=$signatures)"
    }

    /**
     * Mutable signature collector bound to an unsigned payload. Not thread-safe.
     *
     * Slots are filled as given and verified only by [build], so until then the collector holds
     * unverified signatures.
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

        /** The bytes this builder signs and verifies against, encoded once and shared with [tx]. */
        private val message: ByteArray = tx.messageBytes()

        init {
            require(this.signatures.size == tx.header.requiredSignatures) {
                "Collector holds ${this.signatures.size} slots, but the message requires ${tx.header.requiredSignatures}"
            }
        }

        /** Fill [signer]'s slot. The signature is verified by [build], not here. */
        fun addSignature(signer: SolanaAddress, signature: SolanaSignature): Builder = apply {
            val index = tx.signers.indexOf(signer)
            require(index >= 0) { "Address is not a required signer" }
            signatures[index] = signature
        }

        /**
         * Sign with each of [signers], atomically: an unknown address, or a signer that fails to
         * produce a signature, leaves the builder's previous signatures intact.
         */
        fun sign(vararg signers: SolanaSigner): Builder = apply {
            val requiredSigners = tx.signers
            val indices = signers.map { signer ->
                requiredSigners.indexOf(signer.publicKey)
                    .also { require(it >= 0) { "Address is not a required signer" } }
            }
            val collected = Array(signers.size) { signers[it].signMessage(message) }
            collected.forEachIndexed { i, signature -> signatures[indices[i]] = signature }
        }

        /** Reset every slot to empty, keeping one slot per required signer. */
        fun clearSignatures(): Builder = apply {
            this@Builder.signatures.indices.forEach { this@Builder.signatures[it] = null }
        }

        /**
         * The signed transaction, once every slot is filled with a signature that verifies. Slots that
         * are not are reported together as [SolanaTransactionError.UnsignedSlots].
         */
        fun build(): Result<SolanaTransactionSigned, SolanaTransactionError> {
            // signers lead the account list, so index it rather than slicing out a copy
            val signers = tx.accounts
            var missing: MutableList<Int>? = null
            var invalid: MutableList<Int>? = null
            signatures.forEachIndexed { index, signature ->
                when {
                    signature == null -> (missing ?: mutableListOf<Int>().also { missing = it }).add(index)
                    !signers[index].verify(signature, message) -> (invalid ?: mutableListOf<Int>().also { invalid = it }).add(index)
                }
            }
            if (missing != null || invalid != null) {
                return Result.failure(SolanaTransactionError.UnsignedSlots(missing ?: emptyList(), invalid ?: emptyList()))
            }
            return Result.success(SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) }, validated = true))
        }

        /**
         * Full envelope with zeros for missing signatures, for offline exchange or simulation only.
         * Its populated slots are unverified.
         */
        fun serializePartial(): ByteArray = tx.encodeEnvelope(signatures)
        fun toBase64Partial(): String = Base64.encode(serializePartial())

        companion object {
            /** Import an envelope, retaining its empty slots for further signing. */
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
            return Result.success(SolanaTransactionSigned(tx, signatures.filled(), validated = true))
        }

        /** As [deserialize], from a base64 envelope. */
        @JvmStatic
        fun fromBase64(encoded: String): Result<SolanaTransactionSigned, SolanaTransactionError> = decodeBase64(encoded).andThen { deserialize(it) }
    }
}
