@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types.transaction

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
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
        if (addresses.size > 256) throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.LOOKUP_TABLE, addresses.size, 0..256).toException()
    }
}

/**
 * An instruction as it appears in a compiled message, with accounts as indices into the message's
 * account list. [accounts] is not copied; pass an immutable list.
 */
data class CompiledInstruction(val programIdIndex: Int, val accounts: List<Int>, val data: SolanaBytes) {
    /** Takes ownership of [data] rather than copying it, so do not mutate the array afterwards. */
    constructor(programIdIndex: Int, accounts: List<Int>, data: ByteArray) :
        this(programIdIndex, accounts, SolanaBytes.fromBytes(data))
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
