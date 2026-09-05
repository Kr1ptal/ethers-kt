package io.ethers.providers

import kotlinx.serialization.json.JsonElement

/** Protocol-specific subscription rules, configured once in [RpcClientConfig]. */
fun interface SubscriptionDescriptor {
    /**
     * Resolve a stream name and its arguments into wire-level routing. Called once per subscription;
     * the result is retained across reconnections. Implementations should snapshot [params].
     */
    fun resolve(params: Array<*>): Resolved

    /** Methods, wire parameters and completion rules for one subscription. */
    class Resolved(
        val subscribeMethod: String,
        val unsubscribeMethod: String,
        val notificationMethod: String,
        val params: Array<*>,
        val isTerminal: (JsonElement) -> Boolean = { false },
    )

    companion object {
        val ETHEREUM = SubscriptionDescriptor { params ->
            Resolved("eth_subscribe", "eth_unsubscribe", "eth_subscription", params.copyOf())
        }
    }
}
