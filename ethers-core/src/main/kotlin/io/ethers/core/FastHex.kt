/*
   Copyright 2019 Evan Saulpaugh
   Modifications by Kr1ptal

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package io.ethers.core

import java.math.BigInteger

/**
 * Hexadecimal codec with unsafe encoding/decoding support. Produces invalid results if input is not valid hex.
 */
object FastHex {
    private const val CHARS_PER_BYTE = 2
    private const val BITS_PER_CHAR = java.lang.Byte.SIZE / CHARS_PER_BYTE
    private const val BYTE_0 = '0'.code.toByte()
    private const val BYTE_X = 'x'.code.toByte()
    private val EMPTY_BYTES = ByteArray(0)

    // values index directly into the encoding/decoding tables
    private val ENCODE_TABLE = IntArray(256) { -1 }
    private val DECODE_TABLE_UPPER = ByteArray(256) { -1 }
    private val DECODE_TABLE_LOWER = ByteArray(256) { -1 }

    init {
        val chars = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
        for (i in ENCODE_TABLE.indices) {
            val upper = chars[i and 0xF0 ushr BITS_PER_CHAR]
            val lower = chars[i and 0x0F]
            ENCODE_TABLE[i] = (upper.code shl java.lang.Byte.SIZE or lower.code)
        }
        for (i in DECODE_TABLE_UPPER.indices) {
            DECODE_TABLE_UPPER[i] = getNibble(i.toChar(), true)
            DECODE_TABLE_LOWER[i] = getNibble(i.toChar(), false)
        }
    }

    /**
     * Encode [Int] as hex string prefixed with '0x', skipping the leading zero.
     * */
    @JvmStatic
    fun encodeWithPrefix(value: Int): String {
        if (value == 0) return "0x0"
        return encodeNumberWithPrefix(Int.SIZE_BITS) { (value shr it and 0xff) }
    }

    /**
     * Encode [Long] as hex string prefixed with '0x', skipping the leading zero.
     * */
    @JvmStatic
    fun encodeWithPrefix(value: Long): String {
        if (value == 0L) return "0x0"
        return encodeNumberWithPrefix(Long.SIZE_BITS) { (value shr it and 0xff).toInt() }
    }

    /**
     * Encode [BigInteger] as hex string prefixed with '0x', skipping the leading zero.
     * */
    @JvmStatic
    fun encodeWithPrefix(value: BigInteger): String {
        if (value == BigInteger.ZERO) return "0x0"
        var byteIdx = 0
        val arr = value.toByteArray()
        return encodeNumberWithPrefix(arr.size * 8) { arr[byteIdx++].toInt() and 0xff }
    }

    /**
     * Encode [buffer] to hex string prefixed with '0x'.
     * */
    @JvmStatic
    fun encodeWithPrefix(buffer: ByteArray): String {
        return encodeWithPrefix(buffer, 0, buffer.size)
    }

    /**
     * Encode [buffer] range to hex string prefixed with '0x'.
     * */
    @JvmStatic
    fun encodeWithPrefix(buffer: ByteArray, offset: Int, len: Int): String {
        return String(encodeToAsciiBytes(buffer, offset, len, true), Charsets.US_ASCII)
    }

    /**
     * Encode [buffer] to hex string without '0x' prefix.
     * */
    @JvmStatic
    fun encodeWithoutPrefix(buffer: ByteArray): String {
        return encodeWithoutPrefix(buffer, 0, buffer.size)
    }

    /**
     * Encode [buffer] range to hex string without '0x' prefix.
     * */
    @JvmStatic
    fun encodeWithoutPrefix(buffer: ByteArray, offset: Int, len: Int): String {
        return String(encodeToAsciiBytes(buffer, offset, len, false), Charsets.US_ASCII)
    }

    /**
     * Encode [buffer] into array of ASCII bytes.
     * */
    @JvmStatic
    @JvmOverloads
    fun encodeAsBytes(buffer: ByteArray, withPrefix: Boolean = false): ByteArray {
        return encodeToAsciiBytes(buffer, 0, buffer.size, withPrefix)
    }

    /**
     * Encode numeric bytes as hex string prefixed with '0x'. Leading zeros of first upper nibble are skipped (e.g.
     * '0x01' becomes just '0x1').
     * */
    private inline fun encodeNumberWithPrefix(bitSize: Int, getNextByte: (bits: Int) -> Int): String {
        var idx = 2
        var bytes: ByteArray? = null
        var bits = bitSize - 8
        while (bits >= 0) {
            val byte = getNextByte(bits)
            if (bytes != null || byte > 0) {
                val nibbles = ENCODE_TABLE[byte]
                val upper = (nibbles ushr java.lang.Byte.SIZE).toByte()
                val lower = (nibbles and 0xff).toByte()

                if (bytes == null) {
                    val upperIsZero = upper == BYTE_0
                    val size = ((bits / 8 + 1) * 2) + 2 - if (upperIsZero) 1 else 0

                    bytes = ByteArray(size)
                    bytes[0] = BYTE_0
                    bytes[1] = BYTE_X

                    // skip leading zero
                    if (!upperIsZero) bytes[idx++] = upper
                    bytes[idx++] = lower
                } else {
                    bytes[idx++] = upper
                    bytes[idx++] = lower
                }
            }

            bits -= 8
        }

        return String(bytes!!)
    }

    private fun encodeToAsciiBytes(buffer: ByteArray, offset: Int, len: Int, withPrefix: Boolean): ByteArray {
        var currOffset = offset
        val end = currOffset + len
        var i = 0
        val bytes = if (withPrefix) {
            i += 2

            ByteArray(len * CHARS_PER_BYTE + 2).also {
                it[0] = BYTE_0
                it[1] = BYTE_X
            }
        } else {
            ByteArray(len * CHARS_PER_BYTE)
        }

        while (currOffset < end) {
            val hexPair = ENCODE_TABLE[buffer[currOffset].toInt() and 0xFF]

            // upper
            bytes[i] = (hexPair ushr java.lang.Byte.SIZE).toByte()

            // lower
            bytes[i + 1] = hexPair.toByte()

            currOffset++
            i += CHARS_PER_BYTE
        }
        return bytes
    }

    /**
     * Decode [hex] string into array of bytes. Supports both inputs - with and without '0x' prefix,
     * handles uneven length by implicitly adding a leading zero.
     * */
    @JvmStatic
    fun decode(hex: CharSequence): ByteArray {
        var destIndex = 0
        var currOffset = 0

        if (hex.length >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X')) {
            currOffset = 2
        }

        if (hex.length == currOffset) {
            return EMPTY_BYTES
        }

        val dest: ByteArray
        if (!isDivisibleBy2(hex.length)) {
            dest = ByteArray((hex.length + 1 - currOffset) / CHARS_PER_BYTE)

            // decode the first nibble, which contains only lower bits, implicitly adding a leading zero, e.g. "f" -> "0f"
            dest[destIndex++] = DECODE_TABLE_LOWER[hex[currOffset++].code]
        } else {
            dest = ByteArray((hex.length - currOffset) / CHARS_PER_BYTE)
        }

        while (destIndex < dest.size) {
            dest[destIndex++] = decodeHexByte(currOffset) { index -> hex[index].code }
            currOffset += CHARS_PER_BYTE
        }

        return dest
    }

    /**
     * Decode [hex] bytes into array of bytes. Supports both inputs - with and without '0x' prefix,
     * handles uneven length by implicitly adding a leading zero.
     * */
    @JvmStatic
    fun decode(hex: ByteArray, offset: Int = 0, length: Int = hex.size - offset): ByteArray {
        var destIndex = 0
        var currOffset = offset
        var currLength = length

        if (currLength >= 2 && hex[currOffset] == BYTE_0 && hex[currOffset + 1] == BYTE_X) {
            currOffset += 2
            currLength -= 2
        }

        if (currLength == 0) {
            return EMPTY_BYTES
        }

        val dest: ByteArray
        if (!isDivisibleBy2(currLength)) {
            dest = ByteArray((currLength + 1) / CHARS_PER_BYTE)

            // decode the first nibble, which contains only lower bits, implicitly adding a leading zero, e.g. "f" -> "0f"
            dest[destIndex++] = DECODE_TABLE_LOWER[hex[currOffset++].toInt()]
        } else {
            dest = ByteArray(currLength / CHARS_PER_BYTE)
        }

        while (destIndex < dest.size) {
            dest[destIndex++] = decodeHexByte(currOffset) { index -> hex[index].toInt() }
            currOffset += CHARS_PER_BYTE
        }

        return dest
    }

    /**
     * Decode [hex] chars into array of bytes. Supports both inputs - with and without '0x' prefix,
     * handles uneven length by implicitly adding a leading zero.
     * */
    @JvmStatic
    fun decode(hex: CharArray, offset: Int = 0, length: Int = hex.size - offset): ByteArray {
        var destIndex = 0
        var currOffset = offset
        var currLength = length

        if (currLength >= 2 && hex[currOffset] == '0' && (hex[currOffset + 1] == 'x' || hex[currOffset + 1] == 'X')) {
            currOffset += 2
            currLength -= 2
        }

        if (currLength == 0) {
            return EMPTY_BYTES
        }

        val dest: ByteArray
        if (!isDivisibleBy2(currLength)) {
            dest = ByteArray((currLength + 1) / CHARS_PER_BYTE)

            // decode the first nibble, which contains only lower bits, implicitly adding a leading zero, e.g. "f" -> "0f"
            dest[destIndex++] = DECODE_TABLE_LOWER[hex[currOffset++].code]
        } else {
            dest = ByteArray(currLength / CHARS_PER_BYTE)
        }

        while (destIndex < dest.size) {
            dest[destIndex++] = decodeHexByte(currOffset) { index -> hex[index].code }
            currOffset += CHARS_PER_BYTE
        }

        return dest
    }

    /**
     * Check if given [CharSequence] is a valid sequence of hex numbers. Supports both inputs - with
     * and without '0x' prefix.
     */
    @JvmStatic
    fun isValidHex(hex: CharSequence): Boolean {
        var startIndex = 0
        if (hex.length >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X')) {
            startIndex = 2
        }

        for (i in startIndex..<hex.length) {
            if (!isValidHexChar(hex[i])) {
                return false
            }
        }

        return hex.isNotEmpty()
    }

    private inline fun decodeHexByte(offset: Int, charCodeAtOffset: (Int) -> Int): Byte {
        return (DECODE_TABLE_UPPER[charCodeAtOffset(offset)].toInt() or DECODE_TABLE_LOWER[charCodeAtOffset(offset + 1)].toInt()).toByte()
    }

    private fun getNibble(c: Char, upper: Boolean): Byte {
        val nibble = when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'F' -> c - ('A' - 0xA)
            in 'a'..'f' -> c - ('a' - 0xa)
            else -> -1
        }

        if (nibble == -1) {
            return -1
        }

        return if (upper) (nibble shl BITS_PER_CHAR).toByte() else nibble.toByte()
    }

    private fun isValidHexChar(c: Char): Boolean {
        return when (c) {
            in '0'..'9' -> true
            in 'A'..'F' -> true
            in 'a'..'f' -> true
            else -> false
        }
    }

    /**
     * For explanation see: [Bitwise divisibility](https://stackoverflow.com/a/33085782)
     */
    private fun isDivisibleBy2(num: Int): Boolean {
        return num and 1 == 0
    }
}
