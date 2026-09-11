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
import io.ethers.core.json.JsonElement as RawJson

/** A node taking part in the cluster. Only [pubkey] is always present; a node may expose no ports at all. */
@KeepGeneratedSerializer
@Serializable(with = ClusterNodeSerializer::class)
data class ClusterNode(
    val pubkey: SolanaAddress,
    val gossip: String? = null,
    val rpc: String? = null,
    val pubsub: String? = null,
    val tpu: String? = null,
    val tpuQuic: String? = null,
    val tpuForwards: String? = null,
    val tpuForwardsQuic: String? = null,
    val tpuVote: String? = null,
    val tvu: String? = null,
    val serveRepair: String? = null,
    val version: String? = null,
    val featureSet: Long? = null,
    val shredVersion: Int? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object ClusterNodeSerializer : ExtensibleJsonSerializer<ClusterNode>(ClusterNode.generatedSerializer(), { it.otherFields })

/** Vote accounts split by whether they are voting currently or lagging behind the cluster. */
@KeepGeneratedSerializer
@Serializable(with = VoteAccountsSerializer::class)
data class VoteAccounts(
    val current: List<VoteAccount>,
    val delinquent: List<VoteAccount>,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object VoteAccountsSerializer : ExtensibleJsonSerializer<VoteAccounts>(VoteAccounts.generatedSerializer(), { it.otherFields })

/** [epochCredits] is `[epoch, credits, previousCredits]` per epoch, most recent last. */
@KeepGeneratedSerializer
@Serializable(with = VoteAccountSerializer::class)
data class VoteAccount(
    val votePubkey: SolanaAddress,
    val nodePubkey: SolanaAddress,
    val activatedStake: BigInteger,
    val epochVoteAccount: Boolean,
    @Serializable(with = U8Serializer::class) val commission: Int,
    val lastVote: BigInteger,
    val rootSlot: BigInteger,
    val epochCredits: List<List<BigInteger>> = emptyList(),
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object VoteAccountSerializer : ExtensibleJsonSerializer<VoteAccount>(VoteAccount.generatedSerializer(), { it.otherFields })

/** How many slots a validator was assigned and how many it produced, as `[assigned, produced]`. */
@KeepGeneratedSerializer
@Serializable(with = BlockProductionSerializer::class)
data class BlockProduction(
    val byIdentity: Map<String, List<Long>>,
    val range: SlotRange,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object BlockProductionSerializer : ExtensibleJsonSerializer<BlockProduction>(BlockProduction.generatedSerializer(), { it.otherFields })

@Serializable
data class SlotRange(val firstSlot: BigInteger, val lastSlot: BigInteger? = null)

@Serializable
data class EpochSchedule(
    val slotsPerEpoch: BigInteger,
    val leaderScheduleSlotOffset: BigInteger,
    val warmup: Boolean,
    val firstNormalEpoch: BigInteger,
    val firstNormalSlot: BigInteger,
)

/** Highest slots for which the node has snapshots, which is what a new node would bootstrap from. */
@Serializable
data class SnapshotSlot(val full: BigInteger, val incremental: BigInteger? = null)

/** Throughput over one sample window, which is how observed TPS is derived. */
@KeepGeneratedSerializer
@Serializable(with = PerformanceSampleSerializer::class)
data class PerformanceSample(
    val slot: BigInteger,
    val numTransactions: BigInteger,
    val numSlots: BigInteger,
    val samplePeriodSecs: Int,
    val numNonVoteTransactions: BigInteger? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object PerformanceSampleSerializer : ExtensibleJsonSerializer<PerformanceSample>(PerformanceSample.generatedSerializer(), { it.otherFields })

/** Stake that voted on a block, indexed by lockout depth, against the cluster's total. */
@Serializable
data class BlockCommitment(val commitment: List<BigInteger>? = null, val totalStake: BigInteger)
