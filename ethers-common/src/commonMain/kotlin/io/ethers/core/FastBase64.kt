package io.ethers.core

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Base64 encoder and decoder for the standard alphabet of RFC 4648, with required padding.
 *
 * [encode] produces exactly what `kotlin.io.encoding.Base64.Default` produces. [decode] accepts exactly what `kotlin.io.encoding.Base64.Default` accepts: no whitespace or line breaks, padding to a
 * multiple of four characters, and zero bits after the last byte. Invalid input throws [IllegalArgumentException].
 */
object FastBase64 {
    private const val PAD = '='
    private const val PAD_BYTE = PAD.code.toByte()
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val EMPTY = ByteArray(0)

    // the 6-bit value of each alphabet character, -1 for the rest
    private val DECODE_TABLE = IntArray(256) { -1 }.also { table ->
        for (i in ALPHABET.indices) table[ALPHABET[i].code] = i
    }

    // the two characters of each 12-bit value, the first in bits 8-15 and the second in bits 0-7
    private val ENCODE_PAIRS = IntArray(4096) { (ALPHABET[it shr 6].code shl 8) or ALPHABET[it and 63].code }

    /**
     * Encode the bytes of [input] between [start] (inclusive) and [end] (exclusive) to padded base64.
     *
     * @throws IndexOutOfBoundsException if the range is outside [input]
     * @throws IllegalArgumentException if [start] is after [end]
     */
    @JvmStatic
    @JvmOverloads
    fun encode(input: ByteArray, start: Int = 0, end: Int = input.size): String {
        if (start < 0 || end > input.size) throw IndexOutOfBoundsException("Range [$start, $end) is outside [0, ${input.size})")
        require(start <= end) { "Start $start is after end $end" }
        val length = end - start
        if (length == 0) return ""

        // ASCII bytes turn into a String without the char-to-byte compaction a CharArray would need on the JVM
        val output = ByteArray((length + 2) / 3 * 4)
        var i = start
        var out = 0
        // two 3-byte groups per iteration, each split into two 12-bit halves looked up as character pairs
        val sixEnd = end - length % 6
        while (i < sixEnd) {
            encodeGroup(input, i, output, out)
            encodeGroup(input, i + 3, output, out + 4)
            i += 6
            out += 8
        }
        if (end - i >= 3) {
            encodeGroup(input, i, output, out)
            i += 3
            out += 4
        }

        when (end - i) {
            1 -> {
                val bits = input[i].toInt() and 0xff
                val pair = ENCODE_PAIRS[bits shl 4]
                output[out] = (pair shr 8).toByte()
                output[out + 1] = pair.toByte()
                output[out + 2] = PAD_BYTE
                output[out + 3] = PAD_BYTE
            }
            2 -> {
                val bits = ((input[i].toInt() and 0xff) shl 10) or ((input[i + 1].toInt() and 0xff) shl 2)
                val pair = ENCODE_PAIRS[bits shr 6]
                output[out] = (pair shr 8).toByte()
                output[out + 1] = pair.toByte()
                output[out + 2] = ALPHABET[bits and 63].code.toByte()
                output[out + 3] = PAD_BYTE
            }
        }
        return output.decodeToString()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun encodeGroup(input: ByteArray, i: Int, output: ByteArray, out: Int) {
        val bits = ((input[i].toInt() and 0xff) shl 16) or ((input[i + 1].toInt() and 0xff) shl 8) or (input[i + 2].toInt() and 0xff)
        val high = ENCODE_PAIRS[bits ushr 12]
        val low = ENCODE_PAIRS[bits and 0xfff]
        output[out] = (high shr 8).toByte()
        output[out + 1] = high.toByte()
        output[out + 2] = (low shr 8).toByte()
        output[out + 3] = low.toByte()
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
