@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.core.types.Bytes
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Compiled instruction with optional RPC execution details. */
@KeepGeneratedSerializer
@Serializable(with = RPCInstructionSerializer::class)
data class RPCInstruction(
    @Serializable(with = U8Serializer::class) val programIdIndex: Int,
    @Serializable(with = U8ListSerializer::class) val accounts: List<Int>,
    @Serializable(with = Base58BytesSerializer::class) val data: Bytes,
    @Serializable(with = U32Serializer::class) val stackHeight: Long? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object RPCInstructionSerializer : ExtensibleJsonSerializer<RPCInstruction>(RPCInstruction.generatedSerializer(), { it.otherFields })
