@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.MessageHeader
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Compiled message in the provider's requested json encoding. */
@KeepGeneratedSerializer
@Serializable(with = RPCMessageSerializer::class)
data class RPCMessage(
    val header: MessageHeader,
    val accountKeys: List<SolanaAddress>,
    val recentBlockhash: Blockhash,
    val instructions: List<RPCInstruction>,
    val addressTableLookups: List<CompiledAddressLookupTable> = emptyList(),
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object RPCMessageSerializer : ExtensibleJsonSerializer<RPCMessage>(RPCMessage.generatedSerializer(), { it.otherFields })
