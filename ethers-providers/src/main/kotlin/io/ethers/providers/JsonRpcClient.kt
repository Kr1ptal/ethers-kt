package io.ethers.providers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcResponse
import java.util.concurrent.CompletableFuture
import java.util.function.Function

interface JsonRpcClient {

    /**
     * Asynchronously execute RPC request.
     *
     * @param method RPC function name
     * @param params RPC function parameters
     * @param resultType class into which JSON result is converted
     */
    fun <T> request(
        method: String,
        params: Array<*>,
        resultType: Class<T>,
    ) = request(method, params) { p -> p.readValueAs(resultType) }

    /**
     * Asynchronously execute RPC request.
     *
     * @param method RPC function name
     * @param params RPC function parameters
     * @param resultDecoder function to convert JSON result into object return [T].
     */
    fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: Function<JsonParser, T>,
    ): CompletableFuture<RpcResponse<T>>

    /**
     * Asynchronously execute [batch] of RPC requests.
     */
    fun requestBatch(batch: BatchRpcRequest): CompletableFuture<Boolean>
}

sealed class RpcClientError : RpcResponse.Error()

data object CallTimeoutError : RpcClientError()

data class CallFailedError(val message: String, val cause: Throwable?) : RpcClientError() {
    override fun doThrow() {
        throw RuntimeException(message, cause)
    }
}

/**
 * Write JSON-RPC request directly to receiver [JsonGenerator].
 */
internal fun JsonGenerator.writeJsonRpcRequest(method: String, id: Long, params: Array<*>) {
    writeStartObject()
    writeNumberField("id", id)
    writeStringField("jsonrpc", "2.0")
    writeStringField("method", method)
    writeArrayFieldStart("params")
    for (p in params) {
        writeObject(p)
    }
    writeEndArray()
    writeEndObject()
}
