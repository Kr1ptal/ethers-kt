@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.Base64TupleBytesSerializer
import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.ethers.core.json.JsonElement as RawJson

/** Account data in the requested base64 encoding, shared by queries, subscriptions and simulation. */
@Serializable(with = AccountInfoSerializer::class)
data class AccountInfo(
    val data: SolanaBytes,
    val executable: Boolean,
    val lamports: BigInteger,
    val owner: SolanaAddress,
    val rentEpoch: BigInteger,
    val space: BigInteger,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
) {
    /** Takes ownership of [data] rather than copying it, so do not mutate the array afterwards. */
    constructor(
        data: ByteArray,
        executable: Boolean,
        lamports: BigInteger,
        owner: SolanaAddress,
        rentEpoch: BigInteger,
        space: BigInteger,
        otherFields: Map<String, RawJson> = emptyMap(),
    ) : this(SolanaBytes.fromBytes(data), executable, lamports, owner, rentEpoch, space, otherFields)
}

object AccountInfoSerializer : KSerializer<AccountInfo> {
    override val descriptor = AccountInfoFields.serializer().descriptor
    override fun deserialize(decoder: Decoder): AccountInfo {
        val fields = decoder.decodeSerializableValue(AccountInfoFields.serializer())
        return AccountInfo(fields.data, fields.executable, fields.lamports, fields.owner, fields.rentEpoch, fields.space, fields.otherFields)
    }
    override fun serialize(encoder: Encoder, value: AccountInfo) = encoder.encodeSerializableValue(AccountInfoFields.serializer(), AccountInfoFields(value.data, value.executable, value.lamports, value.owner, value.rentEpoch, value.space, value.otherFields))
}

@KeepGeneratedSerializer
@Serializable(with = AccountInfoFieldsSerializer::class)
private data class AccountInfoFields(
    @Serializable(with = Base64TupleBytesSerializer::class) val data: SolanaBytes,
    val executable: Boolean,
    val lamports: BigInteger,
    val owner: SolanaAddress,
    val rentEpoch: BigInteger,
    // Older responses omit space. The complete requested account data supplies its size.
    val space: BigInteger = bigIntegerOf(data.size),
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

private object AccountInfoFieldsSerializer : ExtensibleJsonSerializer<AccountInfoFields>(AccountInfoFields.generatedSerializer(), { it.otherFields })
