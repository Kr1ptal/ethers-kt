package io.ethers.providers

import io.ethers.providers.types.RpcParams

/** Protocol-specific subscription rules, configured once in [RpcClientConfig]. */
fun interface SubscriptionDescriptor {
    /**
     * Resolve a stream name and its arguments into wire-level routing. Called once per subscription;
     * the result is retained across reconnections.
     */
    fun resolve(params: RpcParams): Resolved

    /**
     * Methods, wire parameters and completion rules for one subscription.
     *
     * @param isTerminal whether an event is the subscription's last, after which the server ends the subscription, as
     * Solana does after a signature's status. The stream delivers that event and closes, without unsubscribing, and the
     * subscription is not renewed on reconnect. It is called with each event as the subscriber decoded it, so it
     * recognizes only the event types it checks for.
     */
    class Resolved(
        val subscribeMethod: String,
        val unsubscribeMethod: String,
        val notificationMethod: String,
        val params: RpcParams,
        val isTerminal: (Any) -> Boolean = { false },
    )

    companion object {
        val ETHEREUM = SubscriptionDescriptor { params ->
            Resolved("eth_subscribe", "eth_unsubscribe", "eth_subscription", params)
        }
    }
}
