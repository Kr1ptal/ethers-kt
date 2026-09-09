@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.ComputeBudgetValues
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.decodeComputeBudget
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
    @SerialName("version") override val type: SolanaTxType = SolanaTxType.Legacy,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
) : SolanaTransaction {
    override val header: MessageHeader get() = transaction.message.header
    override val accounts: List<SolanaAddress> get() = transaction.message.accountKeys
    override val recentBlockhash: SolanaBlockhash get() = transaction.message.recentBlockhash
    override val instructions: List<CompiledInstruction> get() = transaction.message.instructions

    /**
     * Decoded once, and only for the versions whose encoding is known.
     *
     * A version this library cannot construct reports nothing rather than guessing: its instructions
     * may not mean what they would in a legacy message, and its inline config, if it has one, is not
     * this config.
     */
    private val computeBudget: ComputeBudgetValues by lazy {
        when (type) {
            SolanaTxType.Legacy, SolanaTxType.V0 -> decodeComputeBudget(accounts, instructions)
            SolanaTxType.V1 ->
                transaction.message.transactionConfig
                    ?.let { ComputeBudgetValues(it.computeUnitLimit, null, it.loadedAccountsDataSizeLimit, it.heapSize) }
                    ?: ComputeBudgetValues.NONE
            is SolanaTxType.Unsupported -> ComputeBudgetValues.NONE
        }
    }

    override val computeUnitLimit: Long? get() = computeBudget.computeUnitLimit
    override val computeUnitPrice: BigInteger? get() = computeBudget.computeUnitPrice
    override val priorityFee: BigInteger?
        get() = when (type) {
            // v1 states the total outright, where legacy and v0 only imply it
            SolanaTxType.V1 -> transaction.message.transactionConfig?.priorityFee
            else -> computeBudget.priorityFee
        }
    override val loadedAccountsDataSizeLimit: Long? get() = computeBudget.loadedAccountsDataSizeLimit
    override val heapSize: Long? get() = computeBudget.heapSize

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
