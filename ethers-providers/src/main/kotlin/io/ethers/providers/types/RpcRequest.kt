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
     * Remap the returned RPC response if the call was successful.
     */
    fun <R> map(mapper: Function<RpcResponse<T>, RpcResponse<R>>): RpcRequest<R> {
        return MappingRpcRequest(this, mapper)
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
