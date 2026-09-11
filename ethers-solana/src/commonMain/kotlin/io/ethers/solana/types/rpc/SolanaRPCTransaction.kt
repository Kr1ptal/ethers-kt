@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.ComputeBudgetValues
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.decodeComputeBudget
import io.ethers.solana.types.transaction.decompile
import io.ethers.solana.types.transaction.resolveAccountMetas
import io.ethers.solana.types.transaction.signatureError
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A getTransaction response in json encoding; not a signable transaction. */
@KeepGeneratedSerializer
@Serializable(with = SolanaRPCTransactionSerializer::class)
data class SolanaRPCTransaction(
    val slot: BigInteger,
    val blockTime: Long?,
    val transaction: SolanaRPCTransactionData,
    val meta: SolanaRPCTransactionMeta?,
    @SerialName("version") override val type: SolanaTxType = SolanaTxType.Legacy,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
) : SolanaTransaction {
    override val header: MessageHeader get() = transaction.message.header
    override val accounts: List<SolanaAddress> get() = transaction.message.accountKeys
    override val recentBlockhash: SolanaBlockhash get() = transaction.message.recentBlockhash
    override val instructions: List<CompiledInstruction> get() = transaction.message.instructions
    override val addressLookupTables: List<CompiledAddressLookupTable> get() = transaction.message.addressTableLookups

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

    /**
     * Recover the request that compiles to this transaction, preferring the addresses the node
     * already resolved over any supplied [tables].
     *
     * A lookup table's contents can change after a transaction is included, so re-reading a table
     * that has since been extended or closed would resolve a historical message to the wrong
     * addresses. The `loadedAddresses` this response carries are what the runtime actually used, so
     * when they are present this needs no tables at all.
     */
    override fun toRequest(tables: List<AddressLookupTableAccount>): Result<SolanaTransactionRequest, SolanaTransactionError> = decompile(this, tables, meta?.loadedAddresses)

    /** As [toRequest], the addresses the node resolved take precedence over the supplied [tables]. */
    override fun resolveAccounts(tables: List<AddressLookupTableAccount>): Result<List<AccountMeta>, SolanaTransactionError> = resolveAccountMetas(this, tables, meta?.loadedAddresses)

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
