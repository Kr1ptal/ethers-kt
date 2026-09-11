@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.U8Serializer
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Circulating and total supply in lamports. [nonCirculatingAccounts] is empty unless asked for. */
@KeepGeneratedSerializer
@Serializable(with = SupplySerializer::class)
data class Supply(
    val total: BigInteger,
    val circulating: BigInteger,
    val nonCirculating: BigInteger,
    val nonCirculatingAccounts: List<SolanaAddress> = emptyList(),
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object SupplySerializer : ExtensibleJsonSerializer<Supply>(Supply.generatedSerializer(), { it.otherFields })

/** The inflation schedule's parameters, which change only by governance. */
@Serializable
data class InflationGovernor(
    val initial: Double,
    val terminal: Double,
    val taper: Double,
    val foundation: Double,
    val foundationTerm: Double,
)

/** Inflation actually in force for [epoch], split by who receives it. */
@Serializable
data class InflationRate(
    val total: Double,
    val validator: Double,
    val foundation: Double,
    val epoch: BigInteger,
)

/** What one account was paid for staking in an epoch. */
@KeepGeneratedSerializer
@Serializable(with = InflationRewardSerializer::class)
data class InflationReward(
    val epoch: BigInteger,
    val effectiveSlot: BigInteger,
    val amount: BigInteger,
    val postBalance: BigInteger,
    @Serializable(with = U8Serializer::class) val commission: Int? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object InflationRewardSerializer : ExtensibleJsonSerializer<InflationReward>(InflationReward.generatedSerializer(), { it.otherFields })

/** One of the largest accounts on the cluster, by lamports. */
@Serializable
data class LargestAccount(val address: SolanaAddress, val lamports: BigInteger)

/** One of the largest holders of a token, with the balance rendered as the mint states it. */
@KeepGeneratedSerializer
@Serializable(with = LargestTokenAccountSerializer::class)
data class LargestTokenAccount(
    val address: SolanaAddress,
    @Serializable(with = TokenQuantitySerializer::class) val amount: BigInteger,
    @Serializable(with = U8Serializer::class) val decimals: Int,
    val uiAmountString: String,
    @Serializable(with = DecimalSerializer::class) val uiAmount: BigDecimal? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object LargestTokenAccountSerializer : ExtensibleJsonSerializer<LargestTokenAccount>(LargestTokenAccount.generatedSerializer(), { it.otherFields })

/** Which accounts to leave out of [Supply.nonCirculatingAccounts]. */
enum class LargestAccountsFilter {
    CIRCULATING,
    NON_CIRCULATING,
    ;

    override fun toString(): String = if (this == CIRCULATING) "circulating" else "nonCirculating"
}
