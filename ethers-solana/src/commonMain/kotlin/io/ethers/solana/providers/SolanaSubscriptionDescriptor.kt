package io.ethers.solana.providers

import io.ethers.providers.SubscriptionDescriptor
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.SignatureNotification
import kotlinx.serialization.json.JsonObject

/**
 * Solana subscription routing for clients constructed outside [SolanaProvider.builder].
 *
 * The node ends a signature subscription after sending its status, which closes the stream. The status is recognized
 * in a `ContextValue<SignatureNotification>`, as [SolanaProvider.subscribeSignature] decodes it, or in a [JsonObject]
 * when the subscriber decodes events as JSON; a subscriber decoding into another type must close the stream itself.
 */
object SolanaSubscriptionDescriptor : SubscriptionDescriptor {
    private val NEVER: (Any) -> Boolean = { false }

    private val SIGNATURE_STATUS: (Any) -> Boolean = { event ->
        when (event) {
            is ContextValue<*> -> event.value is SignatureNotification.Status
            // a status is an object, `{"err":...}`, and the received event the string "receivedSignature"
            is JsonObject -> event["value"] is JsonObject
            else -> false
        }
    }

    override fun resolve(params: Array<*>): SubscriptionDescriptor.Resolved {
        val name = params.firstOrNull()?.toString().orEmpty()
        return SubscriptionDescriptor.Resolved(
            "${name}Subscribe",
            "${name}Unsubscribe",
            "${name}Notification",
            params.drop(1).toTypedArray(),
            if (name == "signature") SIGNATURE_STATUS else NEVER,
        )
    }
}
