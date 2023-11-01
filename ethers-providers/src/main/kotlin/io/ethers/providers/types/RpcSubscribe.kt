package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonParser
import io.ethers.providers.JsonPubSubClient
import io.ethers.providers.SubscriptionStream
import java.util.concurrent.CompletableFuture
import java.util.function.Function

interface RpcSubscribe<T> {
    /**
     * Subscribe to stream via RPC and await the subscription response by blocking calling thread.
     */
    fun sendAwait(): RpcResponse<SubscriptionStream<T>>

    /**
     * Asynchronously subscribe to stream via RPC.
     */
    fun sendAsync(): CompletableFuture<RpcResponse<SubscriptionStream<T>>>

    /**
     * Remap the returned [SubscriptionStream] if the call was successful.
     */
    fun <R> map(mapper: Function<SubscriptionStream<T>, RpcResponse<SubscriptionStream<R>>>): RpcSubscribe<R> {
        return MappingRpcSubscribe(this) {
            if (it.isError) {
                it.propagateError()
            } else {
                mapper.apply(it.resultOrThrow())
            }
        }
    }
}

/**
 * Normal stream subscription via RPC.
 */
class RpcSubscribeCall<T>(
    private val client: JsonPubSubClient,
    private val params: Array<*>,
    private val resultDecoder: Function<JsonParser, T>,
) : RpcSubscribe<T> {
    constructor(
        client: JsonPubSubClient,
        params: Array<*>,
        resultType: Class<T>,
    ) : this(client, params, { p -> p.readValueAs(resultType) })

    override fun sendAwait() = sendAsync().join()
    override fun sendAsync(): CompletableFuture<RpcResponse<SubscriptionStream<T>>> {
        return client.subscribe(params, resultDecoder)
    }

    override fun toString(): String {
        return "RpcSubscribeCall(params=${params.contentToString()})"
    }
}

/**
 * Stream subscription via RPC which uses [mapper] function to remap [SubscriptionStream].
 */
private class MappingRpcSubscribe<I, O>(
    private val request: RpcSubscribe<I>,
    private val mapper: Function<RpcResponse<SubscriptionStream<I>>, RpcResponse<SubscriptionStream<O>>>,
) : RpcSubscribe<O> {
    override fun sendAwait(): RpcResponse<SubscriptionStream<O>> = sendAsync().join()

    override fun sendAsync(): CompletableFuture<RpcResponse<SubscriptionStream<O>>> =
        request.sendAsync().thenApplyAsync(mapper)

    override fun toString(): String {
        return "MappingRpcSubscribe(request=$request)"
    }
}
