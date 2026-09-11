@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.U8Serializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A token balance associated with a transaction account. */
@KeepGeneratedSerializer
@Serializable(with = TokenBalanceSerializer::class)
data class TokenBalance(
    @Serializable(with = U8Serializer::class) val accountIndex: Int,
    val mint: SolanaAddress,
    val uiTokenAmount: TokenAmount,
    val owner: SolanaAddress? = null,
    val programId: SolanaAddress? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object TokenBalanceSerializer : ExtensibleJsonSerializer<TokenBalance>(TokenBalance.generatedSerializer(), { it.otherFields })
