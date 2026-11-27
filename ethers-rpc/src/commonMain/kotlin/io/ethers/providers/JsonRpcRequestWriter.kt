package io.ethers.providers

import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcParams
import kotlinx.serialization.json.JsonPrimitive

// The text of a request without its method, params and id: `{"id":`, `,"jsonrpc":"2.0","method":""`, `,"params":` and
// `}`, plus room for an id of up to 19 digits.
private const val REQUEST_OVERHEAD = 64

/** Build the text of a JSON-RPC request. */
internal fun buildJsonRpcRequest(method: String, id: Long, params: RpcParams): String {
    return buildJsonRpcRequest(method, id, params.encode())
}

/** Build the text of a JSON-RPC request whose params are already encoded as the text of a JSON array. */
internal fun buildJsonRpcRequest(method: String, id: Long, params: String): String {
    return StringBuilder(REQUEST_OVERHEAD + method.length + params.length)
        .appendJsonRpcRequest(method, id, params)
        .toString()
}

/**
 * Build the text of a JSON-RPC batch of [calls], whose params are already encoded in [params], in the same order.
 * [nextId] returns the id of the call at each index, and is called once per call in order.
 */
internal inline fun buildJsonRpcBatch(
    calls: List<RpcCall<*>>,
    params: List<String>,
    nextId: (index: Int) -> Long,
): String {
    var capacity = 2
    for (i in calls.indices) {
        capacity += REQUEST_OVERHEAD + 1 + calls[i].method.length + params[i].length
    }

    val sb = StringBuilder(capacity)
    sb.append('[')
    for (i in calls.indices) {
        if (i > 0) sb.append(',')
        sb.appendJsonRpcRequest(calls[i].method, nextId(i), params[i])
    }
    return sb.append(']').toString()
}

/**
 * Append a JSON-RPC request, `{"id":<id>,"jsonrpc":"2.0","method":<method>,"params":<params>}`, with no whitespace,
 * as kotlinx.serialization writes it.
 */
internal fun StringBuilder.appendJsonRpcRequest(method: String, id: Long, params: String): StringBuilder {
    append("{\"id\":").append(id).append(",\"jsonrpc\":\"2.0\",\"method\":")
    appendJsonString(method)
    return append(",\"params\":").append(params).append('}')
}

/**
 * Append [value] as a quoted JSON string, escaped exactly as kotlinx.serialization escapes it.
 *
 * Method names never need escaping, so they are checked first and appended as they are. A string that does need it is
 * written by kotlinx itself, which keeps the escaping identical to every other string in the request.
 */
internal fun StringBuilder.appendJsonString(value: String): StringBuilder {
    for (i in value.indices) {
        val c = value[i]
        if (c < ' ' || c == '"' || c == '\\') return append(JsonPrimitive(value).toString())
    }
    return append('"').append(value).append('"')
}
