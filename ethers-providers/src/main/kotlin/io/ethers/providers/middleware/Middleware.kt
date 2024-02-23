package io.ethers.providers.middleware

import io.ethers.providers.JsonPubSubClient
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.Provider

/**
 * Middleware provides a way to customize the functionality of supported RPC calls. For example, you can write your own
 * middleware to change the gas oracle used for returning current optimal gas price.
 *
 * It's recommended to write your own middleware by using the delegation pattern, i.e. by extending [Middleware],
 * overriding only the methods you want to customize, and then delegating all calls to the [inner] middleware. This way
 * you can easily compose multiple middleware layers.
 *
 * Example:
 * ```kotlin
 * class GasOracleMiddleware(override val inner: Middleware) : Middleware by inner {
 *     override fun getGasPrice(): RpcRequest<BigInteger> {
 *         return inner.getGasPrice().map { RpcResponse.result(it * BigInteger.TWO) }
 *     }
 * }
 * ```
 * */
interface Middleware : EthApi, DebugApi, NetApi, TxpoolApi, Web3Api {
    /**
     * Get the underlying [JsonRpcClient].
     * */
    val client: JsonRpcClient

    /**
     * Get the [Middleware] layer before this one, or null if this is the bottom-most layer.
     * */
    val inner: Middleware?

    /**
     * Get the underlying [Provider].
     * */
    val provider: Provider

    /**
     * Whether this middleware supports pub/sub functionality.
     * */
    val isPubSub: Boolean
        get() = client is JsonPubSubClient
}
