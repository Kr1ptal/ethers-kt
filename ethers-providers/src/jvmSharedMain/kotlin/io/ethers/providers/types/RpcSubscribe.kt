package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import io.ethers.core.Result.Consumer
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CompletableFuture

interface RpcSubscribe<T : Any, E : Result.Error> {
    /**
     * Subscribe to a stream via RPC without blocking the calling thread.
     */
    suspend fun send(): Result<ChannelReceiver<T>, E>

    /**
     * Subscribe to a stream via RPC and await the subscription response by blocking the calling thread.
     */
    fun sendAwait(): Result<ChannelReceiver<T>, E> = runBlocking { send() }

    /**
     * Asynchronously subscribe to a stream via RPC as a [CompletableFuture].
     */
    fun sendAsync(): CompletableFuture<Result<ChannelReceiver<T>, E>> {
        return CoroutineScope(Dispatchers.Default).async { send() }.asCompletableFuture()
    }

    /**
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Any> map(mapper: Result.Transformer<ChannelReceiver<T>, ChannelReceiver<R>>): RpcSubscribe<R, E> {
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
    fun <R : Any> andThen(mapper: Result.Transformer<ChannelReceiver<T>, Result<ChannelReceiver<R>, E>>): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { it.andThen(mapper) }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Result.Error> orElse(mapper: Result.Transformer<E, Result<ChannelReceiver<T>, R>>): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { it.orElse(mapper) }
    }

    /**
     * Callback called only when the call has succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun onSuccess(block: Consumer<ChannelReceiver<T>>): RpcSubscribe<T, E> {
        return MappingRpcSubscribe(this) {
            it.onSuccess(block)

            it
        }
    }

    /**
     * Callback called only when the call has failed with an error.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun onFailure(block: Consumer<E>): RpcSubscribe<T, E> {
        return MappingRpcSubscribe(this) {
            it.onFailure(block)

            it
        }
    }
}

/**
 * Internal implementation of [RpcSubscribe] which always returns the same value.
 * */
internal class RpcSubscribeConstant<T : Any, E : Result.Error>(
    private val value: Result<ChannelReceiver<T>, E>,
) : RpcSubscribe<T, E> {
    override suspend fun send(): Result<ChannelReceiver<T>, E> = value

    override fun toString(): String = "RpcSubscribeConstant(value=$value)"
}

/**
 * Normal stream subscription via RPC.
 */
class RpcSubscribeCall<T : Any>(
    private val client: JsonRpcClient,
    private val params: Array<*>,
    private val resultDecoder: (JsonElement) -> T,
) : RpcSubscribe<T, RpcError> {
    @Suppress("UNCHECKED_CAST")
    constructor(
        client: JsonRpcClient,
        params: Array<*>,
        resultType: Class<T>,
    ) : this(client, params, { p -> io.ethers.core.Kotlinx.DEFAULT.decodeFromJsonElement(kotlinx.serialization.serializer(resultType), p) as T })

    override suspend fun send(): Result<ChannelReceiver<T>, RpcError> = client.subscribe(params, resultDecoder)

    override fun toString(): String {
        return "RpcSubscribeCall(params=${params.contentToString()})"
    }
}

/**
 * Stream subscription via RPC which uses [mapper] function to remap [ChannelReceiver].
 */
private class MappingRpcSubscribe<I : Any, O : Any, E : Result.Error, U : Result.Error>(
    private val request: RpcSubscribe<I, E>,
    private val mapper: (Result<ChannelReceiver<I>, E>) -> Result<ChannelReceiver<O>, U>,
) : RpcSubscribe<O, U> {
    override suspend fun send(): Result<ChannelReceiver<O>, U> = mapper(request.send())

    override fun toString(): String {
        return "MappingRpcSubscribe(request=$request)"
    }
}
