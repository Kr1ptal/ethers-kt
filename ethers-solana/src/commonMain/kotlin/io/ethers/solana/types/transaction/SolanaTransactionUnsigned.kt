package io.ethers.solana.types.transaction

import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.SolanaBlockhash
import kotlin.jvm.JvmStatic

/** A compiled legacy, v0 or v1 payload with no signatures. */
sealed interface SolanaTransactionUnsigned : SolanaTransaction {
    /** Sign with every required signer, failing if any signature is missing or invalid. */
    fun sign(vararg signers: SolanaSigner): SolanaTransactionSigned = signingBuilder().sign(*signers).build()

    /** Rebuild the payload against a fresh blockhash. */
    fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTransactionUnsigned

    /** Start collecting signatures without requiring all signers to be available. */
    fun signingBuilder(): SolanaTransactionSigned.Builder = SolanaTransactionSigned.Builder(this)

    override fun serializeForSimulation(): ByteArray = encodeTransactionEnvelope(this, List(header.requiredSignatures) { null })

    companion object {
        /** Decode message bytes, not a transaction envelope containing signatures. */
        @JvmStatic
        fun deserializeMessage(bytes: ByteArray): SolanaTransactionUnsigned = decodeMessage(bytes)
    }
}
