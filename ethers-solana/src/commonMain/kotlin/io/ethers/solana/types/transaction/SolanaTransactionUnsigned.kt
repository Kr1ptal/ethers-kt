package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import kotlin.jvm.JvmStatic

/** A compiled legacy, v0 or v1 payload with no signatures. */
sealed interface SolanaTransactionUnsigned : SolanaTransaction {
    /** Sign with every required signer, failing if any signature is missing or invalid. */
    fun sign(vararg signers: SolanaSigner): SolanaTransactionSigned = signingBuilder().sign(*signers).build()

    /** Rebuild the payload against a fresh blockhash. */
    fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTransactionUnsigned

    /** Start collecting signatures without requiring all signers to be available. */
    fun signingBuilder(): SolanaTransactionSigned.Builder = SolanaTransactionSigned.Builder(this)

    /**
     * Exact size of [encodeEnvelope], counting a slot for every required signature. Must stay in
     * step with this type's [encodeEnvelope] and [serializeMessage].
     */
    fun envelopeSize(): Long

    /**
     * Encode the full envelope for the given signature slots, zero-filling the missing ones.
     *
     * This is the per-version encoding primitive the `serialize` functions are built on: legacy and
     * v0 place the signature vector before the message, v1 after it. Prefer
     * [SolanaTransactionSigned.serialize], [SolanaTransactionSigned.Builder.serializePartial] or
     * [serializeForSimulation], which supply the signatures they already hold.
     */
    fun encodeEnvelope(signatures: List<SolanaSignature?>): ByteArray

    override fun serializeForSimulation(): ByteArray = encodeEnvelope(List(header.requiredSignatures) { null })

    companion object {
        /** Decode message bytes, not a transaction envelope containing signatures. */
        @JvmStatic
        fun deserializeMessage(bytes: ByteArray): SolanaTransactionUnsigned = decodeMessage(bytes)

        /** As [deserializeMessage], returning the reason the bytes could not be decoded instead of throwing. */
        @JvmStatic
        fun tryDeserializeMessage(bytes: ByteArray): Result<SolanaTransactionUnsigned, SolanaTransactionError> = catchTransactionError { decodeMessage(bytes) }
    }
}
