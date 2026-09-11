@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable data class LogsNotification(val signature: SolanaSignature, val err: TransactionError?, val logs: List<String>)
@Serializable data class SlotNotification(val parent: BigInteger, val root: BigInteger, val slot: BigInteger)

/** Signature notification payload; the slot and API version belong to the enclosing [ContextValue]. */
@Serializable(with = SignatureNotificationSerializer::class)
sealed interface SignatureNotification {
    data object Received : SignatureNotification

    @Serializable
    data class Status(val err: TransactionError?) : SignatureNotification
}

/** The RPC payload is either the received-signature string or a status object, without a discriminator. */
object SignatureNotificationSerializer : KSerializer<SignatureNotification> {
    override val descriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): SignatureNotification {
        val input = decoder as JsonDecoder
        return when (val value = input.decodeJsonElement()) {
            is JsonObject -> input.json.decodeFromJsonElement(SignatureNotification.Status.serializer(), value)
            is JsonPrimitive -> {
                if (!value.isString || value.content != "receivedSignature") {
                    throw SerializationException("Expected receivedSignature or a signature status object")
                }
                SignatureNotification.Received
            }
            else -> throw SerializationException("Expected receivedSignature or a signature status object")
        }
    }

    override fun serialize(encoder: Encoder, value: SignatureNotification) {
        when (value) {
            SignatureNotification.Received -> (encoder as JsonEncoder).encodeJsonElement(JsonPrimitive("receivedSignature"))
            is SignatureNotification.Status -> encoder.encodeSerializableValue(SignatureNotification.Status.serializer(), value)
        }
    }
}

/** A block as blockSubscribe delivers it, with [err] set when the block could not be processed. */
@Serializable
data class BlockNotification(val slot: BigInteger, val block: SolanaBlock? = null, val err: TransactionError? = null)

/**
 * A step in one slot's progress through the node, which is finer grained than slotSubscribe.
 *
 * Which fields are set depends on [type]: `createdBank` carries [parent], `frozen` carries [stats],
 * and `dead` carries [err]. [timestamp] is Unix milliseconds.
 */
@Serializable
data class SlotUpdateNotification(
    val slot: BigInteger,
    val timestamp: BigInteger,
    val type: SlotUpdateType,
    val parent: BigInteger? = null,
    val stats: SlotTransactionStats? = null,
    val err: String? = null,
)

@Serializable
data class SlotTransactionStats(
    val numTransactionEntries: BigInteger,
    val numSuccessfulTransactions: BigInteger,
    val numFailedTransactions: BigInteger,
    val maxTransactionsPerEntry: BigInteger,
)

@Serializable
enum class SlotUpdateType {
    @SerialName("firstShredReceived")
    FIRST_SHRED_RECEIVED,

    @SerialName("completed")
    COMPLETED,

    @SerialName("createdBank")
    CREATED_BANK,

    @SerialName("frozen")
    FROZEN,

    @SerialName("dead")
    DEAD,

    @SerialName("optimisticConfirmation")
    OPTIMISTIC_CONFIRMATION,

    @SerialName("root")
    ROOT,
}

/** A vote observed in gossip, which is not necessarily one that landed on chain. */
@Serializable
data class VoteNotification(
    val hash: SolanaBlockhash,
    val slots: List<BigInteger>,
    val timestamp: Long? = null,
    val signature: SolanaSignature? = null,
    val votePubkey: SolanaAddress? = null,
)
