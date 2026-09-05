package io.ethers.solana.providers

import io.ethers.providers.SubscriptionDescriptor
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Solana subscription routing for clients constructed outside [SolanaProvider.builder]. */
object SolanaSubscriptionDescriptor : SubscriptionDescriptor {
    override fun resolve(params: Array<*>): SubscriptionDescriptor.Resolved {
        val name = params.firstOrNull()?.toString().orEmpty()
        return SubscriptionDescriptor.Resolved(
            "${name}Subscribe",
            "${name}Unsubscribe",
            "${name}Notification",
            params.drop(1).toTypedArray(),
        ) { event ->
            name == "signature" && event.jsonObject.getValue("value") !is JsonPrimitive
        }
    }
}
