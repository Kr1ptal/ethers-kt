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

class AddressLookupTableAccount(val key: SolanaAddress, addresses: List<SolanaAddress>) {
    private val entries = addresses.toList().also { require(it.size <= 256) { "Lookup table exceeds 256 addresses" } }
    val addresses: List<SolanaAddress> get() = entries.toList()
}

class CompiledInstruction(val programIdIndex: Int, accounts: List<Int>, data: ByteArray) {
    private val indices = accounts.toList()
    private val payload = data.copyOf()
    val accounts: List<Int> get() = indices.toList()
    val data: ByteArray get() = payload.copyOf()
}

@Serializable(with = CompiledAddressLookupTableSerializer::class)
class CompiledAddressLookupTable(
    val key: SolanaAddress,
    writableIndexes: List<Int>,
    readonlyIndexes: List<Int>,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
) {
    private val writable = writableIndexes.toList()
    private val readonly = readonlyIndexes.toList()
    val writableIndexes: List<Int> get() = writable.toList()
    val readonlyIndexes: List<Int> get() = readonly.toList()
}

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
