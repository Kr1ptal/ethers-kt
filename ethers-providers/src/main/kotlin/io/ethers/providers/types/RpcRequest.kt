package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.Result
import io.ethers.core.Result.Consumer
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Supplier

abstract class RpcRequest<T, E : Result.Error> {
    /**
     * Send RPC request and await the result by blocking calling thread.
     */
    abstract fun sendAwait(): Result<T, E>

    /**
     * Asynchronously send RPC request.
     */
    abstract fun sendAsync(): CompletableFuture<Result<T, E>>

    /**
     * Batch this into provided [BatchRpcRequest].
     */
    abstract fun batch(batch: BatchRpcRequest): CompletableFuture<Result<T, E>>

    /**
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> map(mapper: Result.Transformer<T, R>): RpcRequest<R, E> {
        return MappingRpcRequest(this) { it.map(mapper) }
    }

    /**
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Result.Error> mapError(mapper: Result.Transformer<E, R>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.mapError(mapper) }
    }

    /**
     * Call the function with response if the call was successful, skipping if it failed. Useful when
     * chaining multiple fallible operations on the result.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> andThen(mapper: Result.Transformer<T, Result<R, E>>): RpcRequest<R, E> {
        return MappingRpcRequest(this) { it.andThen(mapper) }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R : Result.Error> orElse(mapper: Result.Transformer<E, Result<T, R>>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.orElse(mapper) }
    }

    /**
     * Callback called only when the call has succeeded.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun onSuccess(block: Consumer<T>): RpcRequest<T, E> {
        return MappingRpcRequest(this) {
            it.onSuccess(block)

            it
        }
    }

    /**
     * Callback called only when the call has failed with an error.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun onFailure(block: Consumer<E>): RpcRequest<T, E> {
        return MappingRpcRequest(this) {
            it.onFailure(block)

            it
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
    val resultDecoder: Function<JsonParser, T>,
) : RpcRequest<T, RpcError>() {
    constructor(
        client: JsonRpcClient,
        method: String,
        params: Array<*>,
        resultType: Class<T>,
    ) : this(client, method, params, { p -> p.readValueAs(resultType) })

    override fun sendAwait(): Result<T, RpcError> = sendAsync().join()
    override fun sendAsync(): CompletableFuture<Result<T, RpcError>> = client.request(method, params, resultDecoder)
    override fun batch(batch: BatchRpcRequest): CompletableFuture<Result<T, RpcError>> = batch.addRpcCall(this)

    override fun toString(): String {
        return "RpcCall(method='$method', params=${params.contentToString()})"
    }
}

/**
 * RPC request which uses [mapper] function to remap RPC response.
 */
private class MappingRpcRequest<I, O, E : Result.Error, U : Result.Error>(
    private val request: RpcRequest<I, E>,
    private val mapper: Function<Result<I, E>, Result<O, U>>,
) : RpcRequest<O, U>() {
    override fun sendAwait(): Result<O, U> = sendAsync().join()

    override fun sendAsync(): CompletableFuture<Result<O, U>> = request.sendAsync().thenApplyAsync(mapper)

    override fun batch(batch: BatchRpcRequest): CompletableFuture<Result<O, U>> {
        return request.batch(batch).thenApplyAsync(mapper)
    }

    override fun toString(): String {
        return "MappingRpcRequest(request=$request)"
    }
}

/**
 * An [RpcRequest] that provides a [Result] via a [Supplier]. This call is not batched.
 * */
class SuppliedRpcRequest<T>(
    private val supplier: Supplier<Result<T, RpcError>>,
) : RpcRequest<T, RpcError>() {
    override fun sendAwait(): Result<T, RpcError> = supplier.get()

    override fun sendAsync(): CompletableFuture<Result<T, RpcError>> = CompletableFuture.supplyAsync(supplier)

    override fun batch(batch: BatchRpcRequest): CompletableFuture<Result<T, RpcError>> {
        return CompletableFuture.supplyAsync(supplier)
    }

    override fun toString(): String {
        return "SuppliedRpcRequest(supplier=$supplier)"
    }
}
