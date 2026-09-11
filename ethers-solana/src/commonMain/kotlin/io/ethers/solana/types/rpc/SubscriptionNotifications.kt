@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
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
