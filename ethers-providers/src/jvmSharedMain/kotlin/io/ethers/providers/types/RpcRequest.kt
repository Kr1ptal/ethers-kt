package io.ethers.providers.types

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

abstract class RpcRequest<T, E : Result.Error> {
    /**
     * Send the RPC request without blocking the calling thread.
     */
    abstract suspend fun send(): Result<T, E>

    /**
     * Send the RPC request and await the result by blocking the calling thread.
     */
    fun sendAwait(): Result<T, E> = runBlocking { send() }

    /**
     * Asynchronously send the RPC request as a [CompletableFuture].
     */
    fun sendAsync(): CompletableFuture<Result<T, E>> {
        return CoroutineScope(Dispatchers.Default).async { send() }.asCompletableFuture()
    }

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
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R : Result.Error> mapError(mapper: Result.Transformer<E, R>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.mapError(mapper) }
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
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g., trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R : Result.Error> orElse(mapper: Result.Transformer<E, Result<T, R>>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.orElse(mapper) }
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
     * Callback called only when the call has failed with an error.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun onFailure(block: Consumer<E>): RpcRequest<T, E> {
        return MappingRpcRequest(this) { it.apply { onFailure(block) } }
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
    @Suppress("UNCHECKED_CAST")
    constructor(
        client: JsonRpcClient,
        method: String,
        params: Array<*>,
        resultType: Class<T>,
    ) : this(client, method, params, { p -> io.ethers.core.Kotlinx.DEFAULT.decodeFromJsonElement(kotlinx.serialization.serializer(resultType), p) as T })

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
    private val mapper: (Result<I, E>) -> Result<O, U>,
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
    private val supplier: () -> Result<T, RpcError>,
) : RpcRequest<T, RpcError>() {
    override suspend fun send(): Result<T, RpcError> = supplier()

    override fun batch(batch: BatchRpcRequest): BatchRpcResponse<Result<T, RpcError>> {
        return BatchRpcResponse(CoroutineScope(Dispatchers.Default).async { send() }) { true }
    }

    override fun toString(): String {
        return "SuppliedRpcRequest(supplier=$supplier)"
    }
}
