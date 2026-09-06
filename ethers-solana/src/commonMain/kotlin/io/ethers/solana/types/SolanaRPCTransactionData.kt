@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import io.ethers.core.json.JsonElement as RawJson

/** Signatures and compiled message, including unsupported transaction versions. */
@KeepGeneratedSerializer
@Serializable(with = SolanaRPCTransactionDataSerializer::class)
data class SolanaRPCTransactionData(
    val signatures: List<SolanaSignature>,
    val message: SolanaRPCMessage,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object SolanaRPCTransactionDataSerializer : ExtensibleJsonSerializer<SolanaRPCTransactionData>(SolanaRPCTransactionData.generatedSerializer(), { it.otherFields })
