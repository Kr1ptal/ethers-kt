@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types

import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Decimal JSON number, unlike ethers-core's hexadecimal Ethereum quantities. */
object U64Serializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaU64", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: BigInteger) {
        val checked = requireU64(value)
        (encoder as JsonEncoder).encodeJsonElement(kotlinx.serialization.json.Json.parseToJsonElement(checked.toString()))
    }
    override fun deserialize(decoder: Decoder): BigInteger {
        val primitive = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        require(!primitive.isString) { "Expected a decimal JSON number" }
        return requireU64(BigInteger(primitive.content))
    }
}

object TokenAmountSerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaTokenAmount", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigInteger) = encoder.encodeString(requireU64(value).toString())
    override fun deserialize(decoder: Decoder): BigInteger = requireU64(BigInteger(decoder.decodeString()))
}

@Serializable
enum class Commitment {
    @SerialName("finalized")
    FINALIZED,
    @SerialName("confirmed")
    CONFIRMED,
    @SerialName("processed")
    PROCESSED,
    ;

    override fun toString(): String = name.lowercase()
}

@Serializable data class RpcContext(val slot: BigInteger, val apiVersion: String? = null)
@Serializable data class ContextValue<T>(val context: RpcContext, val value: T)
@Serializable data class LatestBlockhash(val blockhash: Blockhash, val lastValidBlockHeight: BigInteger)
@Serializable data class EpochInfo(val absoluteSlot: BigInteger, val blockHeight: BigInteger, val epoch: BigInteger, val slotIndex: BigInteger, val slotsInEpoch: BigInteger, val transactionCount: BigInteger? = null)
@Serializable data class Version(@SerialName("solana-core") val solanaCore: String, @SerialName("feature-set") val featureSet: BigInteger? = null)
enum class Health { OK, ERROR }

/** Account data is decoded from the explicitly requested base64 encoding. */
class AccountInfo(data: ByteArray, val executable: Boolean, val lamports: BigInteger, val owner: PublicKey, val rentEpoch: BigInteger, val space: BigInteger) {
    private val payload = data.copyOf()
    val data: ByteArray get() = payload.copyOf()
    override fun equals(other: Any?): Boolean = other is AccountInfo && payload.contentEquals(other.payload) && executable == other.executable && lamports == other.lamports && owner == other.owner && rentEpoch == other.rentEpoch && space == other.space
    override fun hashCode(): Int = listOf(payload.contentHashCode(), executable, lamports, owner, rentEpoch, space).hashCode()
}

@Serializable
data class TokenAmount(
    @Serializable(with = TokenAmountSerializer::class) val amount: BigInteger,
    val decimals: Int,
    val uiAmountString: String,
)

@Serializable data class PrioritizationFee(val slot: BigInteger, val prioritizationFee: BigInteger)
@Serializable data class TransactionSignature(val signature: Signature, val slot: BigInteger, val err: JsonElement? = null, val memo: String? = null, val blockTime: Long? = null, val confirmationStatus: Commitment? = null) {
    val isError: Boolean get() = err != null
}

/** A failed simulated transaction is a successful RPC response with a non-null [err]. */
@Serializable data class TransactionSimulation(val err: JsonElement? = null, val logs: List<String>? = null, val unitsConsumed: BigInteger? = null, val returnData: JsonElement? = null, val accounts: JsonElement? = null) {
    val isSuccess: Boolean get() = err == null
}

@Serializable data class LogsNotification(val signature: Signature, val err: JsonElement? = null, val logs: List<String>)
data class ProgramNotification(val pubkey: PublicKey, val account: AccountInfo)
@Serializable data class SlotNotification(val parent: BigInteger, val root: BigInteger, val slot: BigInteger)

sealed class SignatureNotification {
    abstract val context: RpcContext
    data class Received(override val context: RpcContext) : SignatureNotification()
    data class Status(override val context: RpcContext, val err: JsonElement?) : SignatureNotification()
}
