/**
 * Copyright 2011 Google Inc.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ethers.crypto

import org.bouncycastle.jcajce.provider.digest.SHA256
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 *
 * Base58 is a way to encode Bitcoin addresses as numbers and letters. Note that this is not the same base58 as used by
 * Flickr, which you may see reference to around the internet.
 *
 * You may instead wish to work with VersionedChecksummedBytes, which adds support for testing the prefix
 * and suffix bytes commonly found in addresses.
 *
 * Satoshi says:
 * ```text
 * Why base-58 instead of standard base-64 encoding?
 *
 * Don't want 0OIl characters that look the same in some fonts and
 * could be used to create visually identical looking account numbers.
 * A string with non-alphanumeric characters is not as easily accepted as an account number.
 * E-mail usually won't line-break if there's no punctuation to break at.
 * Doubleclicking selects the whole number as one word if it's all alphanumeric.
 * ```
 */
object Base58 {
    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val INDEXES = IntArray(128)

    init {
        for (i in INDEXES.indices) {
            INDEXES[i] = -1
        }
        for (i in ALPHABET.indices) {
            INDEXES[ALPHABET[i].code] = i
        }
    }

    /**
     * Encodes the given bytes in base58. No checksum is appended.
     * */
    fun encode(input: ByteArray): String {
        @Suppress("NAME_SHADOWING")
        var input = input
        if (input.isEmpty()) {
            return ""
        }
        input = copyOfRange(input, 0, input.size)
        // Count leading zeroes.
        var zeroCount = 0
        while (zeroCount < input.size && input[zeroCount].toInt() == 0) {
            ++zeroCount
        }
        // The actual encoding.
        val temp = ByteArray(input.size * 2)
        var j = temp.size
        var startAt = zeroCount
        while (startAt < input.size) {
            val mod = divmod58(input, startAt)
            if (input[startAt].toInt() == 0) {
                ++startAt
            }
            temp[--j] = ALPHABET[mod.toInt()].code.toByte()
        }

        // Strip extra '1' if there are some after decoding.
        while (j < temp.size && temp[j] == ALPHABET[0].code.toByte()) {
            ++j
        }
        // Add as many leading '1' as there were leading zeros.
        while (--zeroCount >= 0) {
            temp[--j] = ALPHABET[0].code.toByte()
        }
        val output = copyOfRange(temp, j, temp.size)
        return String(output, StandardCharsets.US_ASCII)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) {
            return ByteArray(0)
        }
        val input58 = ByteArray(input.length)
        // Transform the String to a base58 byte sequence
        for (i in input.indices) {
            val c = input[i]
            var digit58 = -1
            if (c.code in 0..127) {
                digit58 = INDEXES[c.code]
            }
            require(digit58 >= 0) { "Illegal character $c at $i" }
            input58[i] = digit58.toByte()
        }
        // Count leading zeroes
        var zeroCount = 0
        while (zeroCount < input58.size && input58[zeroCount].toInt() == 0) {
            ++zeroCount
        }
        // The encoding
        val temp = ByteArray(input.length)
        var j = temp.size
        var startAt = zeroCount
        while (startAt < input58.size) {
            val mod = divmod256(input58, startAt)
            if (input58[startAt].toInt() == 0) {
                ++startAt
            }
            temp[--j] = mod
        }
        // Do no add extra leading zeroes, move j to first non null byte.
        while (j < temp.size && temp[j].toInt() == 0) {
            ++j
        }
        return copyOfRange(temp, j - zeroCount, temp.size)
    }

    fun decodeToBigInteger(input: String): BigInteger {
        return BigInteger(1, decode(input))
    }

    /**
     * Uses the checksum in the last 4 bytes of the decoded data to verify the rest are correct. The checksum is
     * removed from the returned data.
     *
     * @throws RuntimeException if the input is not base 58 or the checksum does not validate.
     */
    fun decodeChecked(input: String): ByteArray {
        var tmp = decode(input)
        require(tmp.size >= 4) { "Input to short" }
        val bytes = copyOfRange(tmp, 0, tmp.size - 4)
        val checksum = copyOfRange(tmp, tmp.size - 4, tmp.size)
        tmp = doubleDigest(bytes)
        val hash = copyOfRange(tmp, 0, 4)
        if (!checksum.contentEquals(hash)) throw RuntimeException("Checksum does not validate")
        return bytes
    }

    /**
     * Calculates the SHA-256 hash of the given byte range, and then hashes the resulting hash again. This is
     * standard procedure in Bitcoin. The resulting hash is in big endian form.
     */
    private fun doubleDigest(input: ByteArray): ByteArray {
        val digest = SHA256.Digest()
        digest.reset()
        digest.update(input)
        val first = digest.digest()
        return digest.digest(first)
    }

    // number -> number / 58, returns number % 58
    private fun divmod58(number: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt..<number.size) {
            val digit256 = number[i].toInt() and 0xFF
            val temp = remainder * 256 + digit256
            number[i] = (temp / 58).toByte()
            remainder = temp % 58
        }
        return remainder.toByte()
    }

    // number -> number / 256, returns number % 256
    private fun divmod256(number58: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt..<number58.size) {
            val digit58 = number58[i].toInt() and 0xFF
            val temp = remainder * 58 + digit58
            number58[i] = (temp / 256).toByte()
            remainder = temp % 256
        }
        return remainder.toByte()
    }

    private fun copyOfRange(source: ByteArray, from: Int, to: Int): ByteArray {
        val range = ByteArray(to - from)
        System.arraycopy(source, from, range, 0, range.size)
        return range
    }
}
