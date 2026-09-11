@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.core.Result
import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.MessageInstruction
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
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
) {
    /**
     * Rebuild the signable message this response describes.
     *
     * [type] selects the layout, since a message with no lookup tables is shaped the same whether it
     * came from a legacy or a v0 transaction. A v1 message must carry its inline config.
     */
    fun toTransaction(type: SolanaTxType): Result<SolanaTransactionUnsigned, SolanaTransactionError> {
        val compiled = instructions.map { MessageInstruction(it.programIdIndex, it.accounts, it.data) }
        return when (type) {
            SolanaTxType.Legacy -> SolanaTxLegacy.create(header, accountKeys, recentBlockhash, compiled)
            SolanaTxType.V0 -> SolanaTxV0.create(header, accountKeys, recentBlockhash, compiled, addressTableLookups)
            SolanaTxType.V1 -> {
                val config = transactionConfig
                    ?: return Result.failure(
                        SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.CONFIG, "A v1 message must carry its inline config"),
                    )
                SolanaTxV1.create(header, accountKeys, recentBlockhash, compiled, config)
            }

            is SolanaTxType.Unsupported -> Result.failure(SolanaTransactionError.UnsupportedVersion(type.version))
        }
    }
}

object SolanaRPCMessageSerializer : ExtensibleJsonSerializer<SolanaRPCMessage>(SolanaRPCMessage.generatedSerializer(), { it.otherFields })
