package io.ethers.providers

import io.ethers.core.Kotlinx
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads just enough of a WebSocket message's top level to pick a deserializer before decoding it, without parsing the
 * message into a tree. Values are skipped by matching brackets and quotes, not validated: the decode that follows
 * validates the whole message, and a message this scanner cannot read goes through the tree route as before.
 */
internal object WsMessageScanner {
    /** Returned when the message is not one [responseId] can route. */
    const val NONE = -1L

    /**
     * The `id` of what may be a response to a single call: the first top-level `id` of the JSON object [text], read as
     * `JsonPrimitive.longOrNull` reads it, if no `method` key comes before it. Returns [NONE] for anything else,
     * including a key with escapes before the `id`, which this scanner does not compare.
     *
     * The scan stops at the `id`, so it does not see a later `method` or a repeated `id`. The caller decodes the whole
     * message anyway and must check both there.
     */
    fun responseId(text: String): Long {
        var i = skipWhitespace(text, 0)
        if (i >= text.length || text[i] != '{') return NONE
        i++

        while (true) {
            i = skipWhitespace(text, i)
            if (i >= text.length) return NONE
            if (text[i] == '}') return NONE
            if (text[i] != '"') return NONE

            val keyStart = i + 1
            val keyEnd = skipString(text, i)
            if (keyEnd < 0) return NONE
            for (k in keyStart until keyEnd) if (text[k] == '\\') return NONE

            i = skipWhitespace(text, keyEnd)
            if (i >= text.length || text[i] != ':') return NONE
            val valueStart = skipWhitespace(text, i + 1)
            i = skipValue(text, valueStart)
            if (i < 0) return NONE

            // the key is text[keyStart, keyEnd - 1), the closing quote excluded
            if (isKey(text, keyStart, keyEnd - 1, "id")) return readLong(text, valueStart, i)
            if (isKey(text, keyStart, keyEnd - 1, "method")) return NONE

            i = skipWhitespace(text, i)
            if (i >= text.length) return NONE
            when (text[i]) {
                ',' -> i++
                '}' -> return NONE
                else -> return NONE
            }
        }
    }

    private fun isKey(text: String, start: Int, end: Int, key: String): Boolean {
        return end - start == key.length && text.regionMatches(start, key, 0, key.length)
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
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return -1
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
