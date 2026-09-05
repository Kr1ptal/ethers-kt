@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Missing optional recording data remains null; required fee and balance fields never default. */
@KeepGeneratedSerializer
@Serializable(with = RPCTransactionMetaSerializer::class)
data class RPCTransactionMeta(
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
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
) {
    val isSuccess: Boolean get() = err == null
}

object RPCTransactionMetaSerializer : ExtensibleJsonSerializer<RPCTransactionMeta>(RPCTransactionMeta.generatedSerializer(), { it.otherFields })
