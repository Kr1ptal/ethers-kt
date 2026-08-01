package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.Result.Consumer
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.core.success
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.decoderFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

abstract class RpcRequest<T, E : Result.Error> : PlatformRpcRequest<T, E>() {
    /**
     * Batch this into provided [BatchRpcRequest].
     */
    abstract fun batch(batch: BatchRpcRequest): BatchRpcResponse<Result<T, E>>

    /**
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R> map(mapper: Result.Transformer<T, R>): RpcRequest<R, E> {
        return MappingRpcRequest(this) { it.map(mapper) }
    }

    /**
     * Same as [map], but the mapper is allowed to suspend.
     */
    @JvmSynthetic
    fun <R> map(mapper: suspend (T) -> R): RpcRequest<R, E> {
        return MappingRpcRequest(this) { result ->
            if (result.isFailure()) result else success(mapper(result.value))
        }
    }

    /**
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R : Result.Error> mapError(mapper: Result.Transformer<E, R>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.mapError(mapper) }
    }

    /**
     * Same as [mapError], but the mapper is allowed to suspend.
     */
    @JvmSynthetic
    fun <R : Result.Error> mapError(mapper: suspend (E) -> R): RpcRequest<T, R> {
        return MappingRpcRequest(this) { result ->
            if (result.isFailure()) failure(mapper(result.error)) else result
        }
    }

    /**
     * Call the function with response if the call was successful, skipping if it failed. Useful when
     * chaining multiple fallible operations on the result.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R> andThen(mapper: Result.Transformer<T, Result<R, E>>): RpcRequest<R, E> {
        return MappingRpcRequest(this) { it.andThen(mapper) }
    }

    /**
     * Same as [andThen], but the mapper is allowed to suspend.
     */
    @JvmSynthetic
    fun <R> andThen(mapper: suspend (T) -> Result<R, E>): RpcRequest<R, E> {
        return MappingRpcRequest(this) { result ->
            if (result.isFailure()) result else mapper(result.value)
        }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g., trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R : Result.Error> orElse(mapper: Result.Transformer<E, Result<T, R>>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.orElse(mapper) }
    }

    /**
     * Same as [orElse], but the recovery function is allowed to suspend.
     */
    @JvmSynthetic
    fun <R : Result.Error> orElse(mapper: suspend (E) -> Result<T, R>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { result ->
            if (result.isFailure()) mapper(result.error) else result
        }
    }

    /**
     * Callback called only when the call has succeeded.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun onSuccess(block: Consumer<T>): RpcRequest<T, E> {
        return MappingRpcRequest(this) { it.apply { onSuccess(block) } }
    }

    /**
     * Same as [onSuccess], but the callback is allowed to suspend.
     */
    @JvmSynthetic
    fun onSuccess(block: suspend (T) -> Unit): RpcRequest<T, E> {
        return MappingRpcRequest(this) { result ->
            if (result.isSuccess()) block(result.value)
            result
        }
    }

    /**
     * Callback called only when the call has failed with an error.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun onFailure(block: Consumer<E>): RpcRequest<T, E> {
        return MappingRpcRequest(this) { it.apply { onFailure(block) } }
    }

    /**
     * Same as [onFailure], but the callback is allowed to suspend.
     */
    @JvmSynthetic
    fun onFailure(block: suspend (E) -> Unit): RpcRequest<T, E> {
        return MappingRpcRequest(this) { result ->
            if (result.isFailure()) block(result.error)
            result
        }
    }
}

/**
 * Normal RPC request.
 */
class RpcCall<T>(
    val client: JsonRpcClient,
    val method: String,
    val params: Array<*>,
    val resultDecoder: (JsonElement) -> T,
) : RpcRequest<T, RpcError>() {
    constructor(
        client: JsonRpcClient,
        method: String,
        params: Array<*>,
        resultSerializer: KSerializer<T>,
    ) : this(client, method, params, decoderFor(resultSerializer))

    override suspend fun send(): Result<T, RpcError> = client.request(method, params, resultDecoder)

    override fun batch(batch: BatchRpcRequest): BatchRpcResponse<Result<T, RpcError>> = batch.addRpcCall(this)

    override fun toString(): String {
        return "RpcCall(method='$method', params=${params.contentToString()})"
    }
}

/**
 * RPC request which uses [mapper] function to remap RPC response.
 */
private class MappingRpcRequest<I, O, E : Result.Error, U : Result.Error>(
    private val request: RpcRequest<I, E>,
    private val mapper: suspend (Result<I, E>) -> Result<O, U>,
) : RpcRequest<O, U>() {
    override suspend fun send(): Result<O, U> = mapper(request.send())

    override fun batch(batch: BatchRpcRequest): BatchRpcResponse<Result<O, U>> {
        return request.batch(batch).map(mapper)
    }

    override fun toString(): String {
        return "MappingRpcRequest(request=$request)"
    }
}

/**
 * An [RpcRequest] that provides a [Result] via a [Supplier]. This call is not batched.
 * */
class SuppliedRpcRequest<T>(
    private val supplier: suspend () -> Result<T, RpcError>,
) : RpcRequest<T, RpcError>() {
    override suspend fun send(): Result<T, RpcError> = supplier()

    override fun batch(batch: BatchRpcRequest): BatchRpcResponse<Result<T, RpcError>> {
        return BatchRpcResponse(CoroutineScope(Dispatchers.Default).async { send() }) { true }
    }

    override fun toString(): String {
        return "SuppliedRpcRequest(supplier=$supplier)"
    }
}
