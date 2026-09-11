@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.U32Serializer
import io.ethers.solana.types.U8ListSerializer
import io.ethers.solana.types.U8Serializer
import io.ethers.solana.types.transaction.CompiledInstruction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.ethers.core.json.JsonElement as RawJson

/** Compiled instruction with optional RPC execution details. */
@KeepGeneratedSerializer
@Serializable(with = SolanaRPCInstructionSerializer::class)
data class SolanaRPCInstruction(
    @Serializable(with = U8Serializer::class) override val programIdIndex: Int,
    @Serializable(with = U8ListSerializer::class) override val accounts: List<Int>,
    @Serializable(with = Base58BytesSerializer::class) override val data: SolanaBytes,
    @Serializable(with = U32Serializer::class) val stackHeight: Long? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
) : CompiledInstruction

object SolanaRPCInstructionSerializer : ExtensibleJsonSerializer<SolanaRPCInstruction>(SolanaRPCInstruction.generatedSerializer(), { it.otherFields })

/** Instruction data as the RPC renders it: base58, not the base64 used for accounts. */
object Base58BytesSerializer : KSerializer<SolanaBytes> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaBase58Bytes", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): SolanaBytes = SolanaBytes.fromBase58(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: SolanaBytes) = encoder.encodeString(value.toBase58())
}
