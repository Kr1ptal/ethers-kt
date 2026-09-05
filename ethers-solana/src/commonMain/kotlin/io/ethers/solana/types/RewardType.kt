package io.ethers.solana.types

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmField

/** String-backed to retain reward kinds introduced by newer validators. */
@Serializable(with = RewardTypeSerializer::class)
data class RewardType(val value: String) {
    override fun toString(): String = value
    companion object {
        @JvmField val FEE = RewardType("fee")
        @JvmField val RENT = RewardType("rent")
        @JvmField val STAKING = RewardType("staking")
        @JvmField val VOTING = RewardType("voting")
    }
}

object RewardTypeSerializer : KSerializer<RewardType> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaRewardType", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): RewardType = RewardType(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: RewardType) = encoder.encodeString(value.value)
}
