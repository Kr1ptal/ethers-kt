@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.transaction.LoadedAddresses
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import io.ethers.core.json.JsonElement as RawJson

/** Missing optional recording data remains null; required fee and balance fields never default. */
@KeepGeneratedSerializer
@Serializable(with = SolanaRPCTransactionMetaSerializer::class)
data class SolanaRPCTransactionMeta(
    val err: TransactionError?,
    val fee: BigInteger,
    val preBalances: List<BigInteger>,
    val postBalances: List<BigInteger>,
    val innerInstructions: List<InnerInstructions>? = null,
    val logMessages: List<String>? = null,
    val preTokenBalances: List<TokenBalance>? = null,
    val postTokenBalances: List<TokenBalance>? = null,
    val rewards: List<Reward>? = null,
    val loadedAddresses: LoadedAddresses? = null,
    val returnData: ReturnData? = null,
    val computeUnitsConsumed: BigInteger? = null,
    val costUnits: BigInteger? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
) {
    val isSuccess: Boolean get() = err == null
}

object SolanaRPCTransactionMetaSerializer : ExtensibleJsonSerializer<SolanaRPCTransactionMeta>(SolanaRPCTransactionMeta.generatedSerializer(), { it.otherFields })
