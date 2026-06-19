package io.ethers.providers.types

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.michaelbull.result.orElse
import io.channels.core.ChannelReceiver
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CompletableFuture

interface RpcSubscribe<T : Any, E> {
    /**
     * Subscribe to stream via RPC and await the subscription response by blocking calling thread.
     */
    fun sendAwait(): Result<ChannelReceiver<T>, E>

    /**
     * Asynchronously subscribe to stream via RPC.
     */
    fun sendAsync(): CompletableFuture<Result<ChannelReceiver<T>, E>>

    /**
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Any> map(mapper: (ChannelReceiver<T>) -> ChannelReceiver<R>): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { it.map(mapper) }
    }

    /**
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> mapError(mapper: (E) -> R): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { it.mapError(mapper) }
    }

    /**
     * Call the function with response if the call was successful, skipping if it failed. Useful when
     * chaining multiple fallible operations on the result.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Any> andThen(mapper: (ChannelReceiver<T>) -> Result<ChannelReceiver<R>, E>): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { it.andThen(mapper) }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> orElse(mapper: (E) -> Result<ChannelReceiver<T>, R>): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { it.orElse(mapper) }
    }

    /**
     * Callback called only when the call has succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun onSuccess(block: (ChannelReceiver<T>) -> Unit): RpcSubscribe<T, E> {
        return MappingRpcSubscribe(this) { it.onOk(block) }
    }

    /**
     * Callback called only when the call has failed with an error.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun onFailure(block: (E) -> Unit): RpcSubscribe<T, E> {
        return MappingRpcSubscribe(this) { it.onErr(block) }
    }
}

/**
 * Internal implementation of [RpcSubscribe] which always returns the same value.
 * */
internal class RpcSubscribeConstant<T : Any, E>(
    private val value: Result<ChannelReceiver<T>, E>,
) : RpcSubscribe<T, E> {
    override fun sendAwait(): Result<ChannelReceiver<T>, E> = value
    override fun sendAsync(): CompletableFuture<Result<ChannelReceiver<T>, E>> {
        return CompletableFuture.completedFuture(value)
    }

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

    override fun sendAwait(): Result<ChannelReceiver<T>, RpcError> = sendAsync().join()
    override fun sendAsync(): CompletableFuture<Result<ChannelReceiver<T>, RpcError>> {
        return client.subscribe(params, resultDecoder)
    }

    override fun toString(): String {
        return "RpcSubscribeCall(params=${params.contentToString()})"
    }
}

/**
 * Stream subscription via RPC which uses [mapper] function to remap [ChannelReceiver].
 */
private class MappingRpcSubscribe<I : Any, O : Any, E, U>(
    private val request: RpcSubscribe<I, E>,
    private val mapper: (Result<ChannelReceiver<I>, E>) -> Result<ChannelReceiver<O>, U>,
) : RpcSubscribe<O, U> {
    override fun sendAwait(): Result<ChannelReceiver<O>, U> = sendAsync().join()

    override fun sendAsync(): CompletableFuture<Result<ChannelReceiver<O>, U>> = request.sendAsync().thenApplyAsync { mapper(it) }

    override fun toString(): String {
        return "MappingRpcSubscribe(request=$request)"
    }
}
