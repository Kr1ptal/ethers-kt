@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

/** Account data in the requested base64 encoding, shared by queries, subscriptions and simulation. */
@Serializable(with = AccountInfoSerializer::class)
class AccountInfo(
    data: ByteArray,
    val executable: Boolean,
    val lamports: BigInteger,
    val owner: SolanaAddress,
    val rentEpoch: BigInteger,
    val space: BigInteger,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
) {
    private val payload = data.copyOf()
    val data: ByteArray get() = payload.copyOf()
    override fun equals(other: Any?): Boolean = other is AccountInfo && payload.contentEquals(other.payload) && executable == other.executable && lamports == other.lamports && owner == other.owner && rentEpoch == other.rentEpoch && space == other.space && otherFields == other.otherFields
    override fun hashCode(): Int = listOf(payload.contentHashCode(), executable, lamports, owner, rentEpoch, space, otherFields).hashCode()
}

object AccountInfoSerializer : KSerializer<AccountInfo> {
    override val descriptor = AccountInfoFields.serializer().descriptor
    override fun deserialize(decoder: Decoder): AccountInfo {
        val fields = decoder.decodeSerializableValue(AccountInfoFields.serializer())
        return AccountInfo(fields.data.asByteArray(), fields.executable, fields.lamports, fields.owner, fields.rentEpoch, fields.space, fields.otherFields)
    }
    override fun serialize(encoder: Encoder, value: AccountInfo) = encoder.encodeSerializableValue(AccountInfoFields.serializer(), AccountInfoFields(Bytes(value.data), value.executable, value.lamports, value.owner, value.rentEpoch, value.space, value.otherFields))
}

@KeepGeneratedSerializer
@Serializable(with = AccountInfoFieldsSerializer::class)
private data class AccountInfoFields(
    @Serializable(with = Base64BytesSerializer::class) val data: Bytes,
    val executable: Boolean,
    val lamports: BigInteger,
    val owner: SolanaAddress,
    val rentEpoch: BigInteger,
    // Older responses omit space. The complete requested account data supplies its size.
    val space: BigInteger = bigIntegerOf(data.size),
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

private object AccountInfoFieldsSerializer : ExtensibleJsonSerializer<AccountInfoFields>(AccountInfoFields.generatedSerializer(), { it.otherFields })
