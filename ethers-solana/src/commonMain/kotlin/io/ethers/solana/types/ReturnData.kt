@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.core.types.Bytes
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Program return bytes, shared by metadata and simulation. */
@KeepGeneratedSerializer
@Serializable(with = ReturnDataSerializer::class)
data class ReturnData(
    val programId: SolanaAddress,
    @Serializable(with = Base64BytesSerializer::class) val data: Bytes,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object ReturnDataSerializer : ExtensibleJsonSerializer<ReturnData>(ReturnData.generatedSerializer(), { it.otherFields })
