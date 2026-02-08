package io.ethers.providers.middleware

import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest

interface Web3Api {
    /**
     * Get the client version of the connected node.
     * */
    fun getClientVersion(): RpcRequest<String, RpcError>
}
