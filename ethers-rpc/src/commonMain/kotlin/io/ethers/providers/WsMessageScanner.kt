package io.ethers.providers

import io.ethers.core.Kotlinx
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads just enough of a WebSocket message to pick a deserializer before decoding it, without parsing the message into
 * a tree. Values are skipped by matching brackets and quotes, not validated: the decode that follows validates the
 * whole message, and a message this scanner cannot read goes through the tree route as before.
 *
 * Keys are compared as written, so a key with escapes ends the scan with no answer, rather than being misread.
 */
internal object WsMessageScanner {
    /** Returned when the message is not one [responseId] can route. */
    const val NONE = -1L

    /**
     * The `id` of what may be a response to a single call: the first top-level `id` of the JSON object [text], read as
     * `JsonPrimitive.longOrNull` reads it, if no `method` key comes before it. Returns [NONE] for anything else.
     *
     * An `id` written after the `result`, as Agave and Nethermind write it, is read backwards from the end of the
     * message instead, so a large result is not skipped to reach it; it is taken on trust, whatever comes before it.
     * Either way the scan does not see all of the message: the caller decodes the whole message anyway and must check
     * there that it has no `method` and that its last `id` is this one.
     */
    fun responseId(text: String): Long {
        var id = NONE
        var resultFirst = false
        scanObject(text, skipWhitespace(text, 0)) { keyStart, keyEnd, valueStart ->
            when {
                isKey(text, keyStart, keyEnd, "id") -> {
                    id = readLong(text, valueStart)
                    true
                }
                isKey(text, keyStart, keyEnd, "method") -> true
                isKey(text, keyStart, keyEnd, "result") -> {
                    resultFirst = true
                    true
                }
                else -> false
            }
        }
        if (!resultFirst) return id

        val trailing = trailingValue(text, "id", 1)
        if (trailing != NO_RANGE) return readLong(text, rangeStart(trailing), rangeEnd(trailing))

        // no id at the end: skip the result after all
        scanObject(text, skipWhitespace(text, 0)) { keyStart, keyEnd, valueStart ->
            when {
                isKey(text, keyStart, keyEnd, "id") -> {
                    id = readLong(text, valueStart)
                    true
                }
                else -> isKey(text, keyStart, keyEnd, "method")
            }
        }
        return id
    }

    /**
     * Where a notification's `method` and `params.subscription` are, written into [out]: the first top-level `method`,
     * which must be a string without escapes, and a `subscription`. Returns false if [text] is not such a notification.
     *
     * The `subscription` is read backwards from the end of the message if it is written last, as Solana writes it,
     * after a `result` that may be large; otherwise it is the first `subscription` of the first `params` object. Either
     * way the scan does not see all of the message, so the caller decodes the whole message anyway and must check there
     * that the last `method` and `params.subscription` are the ones found here.
     */
    fun notification(text: String, out: NotificationPosition): Boolean {
        out.methodStart = -1
        out.subscriptionStart = -1
        val trailing = trailingValue(text, "subscription", 2)
        if (trailing != NO_RANGE) {
            out.subscriptionStart = rangeStart(trailing)
            out.subscriptionEnd = rangeEnd(trailing)
        }
        var paramsSeen = false
        scanObject(text, skipWhitespace(text, 0)) { keyStart, keyEnd, valueStart ->
            if (out.methodStart < 0 && isKey(text, keyStart, keyEnd, "method")) {
                // a method with escapes, or not a string, is left to the tree route
                if (text[valueStart] != '"') return false
                val valueEnd = skipString(text, valueStart)
                if (valueEnd < 0 || hasEscape(text, valueStart + 1, valueEnd - 1)) return false
                out.methodStart = valueStart + 1
                out.methodEnd = valueEnd - 1
            } else if (out.subscriptionStart < 0 && !paramsSeen && isKey(text, keyStart, keyEnd, "params")) {
                paramsSeen = true
                if (text[valueStart] != '{') return false
                scanObject(text, valueStart) { innerKeyStart, innerKeyEnd, innerValueStart ->
                    if (isKey(text, innerKeyStart, innerKeyEnd, "subscription")) {
                        val innerValueEnd = skipValue(text, innerValueStart)
                        if (innerValueEnd >= 0) {
                            out.subscriptionStart = innerValueStart
                            out.subscriptionEnd = innerValueEnd
                        }
                        true
                    } else {
                        false
                    }
                }
            }
            out.methodStart >= 0 && out.subscriptionStart >= 0
        }
        return out.methodStart >= 0 && out.subscriptionStart >= 0
    }

    /**
     * The range of a `"[key]": <value>` written last in [text], before [closingBraces] closing braces, as in
     * `..."subscription":23784}}` or `..."id":1}`, found by reading backwards: packed by [range], or [NO_RANGE]. The
     * value must be a number, a literal or a string without escapes. Finds nothing for any other ending.
     */
    private fun trailingValue(text: String, key: String, closingBraces: Int): Long {
        var i = text.length - 1
        repeat(closingBraces) {
            i = skipWhitespaceBack(text, i)
            if (i < 0 || text[i] != '}') return NO_RANGE
            i--
        }
        i = skipWhitespaceBack(text, i)
        if (i < 0) return NO_RANGE

        val valueEnd = i + 1
        val valueStart: Int
        if (text[i] == '"') {
            val open = text.lastIndexOf('"', i - 1)
            if (open < 0 || hasEscape(text, open + 1, i)) return NO_RANGE
            valueStart = open
        } else {
            while (i >= 0 && (text[i].isLetterOrDigit() || text[i] == '-' || text[i] == '+' || text[i] == '.')) i--
            valueStart = i + 1
            if (valueStart == valueEnd) return NO_RANGE
        }

        i = skipWhitespaceBack(text, valueStart - 1)
        if (i < 0 || text[i] != ':') return NO_RANGE
        i = skipWhitespaceBack(text, i - 1)
        val keyStart = i - key.length
        if (keyStart < 1 || text[i] != '"' || text[keyStart - 1] != '"' || !text.regionMatches(keyStart, key, 0, key.length)) return NO_RANGE

        return range(valueStart, valueEnd)
    }

    /** No range, from [trailingValue]. */
    private const val NO_RANGE = -1L

    private fun range(start: Int, end: Int): Long = (start.toLong() shl 32) or end.toLong()
    private fun rangeStart(range: Long): Int = (range ushr 32).toInt()
    private fun rangeEnd(range: Long): Int = range.toInt()

    private fun skipWhitespaceBack(text: String, from: Int): Int {
        var i = from
        while (i >= 0 && text[i].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) i--
        return i
    }

    /** Positions [notification] found, as `[start, end)` ranges of the message. */
    class NotificationPosition {
        /** The `method` string's content, without its quotes. */
        var methodStart = -1
        var methodEnd = -1

        /** The `subscription` value as written, with its quotes if it is a string. */
        var subscriptionStart = -1
        var subscriptionEnd = -1

        /** Whether the `method` is [method]. */
        fun isMethod(text: String, method: String): Boolean {
            return methodEnd - methodStart == method.length && text.regionMatches(methodStart, method, 0, method.length)
        }

        // a subscription's notifications all carry its id, so the last one read is usually the next one too
        private var lastToken: String? = null
        private var lastId: JsonPrimitive? = null

        /** The `subscription` as a tree reads it, or null if it is not a primitive. */
        fun subscriptionId(text: String): JsonPrimitive? {
            val length = subscriptionEnd - subscriptionStart
            val last = lastToken
            if (last != null && last.length == length && text.regionMatches(subscriptionStart, last, 0, length)) return lastId

            val token = text.substring(subscriptionStart, subscriptionEnd)
            val id = if (token[0] == '"' && !hasEscape(token, 1, token.length - 1)) {
                JsonPrimitive(token.substring(1, token.length - 1))
            } else {
                try {
                    Kotlinx.DEFAULT.parseToJsonElement(token) as? JsonPrimitive
                } catch (_: Exception) {
                    null
                }
            }
            lastToken = token
            lastId = id
            return id
        }
    }

    /**
     * Walk the keys of the object starting at [start], calling [onEntry] with each key's range, without its quotes, and
     * where its value starts, until it returns true or the object ends. A value is skipped only to reach the next key,
     * so one [onEntry] reads, or ends the walk at, is never skipped. Stops early on anything it cannot read, including a
     * key with escapes; a value [onEntry] reads may still be unterminated.
     */
    private inline fun scanObject(
        text: String,
        start: Int,
        onEntry: (keyStart: Int, keyEnd: Int, valueStart: Int) -> Boolean,
    ) {
        if (start >= text.length || text[start] != '{') return
        var i = start + 1
        while (true) {
            i = skipWhitespace(text, i)
            if (i >= text.length || text[i] != '"') return

            val keyStart = i + 1
            val keyEnd = skipString(text, i)
            if (keyEnd < 0 || hasEscape(text, keyStart, keyEnd - 1)) return

            i = skipWhitespace(text, keyEnd)
            if (i >= text.length || text[i] != ':') return
            val valueStart = skipWhitespace(text, i + 1)
            if (valueStart >= text.length) return
            if (onEntry(keyStart, keyEnd - 1, valueStart)) return

            i = skipValue(text, valueStart)
            if (i < 0) return

            i = skipWhitespace(text, i)
            if (i >= text.length || text[i] != ',') return
            i++
        }
    }

    private fun isKey(text: String, start: Int, end: Int, key: String): Boolean {
        return end - start == key.length && text.regionMatches(start, key, 0, key.length)
    }

    private fun hasEscape(text: String, start: Int, end: Int): Boolean {
        for (k in start until end) if (text[k] == '\\') return true
        return false
    }

    /** The value in [text] starting at [start] read as a `Long`, or [NONE] if it is not one or does not end. */
    private fun readLong(text: String, start: Int): Long {
        val end = skipValue(text, start)
        return if (end < 0) NONE else readLong(text, start, end)
    }

    /** The value in [text] from [start] to [end] read as a `Long`, or [NONE]. */
    private fun readLong(text: String, start: Int, end: Int): Long {
        // the common case, a plain integer of up to 18 digits, cannot overflow
        var i = start
        val negative = text[i] == '-'
        if (negative) i++
        if (i < end && end - i <= 18) {
            var value = 0L
            while (i < end && text[i] in '0'..'9') value = value * 10 + (text[i++] - '0')
            if (i == end) return if (negative) -value else value
        }

        // anything else reads exactly as the tree route reads it
        val element = try {
            Kotlinx.DEFAULT.parseToJsonElement(text.substring(start, end))
        } catch (_: Exception) {
            return NONE
        }
        return (element as? JsonPrimitive)?.longOrNull ?: NONE
    }

    private fun skipWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) i++
        return i
    }

    /** The index after the string starting with the quote at [start], or -1 if it is not closed. */
    private fun skipString(text: String, start: Int): Int {
        var i = start + 1
        while (true) {
            // indexOf is an intrinsic, so a long string such as base64 account data is skipped at memory speed
            val quote = text.indexOf('"', i)
            if (quote < 0) return -1
            // the quote is escaped if an odd number of backslashes precede it
            var backslashes = 0
            while (text[quote - 1 - backslashes] == '\\') backslashes++
            if (backslashes % 2 == 0) return quote + 1
            i = quote + 1
        }
    }

    /** The index after the value starting at [start], or -1 if it does not end. */
    private fun skipValue(text: String, start: Int): Int {
        if (start >= text.length) return -1
        return when (text[start]) {
            '"' -> skipString(text, start)
            '{', '[' -> {
                var depth = 0
                var i = start
                while (i < text.length) {
                    when (text[i]) {
                        '"' -> {
                            i = skipString(text, i)
                            if (i < 0) return -1
                            continue
                        }
                        '{', '[' -> depth++
                        '}', ']' -> if (--depth == 0) return i + 1
                    }
                    i++
                }
                -1
            }
            else -> {
                // a number or literal, or an unquoted string in lenient mode
                var i = start
                while (i < text.length && text[i] !in ",}] \n\r\t") i++
                if (i == start) -1 else i
            }
        }
    }
}
