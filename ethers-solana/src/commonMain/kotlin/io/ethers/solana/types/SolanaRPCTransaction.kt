@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.signatureError
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import io.ethers.core.json.JsonElement as RawJson

/** A getTransaction response in json encoding; not a signable transaction. */
@KeepGeneratedSerializer
@Serializable(with = SolanaRPCTransactionSerializer::class)
data class SolanaRPCTransaction(
    val slot: BigInteger,
    val blockTime: Long?,
    val transaction: SolanaRPCTransactionData,
    val meta: SolanaRPCTransactionMeta?,
    @SerialName("version") val type: SolanaTxType = SolanaTxType.Legacy,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
) {
    /** Rebuild the signable message, discarding the signatures this response carries. */
    fun toUnsignedTransaction(): Result<SolanaTransactionUnsigned, SolanaTransactionError> = transaction.message.toTransaction(type)

    /** Rebuild the full transaction, verifying the signatures this response carries against it. */
    fun toSignedTransaction(): Result<SolanaTransactionSigned, SolanaTransactionError> {
        val tx = toUnsignedTransaction().unwrapOrReturn { return Result.failure(it) }
        val signatures = transaction.signatures
        signatureError(tx, signatures)?.let { return Result.failure(it) }
        return Result.success(SolanaTransactionSigned(tx, signatures))
    }
}

object SolanaRPCTransactionSerializer : ExtensibleJsonSerializer<SolanaRPCTransaction>(SolanaRPCTransaction.generatedSerializer(), { it.otherFields })
