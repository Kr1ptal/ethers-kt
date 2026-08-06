package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import io.ethers.core.Result.Consumer
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.core.success
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.decoderFor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmSynthetic

/**
 * Seam through which each platform adds its own conveniences to [RpcSubscribe].
 *
 * It exists so platform helpers can be inherited *members* rather than extension functions - Java callers get
 * `subscription.sendAwait()` instead of a static call. All implementation stays in ordinary common code in [RpcSubscribe].
 *
 * JVM and Android actualize this with blocking and `CompletableFuture` variants. A platform without those
 * primitives actualizes it with no extra members.
 */
expect interface PlatformRpcSubscribe<T : Any, E : ThrowableError> {
    suspend fun send(): Result<ChannelReceiver<T>, E>
}

interface RpcSubscribe<T : Any, E : ThrowableError> : PlatformRpcSubscribe<T, E> {
    /**
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Any> map(mapper: Result.Transformer<ChannelReceiver<T>, ChannelReceiver<R>>): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { it.map(mapper) }
    }

    /**
     * Same as [map], but the mapper is allowed to suspend.
     */
    @JvmSynthetic
    fun <R : Any> map(mapper: suspend (ChannelReceiver<T>) -> ChannelReceiver<R>): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { result ->
            if (result.isFailure()) result else success(mapper(result.value))
        }
    }

    /**
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : ThrowableError> mapError(mapper: Result.Transformer<E, R>): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { it.mapError(mapper) }
    }

    /**
     * Same as [mapError], but the mapper is allowed to suspend.
     */
    @JvmSynthetic
    fun <R : ThrowableError> mapError(mapper: suspend (E) -> R): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { result ->
            if (result.isFailure()) failure(mapper(result.error)) else result
        }
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
     * Same as [andThen], but the mapper is allowed to suspend.
     */
    @JvmSynthetic
    fun <R : Any> andThen(
        mapper: suspend (ChannelReceiver<T>) -> Result<ChannelReceiver<R>, E>,
    ): RpcSubscribe<R, E> {
        return MappingRpcSubscribe(this) { result ->
            if (result.isFailure()) result else mapper(result.value)
        }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : ThrowableError> orElse(mapper: Result.Transformer<E, Result<ChannelReceiver<T>, R>>): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { it.orElse(mapper) }
    }

    /**
     * Same as [orElse], but the recovery function is allowed to suspend.
     */
    @JvmSynthetic
    fun <R : ThrowableError> orElse(
        mapper: suspend (E) -> Result<ChannelReceiver<T>, R>,
    ): RpcSubscribe<T, R> {
        return MappingRpcSubscribe(this) { result ->
            if (result.isFailure()) mapper(result.error) else result
        }
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
     * Same as [onSuccess], but the callback is allowed to suspend.
     */
    @JvmSynthetic
    fun onSuccess(block: suspend (ChannelReceiver<T>) -> Unit): RpcSubscribe<T, E> {
        return MappingRpcSubscribe(this) { result ->
            if (result.isSuccess()) block(result.value)
            result
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

    /**
     * Same as [onFailure], but the callback is allowed to suspend.
     */
    @JvmSynthetic
    fun onFailure(block: suspend (E) -> Unit): RpcSubscribe<T, E> {
        return MappingRpcSubscribe(this) { result ->
            if (result.isFailure()) block(result.error)
            result
        }
    }
}

/**
 * Internal implementation of [RpcSubscribe] which always returns the same value.
 * */
internal class RpcSubscribeConstant<T : Any, E : ThrowableError>(
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
    constructor(
        client: JsonRpcClient,
        params: Array<*>,
        resultSerializer: KSerializer<T>,
    ) : this(client, params, decoderFor(resultSerializer))

    override suspend fun send(): Result<ChannelReceiver<T>, RpcError> = client.subscribe(params, resultDecoder)

    override fun toString(): String {
        return "RpcSubscribeCall(params=${params.contentToString()})"
    }
}

/**
 * Stream subscription via RPC which uses [mapper] function to remap [ChannelReceiver].
 */
private class MappingRpcSubscribe<I : Any, O : Any, E : ThrowableError, U : ThrowableError>(
    private val request: RpcSubscribe<I, E>,
    private val mapper: suspend (Result<ChannelReceiver<I>, E>) -> Result<ChannelReceiver<O>, U>,
) : RpcSubscribe<O, U> {
    override suspend fun send(): Result<ChannelReceiver<O>, U> = mapper(request.send())

    override fun toString(): String {
        return "MappingRpcSubscribe(request=$request)"
    }
}
