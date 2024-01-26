package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonParser
import io.ethers.providers.JsonRpcClient
import java.util.concurrent.CompletableFuture
import java.util.function.Function

abstract class RpcRequest<T> {
    /**
     * Send RPC request and await the result by blocking calling thread.
     */
    abstract fun sendAwait(): RpcResponse<T>

    /**
     * Asynchronously send RPC request.
     */
    abstract fun sendAsync(): CompletableFuture<RpcResponse<T>>

    /**
     * Batch this into provided [BatchRpcRequest].
     */
    abstract fun batch(batch: BatchRpcRequest): CompletableFuture<RpcResponse<T>>

    /**
     * Map the returned response if the call was successful, skipping if it failed.
     *
     * The function will be executed asynchronously after the request is sent and response received.
     */
    fun <R> map(mapper: Function<T, R>): RpcRequest<R> {
        return MappingRpcRequest(this) { response ->
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
    fun mapError(mapper: Function<RpcResponse.Error, RpcResponse.Error>): RpcRequest<T> {
        return MappingRpcRequest(this) { response ->
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
    fun <R> andThen(mapper: Function<T, RpcResponse<R>>): RpcRequest<R> {
        return MappingRpcRequest(this) { response ->
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
    fun orElse(mapper: Function<RpcResponse.Error, RpcResponse<T>>): RpcRequest<T> {
        return MappingRpcRequest(this) { response ->
            when {
                response.isError -> mapper.apply(response.error!!)
                else -> response
            }
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
) : RpcRequest<T>() {
    constructor(
        client: JsonRpcClient,
        method: String,
        params: Array<*>,
        resultType: Class<T>,
    ) : this(client, method, params, { p -> p.readValueAs(resultType) })

    override fun sendAwait(): RpcResponse<T> = sendAsync().join()
    override fun sendAsync(): CompletableFuture<RpcResponse<T>> = client.request(method, params, resultDecoder)
    override fun batch(batch: BatchRpcRequest): CompletableFuture<RpcResponse<T>> = batch.addRpcCall(this)

    override fun toString(): String {
        return "RpcCall(method='$method', params=${params.contentToString()})"
    }
}

/**
 * RPC request which uses [mapper] function to remap RPC response.
 */
private class MappingRpcRequest<I, O>(
    private val request: RpcRequest<I>,
    private val mapper: Function<RpcResponse<I>, RpcResponse<O>>,
) : RpcRequest<O>() {
    override fun sendAwait(): RpcResponse<O> = sendAsync().join()

    override fun sendAsync(): CompletableFuture<RpcResponse<O>> = request.sendAsync().thenApplyAsync(mapper)

    override fun batch(batch: BatchRpcRequest): CompletableFuture<RpcResponse<O>> {
        return request.batch(batch).thenApplyAsync(mapper)
    }

    override fun toString(): String {
        return "MappingRpcRequest(request=$request)"
    }
}
