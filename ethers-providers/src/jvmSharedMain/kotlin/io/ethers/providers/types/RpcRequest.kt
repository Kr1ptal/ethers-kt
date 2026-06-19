package io.ethers.providers.types

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.michaelbull.result.orElse
import io.ethers.core.Kotlinx
import io.ethers.providers.AsyncExecutor
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

abstract class RpcRequest<T, E> {
    /**
     * Send the RPC request and await the result by blocking calling thread.
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
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R> map(mapper: (T) -> R): RpcRequest<R, E> {
        return MappingRpcRequest(this) { it.map(mapper) }
    }

    /**
     * Map the returned response if the call has failed with an error, skipping if it succeeded.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R> mapError(mapper: (E) -> R): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.mapError(mapper) }
    }

    /**
     * Call the function with response if the call was successful, skipping if it failed. Useful when
     * chaining multiple fallible operations on the result.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R> andThen(mapper: (T) -> Result<R, E>): RpcRequest<R, E> {
        return MappingRpcRequest(this) { it.andThen(mapper) }
    }

    /**
     * Call the function with response if the call has failed with an error, skipping if it succeeded. Useful
     * when chaining multiple fallible operations on the error (e.g., trying to recover from an error).
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun <R> orElse(mapper: (E) -> Result<T, R>): RpcRequest<T, R> {
        return MappingRpcRequest(this) { it.orElse(mapper) }
    }

    /**
     * Callback called only when the call has succeeded.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun onSuccess(block: (T) -> Unit): RpcRequest<T, E> {
        return MappingRpcRequest(this) { it.onOk(block) }
    }

    /**
     * Callback called only when the call has failed with an error.
     *
     * The function will be executed asynchronously after the request is sent and the response received.
     */
    fun onFailure(block: (E) -> Unit): RpcRequest<T, E> {
        return MappingRpcRequest(this) { it.onErr(block) }
    }

    /**
     * Recommended [Executor] for [CompletableFuture] async operations. See [io.ethers.providers.AsyncExecutor] for details.
     * */
    protected fun asyncExecutor(): Executor {
        return AsyncExecutor.maybeVirtualExecutor()
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
    ) : this(client, method, params, { p -> Kotlinx.DEFAULT.decodeFromJsonElement(serializer(resultType), p) as T })

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
private class MappingRpcRequest<I, O, E, U>(
    private val request: RpcRequest<I, E>,
    private val mapper: (Result<I, E>) -> Result<O, U>,
) : RpcRequest<O, U>() {
    override fun sendAwait(): Result<O, U> = sendAsync().join()

    override fun sendAsync(): CompletableFuture<Result<O, U>> = request.sendAsync().thenApplyAsync({ mapper(it) }, asyncExecutor())

    override fun batch(batch: BatchRpcRequest): CompletableFuture<Result<O, U>> {
        return request.batch(batch).thenApplyAsync({ mapper(it) }, asyncExecutor())
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
    override fun sendAwait(): Result<T, RpcError> = supplier()

    override fun sendAsync(): CompletableFuture<Result<T, RpcError>> = CompletableFuture.supplyAsync({ supplier() }, asyncExecutor())

    override fun batch(batch: BatchRpcRequest): CompletableFuture<Result<T, RpcError>> {
        return CompletableFuture.supplyAsync({ supplier() }, asyncExecutor())
    }

    override fun toString(): String {
        return "SuppliedRpcRequest(supplier=$supplier)"
    }
}
