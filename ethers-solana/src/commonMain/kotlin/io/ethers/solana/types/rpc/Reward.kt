@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.U8Serializer
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import io.ethers.core.json.JsonElement as RawJson

/** An account reward; lamports is a signed delta. */
@KeepGeneratedSerializer
@Serializable(with = RewardSerializer::class)
data class Reward(
    val pubkey: SolanaAddress,
    val lamports: Long,
    val postBalance: BigInteger,
    val rewardType: RewardType? = null,
    @Serializable(with = U8Serializer::class) val commission: Int? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object RewardSerializer : ExtensibleJsonSerializer<Reward>(Reward.generatedSerializer(), { it.otherFields })
