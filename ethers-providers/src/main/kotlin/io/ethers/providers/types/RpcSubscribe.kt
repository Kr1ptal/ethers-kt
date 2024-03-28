package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.Result
import io.ethers.providers.JsonPubSubClient
import io.ethers.providers.RpcError
import io.ethers.providers.SubscriptionStream
import java.util.concurrent.CompletableFuture
import java.util.function.Function

interface RpcSubscribe<T, E : Result.Error> {
    /**
     * Subscribe to stream via RPC and await the subscription response by blocking calling thread.
     */
    fun sendAwait(): Result<SubscriptionStream<T>, E>

    /**
     * Asynchronously subscribe to stream via RPC.
     */
    fun sendAsync(): CompletableFuture<Result<SubscriptionStream<T>, E>>

    /**
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> map(mapper: Result.Transformer<SubscriptionStream<T>, SubscriptionStream<R>>): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { it.map(mapper) }
    }

    /**
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Result.Error> mapError(mapper: Result.Transformer<E, R>): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { it.mapError(mapper) }
    }

    /**
     * Call the function with response if the call was successful, skipping if it failed. Useful when
     * chaining multiple fallible operations on the result.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> andThen(mapper: Result.Transformer<SubscriptionStream<T>, Result<SubscriptionStream<R>, E>>): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { it.andThen(mapper) }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Result.Error> orElse(mapper: Result.Transformer<E, Result<SubscriptionStream<T>, R>>): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { it.orElse(mapper) }
    }
}

/**
 * Internal implementation of [RpcSubscribe] which always returns the same value.
 * */
internal class RpcSubscribeConstant<T, E : Result.Error>(
    private val value: Result<SubscriptionStream<T>, E>,
) : RpcSubscribe<T, E> {
    override fun sendAwait(): Result<SubscriptionStream<T>, E> = value
    override fun sendAsync(): CompletableFuture<Result<SubscriptionStream<T>, E>> {
        return CompletableFuture.completedFuture(value)
    }

    override fun toString(): String = "RpcSubscribeConstant(value=$value)"
}

/**
 * Normal stream subscription via RPC.
 */
class RpcSubscribeCall<T>(
    private val client: JsonPubSubClient,
    private val params: Array<*>,
    private val resultDecoder: Function<JsonParser, T>,
) : RpcSubscribe<T, RpcError> {
    constructor(
        client: JsonPubSubClient,
        params: Array<*>,
        resultType: Class<T>,
    ) : this(client, params, { p -> p.readValueAs(resultType) })

    override fun sendAwait(): Result<SubscriptionStream<T>, RpcError> = sendAsync().join()
    override fun sendAsync(): CompletableFuture<Result<SubscriptionStream<T>, RpcError>> {
        return client.subscribe(params, resultDecoder)
    }

    override fun toString(): String {
        return "RpcSubscribeCall(params=${params.contentToString()})"
    }
}

/**
 * Stream subscription via RPC which uses [mapper] function to remap [SubscriptionStream].
 */
private class MappingRpcSubscribe<I, O, E : Result.Error, U : Result.Error>(
    private val request: RpcSubscribe<I, E>,
    private val mapper: Function<Result<SubscriptionStream<I>, E>, Result<SubscriptionStream<O>, U>>,
) : RpcSubscribe<O, U> {
    override fun sendAwait(): Result<SubscriptionStream<O>, U> = sendAsync().join()

    override fun sendAsync(): CompletableFuture<Result<SubscriptionStream<O>, U>> =
        request.sendAsync().thenApplyAsync(mapper)

    override fun toString(): String {
        return "MappingRpcSubscribe(request=$request)"
    }
}
