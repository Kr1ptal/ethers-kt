package io.ethers.providers.middleware

import io.ethers.providers.types.RpcRequest

interface NetApi {
    /**
     * Check if node is listening for network connections.
     */
    fun isListening(): RpcRequest<Boolean>

    /**
     * Get number of connected peers.
     */
    fun getPeerCount(): RpcRequest<Long>

    /**
     * Get current protocol version.
     */
    fun getVersion(): RpcRequest<String>
}
