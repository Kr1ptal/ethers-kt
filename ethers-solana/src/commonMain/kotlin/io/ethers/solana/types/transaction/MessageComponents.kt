@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.U8ListSerializer
import io.ethers.solana.types.U8Serializer
import io.ethers.solana.utils.U64_MAX
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@KeepGeneratedSerializer
@Serializable(with = MessageHeaderSerializer::class)
data class MessageHeader(
    @SerialName("numRequiredSignatures") @Serializable(with = U8Serializer::class) val requiredSignatures: Int,
    @SerialName("numReadonlySignedAccounts") @Serializable(with = U8Serializer::class) val readonlySignedAccounts: Int,
    @SerialName("numReadonlyUnsignedAccounts") @Serializable(with = U8Serializer::class) val readonlyUnsignedAccounts: Int,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object MessageHeaderSerializer : ExtensibleJsonSerializer<MessageHeader>(MessageHeader.generatedSerializer(), { it.otherFields })

/**
 * An address lookup table's contents, as needed to compile a v0 message against it.
 *
 * [deactivationSlot] and [authority] are populated by [decode]; a table whose deactivation slot has
 * passed can no longer be used by a transaction.
 */
data class AddressLookupTableAccount @JvmOverloads constructor(
    val key: SolanaAddress,
    val addresses: List<SolanaAddress>,
    val deactivationSlot: BigInteger? = null,
    val authority: SolanaAddress? = null,
) {
    init {
        if (addresses.size > 256) throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.LOOKUP_TABLE, addresses.size, 0..256).toException()
    }

    companion object {
        /** Bytes preceding the addresses in an on-chain lookup table account. */
        const val META_SIZE: Int = 56

        private const val LOOKUP_TABLE_DISCRIMINANT = 1

        /**
         * Decode an on-chain lookup table account: a [META_SIZE]-byte header followed by 32-byte
         * addresses. A never-deactivated table stores `u64::MAX`, which becomes a null
         * [deactivationSlot].
         */
        @JvmStatic
        fun tryDecode(key: SolanaAddress, data: ByteArray): Result<AddressLookupTableAccount, SolanaTransactionError> {
            if (data.size < META_SIZE || (data.size - META_SIZE) % 32 != 0) {
                return Result.failure(
                    SolanaTransactionError.MalformedBytes("A lookup table account holds $META_SIZE header bytes and whole addresses, got ${data.size}"),
                )
            }
            val decoder = SolanaMessageDecoder(data)
            val discriminant = decoder.readUnsignedLittleEndian(4).toLong()
            if (discriminant != LOOKUP_TABLE_DISCRIMINANT.toLong()) {
                return Result.failure(SolanaTransactionError.MalformedBytes("Account is not a lookup table, got discriminant $discriminant"))
            }
            val deactivationSlot = decoder.readUnsignedLittleEndian(8)
            decoder.readBytes(9) // last extended slot and its start index
            val authority = if (decoder.readByte() == 0) null.also { decoder.readBytes(32) } else SolanaAddress(decoder.readBytes(32))
            decoder.readBytes(2) // padding
            val addresses = List((data.size - META_SIZE) / 32) { SolanaAddress(decoder.readBytes(32)) }
            return Result.success(
                AddressLookupTableAccount(key, addresses, deactivationSlot.takeIf { it != U64_MAX }, authority),
            )
        }

        @JvmStatic
        fun decode(key: SolanaAddress, data: ByteArray): AddressLookupTableAccount = tryDecode(key, data).unwrap()
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
