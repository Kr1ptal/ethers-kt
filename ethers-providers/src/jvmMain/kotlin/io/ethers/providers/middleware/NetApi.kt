package io.ethers.providers.middleware

import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest

interface NetApi {
    /**
     * Check if node is listening for network connections.
     */
    fun isListening(): RpcRequest<Boolean, RpcError>

    /**
     * Get number of connected peers.
     */
    fun getPeerCount(): RpcRequest<Long, RpcError>

    /**
     * Get current protocol version.
     */
    fun getVersion(): RpcRequest<String, RpcError>
}
