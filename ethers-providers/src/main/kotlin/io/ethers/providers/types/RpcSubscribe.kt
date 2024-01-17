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
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> map(mapper: Function<SubscriptionStream<T>, SubscriptionStream<R>>): RpcSubscribe<R> {
        return MappingRpcSubscribe(this) { response ->
            when {
                response.isError -> response.propagateError()
                else -> RpcResponse.result(mapper.apply(response.result!!))
            }
        }
    }

    /**
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun mapError(mapper: Function<RpcResponse.Error, RpcResponse.Error>): RpcSubscribe<T> {
        return MappingRpcSubscribe(this) { response ->
            when {
                response.isError -> RpcResponse.error(mapper.apply(response.error!!))
                else -> response
            }
        }
    }

    /**
     * Call the function with response if the call was successful, skipping if it failed. Useful when
     * chaining multiple fallible operations on the result.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> andThen(mapper: Function<SubscriptionStream<T>, RpcResponse<SubscriptionStream<R>>>): RpcSubscribe<R> {
        return MappingRpcSubscribe(this) { response ->
            when {
                response.isError -> response.propagateError()
                else -> mapper.apply(response.result!!)
            }
        }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun orElse(mapper: Function<RpcResponse.Error, RpcResponse<SubscriptionStream<T>>>): RpcSubscribe<T> {
        return MappingRpcSubscribe(this) { response ->
            when {
                response.isError -> mapper.apply(response.error!!)
                else -> response
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
