@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** An account reward; lamports is a signed delta. */
@KeepGeneratedSerializer
@Serializable(with = RewardSerializer::class)
data class Reward(
    val pubkey: SolanaAddress,
    val lamports: Long,
    val postBalance: BigInteger,
    val rewardType: RewardType? = null,
    @Serializable(with = U8Serializer::class) val commission: Int? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object RewardSerializer : ExtensibleJsonSerializer<Reward>(Reward.generatedSerializer(), { it.otherFields })
