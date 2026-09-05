@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Compiled message in the provider's requested json encoding. */
@KeepGeneratedSerializer
@Serializable(with = SolanaRPCMessageSerializer::class)
data class SolanaRPCMessage(
    val header: MessageHeader,
    val accountKeys: List<SolanaAddress>,
    val recentBlockhash: SolanaBlockhash,
    val instructions: List<SolanaRPCInstruction>,
    val addressTableLookups: List<CompiledAddressLookupTable> = emptyList(),
    /** Present for v1; absent for legacy/v0. Individual absent requests remain null. */
    val transactionConfig: SolanaTransactionConfig? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object SolanaRPCMessageSerializer : ExtensibleJsonSerializer<SolanaRPCMessage>(SolanaRPCMessage.generatedSerializer(), { it.otherFields })
