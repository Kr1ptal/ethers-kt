@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.Base64TupleBytesSerializer
import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import io.ethers.core.json.JsonElement as RawJson

/** Program return bytes, shared by metadata and simulation. */
@KeepGeneratedSerializer
@Serializable(with = ReturnDataSerializer::class)
data class ReturnData(
    val programId: SolanaAddress,
    @Serializable(with = Base64TupleBytesSerializer::class) val data: SolanaBytes,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object ReturnDataSerializer : ExtensibleJsonSerializer<ReturnData>(ReturnData.generatedSerializer(), { it.otherFields })
