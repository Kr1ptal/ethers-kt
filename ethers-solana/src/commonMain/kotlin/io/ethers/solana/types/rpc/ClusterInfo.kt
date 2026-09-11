@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class LatestBlockhash(val blockhash: SolanaBlockhash, val lastValidBlockHeight: BigInteger)
@Serializable data class EpochInfo(val absoluteSlot: BigInteger, val blockHeight: BigInteger, val epoch: BigInteger, val slotIndex: BigInteger, val slotsInEpoch: BigInteger, val transactionCount: BigInteger? = null)
@Serializable data class SolanaNodeIdentity(val identity: SolanaAddress)
@Serializable data class SolanaNodeVersion(@SerialName("solana-core") val solanaCore: String, @SerialName("feature-set") val featureSet: BigInteger? = null)
enum class SolanaNodeHealth { OK, ERROR }

/** One slot's observed prioritization fee, as getRecentPrioritizationFees samples them. */
@Serializable data class PrioritizationFee(val slot: BigInteger, val prioritizationFee: BigInteger)
