package io.ethers.providers

import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcParams

// The text of a request without its method, params and id: `{"id":`, `,"jsonrpc":"2.0","method":""`, `,"params":` and
// `}`, plus room for an id of up to 19 digits.
private const val REQUEST_OVERHEAD = 64

private const val HEX_DIGITS = "0123456789abcdef"

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
 * Append [value] as a quoted JSON string, escaped exactly as kotlinx.serialization escapes it: `"` and `\` with a
 * backslash, the control characters that have a short escape with it, and the other control characters as `\u00xx`.
 * Every other character, non-ASCII included, is appended as is.
 */
internal fun StringBuilder.appendJsonString(value: String): StringBuilder {
    append('"')
    // a method name never needs escaping: find that out first, then append the whole string in one go
    for (i in value.indices) {
        if (needsEscape(value[i])) return appendEscaped(value, i).append('"')
    }
    return append(value).append('"')
}

private fun needsEscape(c: Char): Boolean = c < ' ' || c == '"' || c == '\\'

/** Append [value], whose first character that needs escaping is at [from]. */
private fun StringBuilder.appendEscaped(value: String, from: Int): StringBuilder {
    var start = 0
    for (i in from until value.length) {
        val c = value[i]
        if (!needsEscape(c)) continue

        appendRange(value, start, i)
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> append("\\u00").append(HEX_DIGITS[c.code shr 4]).append(HEX_DIGITS[c.code and 0xF])
        }
        start = i + 1
    }
    return appendRange(value, start, value.length)
}
