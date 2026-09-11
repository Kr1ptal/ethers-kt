@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.U8Serializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Inner instructions shared by metadata and simulation. */
@KeepGeneratedSerializer
@Serializable(with = InnerInstructionsSerializer::class)
data class InnerInstructions(
    @Serializable(with = U8Serializer::class) val index: Int,
    val instructions: List<SolanaRPCInstruction>,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object InnerInstructionsSerializer : ExtensibleJsonSerializer<InnerInstructions>(InnerInstructions.generatedSerializer(), { it.otherFields })
