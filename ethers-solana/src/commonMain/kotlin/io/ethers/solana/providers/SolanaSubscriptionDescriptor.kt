package io.ethers.solana.providers

import io.ethers.providers.SubscriptionDescriptor
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Solana subscription routing for clients constructed outside [SolanaProvider.builder]. */
object SolanaSubscriptionDescriptor : SubscriptionDescriptor {
    override fun resolve(params: Array<*>): SubscriptionDescriptor.Resolved {
        val name = params.firstOrNull() as? String
        require(name in setOf("account", "program", "logs", "signature", "slot", "root")) {
            "Expected a Solana subscription name as the first parameter"
        }
        return SubscriptionDescriptor.Resolved(
            "${name}Subscribe",
            "${name}Unsubscribe",
            "${name}Notification",
            params.copyOfRange(1, params.size),
        ) { event ->
            name == "signature" && event.jsonObject.getValue("value") !is JsonPrimitive
        }
    }
}
