@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types.transaction

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.U8ListSerializer
import io.ethers.solana.types.U8Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

@KeepGeneratedSerializer
@Serializable(with = MessageHeaderSerializer::class)
data class MessageHeader(
    @SerialName("numRequiredSignatures") @Serializable(with = U8Serializer::class) val requiredSignatures: Int,
    @SerialName("numReadonlySignedAccounts") @Serializable(with = U8Serializer::class) val readonlySignedAccounts: Int,
    @SerialName("numReadonlyUnsignedAccounts") @Serializable(with = U8Serializer::class) val readonlyUnsignedAccounts: Int,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object MessageHeaderSerializer : ExtensibleJsonSerializer<MessageHeader>(MessageHeader.generatedSerializer(), { it.otherFields })

data class AddressLookupTableAccount(val key: SolanaAddress, val addresses: List<SolanaAddress>) {
    init {
        if (addresses.size > 256) throw SolanaTransactionError.LookupTableTooLarge(addresses.size).toException()
    }
}

data class CompiledInstruction(val programIdIndex: Int, val accounts: List<Int>, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CompiledInstruction

        if (programIdIndex != other.programIdIndex) return false
        if (accounts != other.accounts) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = programIdIndex
        result = 31 * result + accounts.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

@Serializable(with = CompiledAddressLookupTableSerializer::class)
data class CompiledAddressLookupTable(
    val key: SolanaAddress,
    val writableIndexes: List<Int>,
    val readonlyIndexes: List<Int>,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object CompiledAddressLookupTableSerializer : KSerializer<CompiledAddressLookupTable> {
    override val descriptor = LookupTableFields.serializer().descriptor
    override fun deserialize(decoder: Decoder): CompiledAddressLookupTable {
        val fields = decoder.decodeSerializableValue(LookupTableFields.serializer())
        return CompiledAddressLookupTable(fields.accountKey, fields.writableIndexes, fields.readonlyIndexes, fields.otherFields)
    }
    override fun serialize(encoder: Encoder, value: CompiledAddressLookupTable) = encoder.encodeSerializableValue(LookupTableFields.serializer(), LookupTableFields(value.key, value.writableIndexes, value.readonlyIndexes, value.otherFields))
}

@KeepGeneratedSerializer
@Serializable(with = LookupTableFieldsSerializer::class)
private data class LookupTableFields(
    val accountKey: SolanaAddress,
    @Serializable(with = U8ListSerializer::class) val writableIndexes: List<Int>,
    @Serializable(with = U8ListSerializer::class) val readonlyIndexes: List<Int>,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

private object LookupTableFieldsSerializer : ExtensibleJsonSerializer<LookupTableFields>(LookupTableFields.generatedSerializer(), { it.otherFields })
