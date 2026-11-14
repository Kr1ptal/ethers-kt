package io.ethers.core

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Base64 decoder for the standard alphabet of RFC 4648, with required padding.
 *
 * Accepts exactly what `kotlin.io.encoding.Base64.Default` accepts: no whitespace or line breaks, padding to a
 * multiple of four characters, and zero bits after the last byte. Invalid input throws [IllegalArgumentException].
 */
object FastBase64 {
    private const val PAD = '='
    private val EMPTY = ByteArray(0)

    // the 6-bit value of each alphabet character, -1 for the rest
    private val DECODE_TABLE = IntArray(256) { -1 }.also { table ->
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in alphabet.indices) table[alphabet[i].code] = i
    }

    /**
     * Decode the base64 characters of [input] between [start] (inclusive) and [end] (exclusive).
     *
     * @throws IndexOutOfBoundsException if the range is outside [input]
     * @throws IllegalArgumentException if [start] is after [end], or the characters are not valid base64
     */
    @JvmStatic
    @JvmOverloads
    fun decode(input: CharSequence, start: Int = 0, end: Int = input.length): ByteArray {
        // the same checks, in the same order, as the stdlib's range checks
        if (start < 0 || end > input.length) throw IndexOutOfBoundsException("Range [$start, $end) is outside [0, ${input.length})")
        require(start <= end) { "Start $start is after end $end" }
        val length = end - start
        if (length == 0) return EMPTY
        require(length % 4 == 0) { "Input of $length characters is not padded to a multiple of 4" }

        val padding = if (input[end - 1] != PAD) 0 else if (input[end - 2] != PAD) 1 else 2
        val output = ByteArray(length / 4 * 3 - padding)
        var out = 0
        var i = start
        // every quantum but the last decodes to 3 bytes
        val lastQuantum = end - 4
        while (i < lastQuantum) {
            val bits = (value(input, i) shl 18) or (value(input, i + 1) shl 12) or (value(input, i + 2) shl 6) or value(input, i + 3)
            output[out++] = (bits shr 16).toByte()
            output[out++] = (bits shr 8).toByte()
            output[out++] = bits.toByte()
            i += 4
        }

        val high = (value(input, i) shl 18) or (value(input, i + 1) shl 12)
        when (padding) {
            0 -> {
                val bits = high or (value(input, i + 2) shl 6) or value(input, i + 3)
                output[out++] = (bits shr 16).toByte()
                output[out++] = (bits shr 8).toByte()
                output[out] = bits.toByte()
            }
            1 -> {
                val bits = high or (value(input, i + 2) shl 6)
                require(bits and 0xff == 0) { "Non-zero pad bits before the padding at ${i + 3}" }
                output[out++] = (bits shr 16).toByte()
                output[out] = (bits shr 8).toByte()
            }
            else -> {
                require(high and 0xffff == 0) { "Non-zero pad bits before the padding at ${i + 2}" }
                output[out] = (high shr 16).toByte()
            }
        }
        return output
    }

    private fun value(input: CharSequence, index: Int): Int {
        val c = input[index]
        val value = if (c.code < 256) DECODE_TABLE[c.code] else -1
        require(value >= 0) { "Illegal base64 character '$c' at $index" }
        return value
    }
}
