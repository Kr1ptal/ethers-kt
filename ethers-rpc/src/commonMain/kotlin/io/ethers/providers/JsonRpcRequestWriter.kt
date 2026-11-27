package io.ethers.providers

import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcParams
import kotlinx.serialization.json.JsonPrimitive

// The parts of a request around its id, method and params, in UTF-8. All of them are ASCII.
private val ID_PREFIX = "{\"id\":".encodeToByteArray()
private val METHOD_PREFIX = ",\"jsonrpc\":\"2.0\",\"method\":".encodeToByteArray()
private val PARAMS_PREFIX = ",\"params\":".encodeToByteArray()
private val MIN_LONG = Long.MIN_VALUE.toString().encodeToByteArray()

// The size of a request without its id, method and params: the parts above and the closing `}`.
private val REQUEST_FIXED_SIZE = ID_PREFIX.size + METHOD_PREFIX.size + PARAMS_PREFIX.size + 1

/**
 * Build a JSON-RPC request, `{"id":<id>,"jsonrpc":"2.0","method":<method>,"params":<params>}`, as the UTF-8 bytes that
 * are sent, with no whitespace, as kotlinx.serialization writes it.
 */
internal fun buildJsonRpcRequest(method: String, id: Long, params: RpcParams): ByteArray {
    return buildJsonRpcRequest(method, id, params.encodeBytes())
}

/** Build a JSON-RPC request whose params are already encoded as the UTF-8 text of a JSON array. */
internal fun buildJsonRpcRequest(method: String, id: Long, params: ByteArray): ByteArray {
    val escapedMethod = escapedJsonStringOrNull(method)
    val request = ByteArray(requestSize(method, escapedMethod, id, params))
    writeJsonRpcRequest(request, 0, method, escapedMethod, id, params)
    return request
}

/**
 * Build a JSON-RPC batch of [calls], as the UTF-8 bytes that are sent, whose params are already encoded in [params], in
 * the same order. [nextId] returns the id of the call at each index, and is called once per call in order, before any
 * of the batch is written.
 */
internal inline fun buildJsonRpcBatch(
    calls: List<RpcCall<*>>,
    params: List<ByteArray>,
    nextId: (index: Int) -> Long,
): ByteArray {
    // the ids and escaped methods are kept to write the batch into a single array of its exact size
    val ids = LongArray(calls.size)
    var escapedMethods: Array<ByteArray?>? = null
    // the brackets, and a comma between each two requests
    var size = 2 + maxOf(calls.size - 1, 0)
    for (i in calls.indices) {
        val id = nextId(i)
        ids[i] = id
        val method = calls[i].method
        val escapedMethod = escapedJsonStringOrNull(method)
        if (escapedMethod != null) {
            if (escapedMethods == null) escapedMethods = arrayOfNulls(calls.size)
            escapedMethods[i] = escapedMethod
        }
        size += requestSize(method, escapedMethod, id, params[i])
    }

    val batch = ByteArray(size)
    batch[0] = '['.code.toByte()
    var position = 1
    for (i in calls.indices) {
        if (i > 0) batch[position++] = ','.code.toByte()
        position = writeJsonRpcRequest(batch, position, calls[i].method, escapedMethods?.get(i), ids[i], params[i])
    }
    batch[position] = ']'.code.toByte()
    return batch
}

/** The size of a request, where [escapedMethod] is what [escapedJsonStringOrNull] returns for [method]. */
@PublishedApi
internal fun requestSize(method: String, escapedMethod: ByteArray?, id: Long, params: ByteArray): Int {
    val methodSize = escapedMethod?.size ?: (method.length + 2)
    return REQUEST_FIXED_SIZE + decimalSize(id) + methodSize + params.size
}

/**
 * Write a request into [out] at [offset], which must have room for [requestSize] bytes, and return the position after
 * it. [escapedMethod] is what [escapedJsonStringOrNull] returns for [method].
 */
@PublishedApi
internal fun writeJsonRpcRequest(
    out: ByteArray,
    offset: Int,
    method: String,
    escapedMethod: ByteArray?,
    id: Long,
    params: ByteArray,
): Int {
    ID_PREFIX.copyInto(out, offset)
    var position = offset + ID_PREFIX.size + decimalSize(id)
    writeDecimal(out, position, id)
    METHOD_PREFIX.copyInto(out, position)
    position += METHOD_PREFIX.size
    if (escapedMethod != null) {
        escapedMethod.copyInto(out, position)
        position += escapedMethod.size
    } else {
        // ASCII that needs no escaping: each character is its byte
        out[position++] = '"'.code.toByte()
        for (i in method.indices) {
            out[position++] = method[i].code.toByte()
        }
        out[position++] = '"'.code.toByte()
    }
    PARAMS_PREFIX.copyInto(out, position)
    position += PARAMS_PREFIX.size
    params.copyInto(out, position)
    position += params.size
    out[position++] = '}'.code.toByte()
    return position
}

/**
 * [value] as a quoted JSON string in UTF-8, escaped exactly as kotlinx.serialization escapes it, or null if it is ASCII
 * that needs no escaping, so it is written as it is.
 *
 * Method names are always such ASCII, so they are checked first. Any other string is written by kotlinx itself, which
 * keeps the escaping identical to every other string in the request.
 */
@PublishedApi
internal fun escapedJsonStringOrNull(value: String): ByteArray? {
    for (i in value.indices) {
        val c = value[i]
        if (c < ' ' || c == '"' || c == '\\' || c.code >= 0x80) {
            return JsonPrimitive(value).toString().encodeToByteArray()
        }
    }
    return null
}

/** The number of characters of [value] in decimal. */
@PublishedApi
internal fun decimalSize(value: Long): Int {
    if (value < 0) return if (value == Long.MIN_VALUE) MIN_LONG.size else 1 + decimalSize(-value)
    var size = 1
    var rest = value
    while (rest >= 10) {
        rest /= 10
        size++
    }
    return size
}

/** Write [value] in decimal into [out], ending just before [end]. */
private fun writeDecimal(out: ByteArray, end: Int, value: Long) {
    if (value == Long.MIN_VALUE) {
        MIN_LONG.copyInto(out, end - MIN_LONG.size)
        return
    }
    var position = end
    var rest = if (value < 0) -value else value
    do {
        out[--position] = ('0'.code + (rest % 10).toInt()).toByte()
        rest /= 10
    } while (rest != 0L)
    if (value < 0) out[--position] = '-'.code.toByte()
}
