@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.transaction.SolanaTxType
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A confirmed block.
 *
 * Which fields the node fills depends on the detail level asked for: [transactions] is populated for
 * full detail, [signatures] for signature detail, and neither for none. [blockHeight] is null for
 * blocks produced before the field existed.
 */
@KeepGeneratedSerializer
@Serializable(with = SolanaBlockSerializer::class)
data class SolanaBlock(
    val blockhash: SolanaBlockhash,
    val previousBlockhash: SolanaBlockhash,
    val parentSlot: BigInteger,
    val transactions: List<SolanaBlockTransaction> = emptyList(),
    val signatures: List<SolanaSignature> = emptyList(),
    val rewards: List<Reward>? = null,
    val blockTime: Long? = null,
    val blockHeight: BigInteger? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object SolanaBlockSerializer : ExtensibleJsonSerializer<SolanaBlock>(SolanaBlock.generatedSerializer(), { it.otherFields })

/** One transaction of a block, which carries no slot of its own since the block supplies it. */
@KeepGeneratedSerializer
@Serializable(with = SolanaBlockTransactionSerializer::class)
data class SolanaBlockTransaction(
    val transaction: SolanaRPCTransactionData,
    val meta: SolanaRPCTransactionMeta? = null,
    @SerialName("version") val type: SolanaTxType = SolanaTxType.Legacy,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object SolanaBlockTransactionSerializer : ExtensibleJsonSerializer<SolanaBlockTransaction>(SolanaBlockTransaction.generatedSerializer(), { it.otherFields })

/** How much of each transaction getBlock should return. */
enum class BlockTransactionDetails {
    FULL,
    SIGNATURES,
    NONE,
    ;

    override fun toString(): String = name.lowercase()
}
