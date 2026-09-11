@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import io.ethers.core.json.JsonElement as RawJson

/**
 * How far a submitted transaction has progressed, as getSignatureStatuses reports it.
 *
 * A null entry in the response means the node has never seen the signature, which is different from
 * a status whose [err] is set: the first may still land, the second already failed on chain.
 */
@KeepGeneratedSerializer
@Serializable(with = SignatureStatusSerializer::class)
data class SignatureStatus(
    val slot: BigInteger,
    /** Blocks built on top of this one, or null once the transaction is finalized. */
    val confirmations: Int? = null,
    /** Why the transaction failed on chain, or null if it succeeded. */
    val err: TransactionError? = null,
    val confirmationStatus: Commitment? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
) {
    val isSuccess: Boolean get() = err == null

    /** True once the transaction is at or beyond [commitment], so it will not be rolled back below it. */
    fun isAtLeast(commitment: Commitment): Boolean {
        val reached = confirmationStatus ?: return false
        return when (commitment) {
            Commitment.PROCESSED -> true
            Commitment.CONFIRMED -> reached != Commitment.PROCESSED
            Commitment.FINALIZED -> reached == Commitment.FINALIZED
        }
    }
}

object SignatureStatusSerializer : ExtensibleJsonSerializer<SignatureStatus>(SignatureStatus.generatedSerializer(), { it.otherFields })
