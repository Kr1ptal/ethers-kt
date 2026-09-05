@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.solana.types.transaction.SolanaTxType
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A getTransaction response in json encoding; not a signable transaction. */
@KeepGeneratedSerializer
@Serializable(with = RPCTransactionSerializer::class)
data class RPCTransaction(
    val slot: BigInteger,
    val blockTime: Long?,
    val transaction: RPCTransactionData,
    val meta: RPCTransactionMeta?,
    @SerialName("version") val type: SolanaTxType = SolanaTxType.Legacy,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object RPCTransactionSerializer : ExtensibleJsonSerializer<RPCTransaction>(RPCTransaction.generatedSerializer(), { it.otherFields })
