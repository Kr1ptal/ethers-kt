@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types

import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/** Decimal JSON number, unlike ethers-core's hexadecimal Ethereum quantities. */
object U64Serializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaU64", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: BigInteger) {
        val checked = requireU64(value)
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(checked.toString()))
    }
    override fun deserialize(decoder: Decoder): BigInteger {
        val primitive = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        require(!primitive.isString) { "Expected a decimal JSON number" }
        return requireU64(BigInteger(primitive.content))
    }
}

object TokenQuantitySerializer : KSerializer<BigInteger> {
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
@Serializable data class LatestBlockhash(val blockhash: SolanaBlockhash, val lastValidBlockHeight: BigInteger)
@Serializable data class EpochInfo(val absoluteSlot: BigInteger, val blockHeight: BigInteger, val epoch: BigInteger, val slotIndex: BigInteger, val slotsInEpoch: BigInteger, val transactionCount: BigInteger? = null)
@Serializable data class SolanaNodeVersion(@SerialName("solana-core") val solanaCore: String, @SerialName("feature-set") val featureSet: BigInteger? = null)
enum class SolanaNodeHealth { OK, ERROR }

@KeepGeneratedSerializer
@Serializable(with = TokenAmountSerializer::class)
data class TokenAmount(
    @Serializable(with = TokenQuantitySerializer::class) val amount: BigInteger,
    @Serializable(with = U8Serializer::class) val decimals: Int,
    val uiAmountString: String,
    @Serializable(with = DecimalSerializer::class) val uiAmount: BigDecimal? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object TokenAmountSerializer : ExtensibleJsonSerializer<TokenAmount>(TokenAmount.generatedSerializer(), { it.otherFields })

@Serializable data class PrioritizationFee(val slot: BigInteger, val prioritizationFee: BigInteger)
@Serializable data class TransactionSignature(val signature: SolanaSignature, val slot: BigInteger, val err: TransactionError?, val memo: String? = null, val blockTime: Long? = null, val confirmationStatus: Commitment? = null) {
    val isError: Boolean get() = err != null
}

/** A failed simulated transaction is a successful RPC response with a non-null [err]. */
@KeepGeneratedSerializer
@Serializable(with = TransactionSimulationSerializer::class)
data class TransactionSimulation(
    val err: TransactionError?,
    val logs: List<String>? = null,
    val unitsConsumed: BigInteger? = null,
    val returnData: ReturnData? = null,
    val accounts: List<AccountInfo?>? = null,
    val innerInstructions: List<InnerInstructions>? = null,
    val replacementBlockhash: LatestBlockhash? = null,
    @Serializable(with = U32Serializer::class) val loadedAccountsDataSize: Long? = null,
    val fee: BigInteger? = null,
    val preBalances: List<BigInteger>? = null,
    val postBalances: List<BigInteger>? = null,
    val preTokenBalances: List<TokenBalance>? = null,
    val postTokenBalances: List<TokenBalance>? = null,
    val loadedAddresses: LoadedAddresses? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
) {
    val isSuccess: Boolean get() = err == null
}

object TransactionSimulationSerializer : ExtensibleJsonSerializer<TransactionSimulation>(TransactionSimulation.generatedSerializer(), { it.otherFields })

@Serializable data class LogsNotification(val signature: SolanaSignature, val err: TransactionError?, val logs: List<String>)
@Serializable data class ProgramNotification(val pubkey: SolanaAddress, val account: AccountInfo)
@Serializable data class SlotNotification(val parent: BigInteger, val root: BigInteger, val slot: BigInteger)

sealed class SignatureNotification {
    abstract val context: RpcContext
    data class Received(override val context: RpcContext) : SignatureNotification()
    data class Status(override val context: RpcContext, val err: TransactionError?) : SignatureNotification()
}
