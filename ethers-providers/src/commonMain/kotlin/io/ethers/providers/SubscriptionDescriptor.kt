package io.ethers.providers

import kotlinx.serialization.json.JsonElement

/** Protocol-specific subscription routing and completion rules, retained across reconnections. */
class SubscriptionDescriptor(
    val subscribeMethod: String,
    val unsubscribeMethod: String,
    val notificationMethod: String,
    val isTerminal: (JsonElement) -> Boolean = { false },
) {
    companion object {
        val ETHEREUM = SubscriptionDescriptor("eth_subscribe", "eth_unsubscribe", "eth_subscription")
    }
}
