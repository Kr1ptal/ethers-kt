/*
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
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    // 58^5 is the largest power of 58 below 2^30, so a base-58^5 limb times 2^32 plus a carry fits in a Long
    private const val LIMB_DIGITS = 5
    private const val LIMB_BASE = 58L * 58 * 58 * 58 * 58
    private const val UINT_MASK = 0xFFFFFFFFL

    // 58^0..58^4, the multiplier of a leading group shorter than LIMB_DIGITS
    private val POWERS = longArrayOf(1, 58, 58 * 58, 58 * 58 * 58, 58 * 58 * 58 * 58)

    private val INDEXES = ByteArray(128) { -1 }.also { indexes ->
        for (i in ALPHABET.indices) {
            indexes[ALPHABET[i].code] = i.toByte()
        }
    }

    /**
     * Encodes the given bytes in base58. No checksum is appended.
     * */
    fun encode(input: ByteArray): String {
        if (input.isEmpty()) {
            return ""
        }

        var zeroCount = 0
        while (zeroCount < input.size && input[zeroCount].toInt() == 0) {
            ++zeroCount
        }

        // Convert the big-endian input to little-endian base-58^5 limbs, feeding 32 bits per step. The value has at
        // most 1.366 base58 digits per byte (log(256) / log(58)), hence at most 0.274 limbs per byte.
        val limbs = IntArray((input.size - zeroCount) * 274 / 1000 + 2)
        var limbCount = 0
        var i = zeroCount
        // take the odd leading bytes first, so the rest of the input splits into whole 32-bit words
        val head = (input.size - zeroCount) and 3
        if (head != 0) {
            var word = 0L
            repeat(head) { word = (word shl 8) or (input[i++].toLong() and 0xFF) }
            limbCount = shiftAdd(limbs, limbCount, 8 * head, word)
        }
        while (i < input.size) {
            val word = ((input[i].toLong() and 0xFF) shl 24) or
                ((input[i + 1].toLong() and 0xFF) shl 16) or
                ((input[i + 2].toLong() and 0xFF) shl 8) or
                (input[i + 3].toLong() and 0xFF)
            i += 4
            limbCount = shiftAdd(limbs, limbCount, 32, word)
        }

        // the most significant limb has no leading '1' padding, the others are always LIMB_DIGITS wide
        var topDigits = 0
        if (limbCount > 0) {
            var top = limbs[limbCount - 1]
            while (top != 0) {
                top /= 58
                ++topDigits
            }
        }
        val length = zeroCount + topDigits + (limbCount - 1).coerceAtLeast(0) * LIMB_DIGITS
        val output = CharArray(length)
        output.fill(ALPHABET[0], 0, zeroCount)
        var pos = length
        for (l in 0..<limbCount) {
            var limb = limbs[l]
            val digits = if (l == limbCount - 1) topDigits else LIMB_DIGITS
            repeat(digits) {
                output[--pos] = ALPHABET[limb % 58]
                limb /= 58
            }
        }
        return output.concatToString()
    }

    // limbs = limbs * 2^shift + value, returning the new limb count
    private fun shiftAdd(limbs: IntArray, limbCount: Int, shift: Int, value: Long): Int {
        var carry = value
        for (l in 0..<limbCount) {
            val acc = (limbs[l].toLong() shl shift) + carry
            carry = acc / LIMB_BASE
            limbs[l] = (acc - carry * LIMB_BASE).toInt()
        }
        var count = limbCount
        while (carry != 0L) {
            val next = carry / LIMB_BASE
            limbs[count++] = (carry - next * LIMB_BASE).toInt()
            carry = next
        }
        return count
    }

    @Throws(IllegalArgumentException::class)
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) {
            return ByteArray(0)
        }

        var zeroCount = 0
        while (zeroCount < input.length && input[zeroCount] == ALPHABET[0]) {
            ++zeroCount
        }

        // Convert to little-endian 32-bit words, feeding 5 base58 digits (one base-58^5 limb) per step. The value
        // has at most 0.733 bytes per base58 digit (log(58) / log(256)).
        val words = IntArray(((input.length - zeroCount) * 733 / 1000 + 4) / 4 + 1)
        var wordCount = 0
        var i = zeroCount
        // take the odd leading digits first, so the rest of the input splits into whole limbs
        var groupSize = (input.length - zeroCount) % LIMB_DIGITS
        if (groupSize == 0) groupSize = LIMB_DIGITS
        while (i < input.length) {
            var limb = 0L
            repeat(groupSize) {
                limb = limb * 58 + digit(input, i++)
            }
            val multiplier = if (groupSize == LIMB_DIGITS) LIMB_BASE else POWERS[groupSize]
            wordCount = mulAdd(words, wordCount, multiplier, limb)
            groupSize = LIMB_DIGITS
        }

        // the most significant word drops its leading zero bytes, the others are always 4 bytes wide
        val topBytes = if (wordCount > 0) (Int.SIZE_BITS - words[wordCount - 1].countLeadingZeroBits() + 7) / 8 else 0
        val output = ByteArray(zeroCount + topBytes + (wordCount - 1).coerceAtLeast(0) * 4)
        var pos = output.size
        for (w in 0..<wordCount) {
            var word = words[w]
            val bytes = if (w == wordCount - 1) topBytes else 4
            repeat(bytes) {
                output[--pos] = word.toByte()
                word = word ushr 8
            }
        }
        return output
    }

    // words = words * multiplier + value, returning the new word count
    private fun mulAdd(words: IntArray, wordCount: Int, multiplier: Long, value: Long): Int {
        var carry = value
        for (w in 0..<wordCount) {
            val acc = (words[w].toLong() and UINT_MASK) * multiplier + carry
            words[w] = acc.toInt()
            carry = acc ushr 32
        }
        var count = wordCount
        while (carry != 0L) {
            words[count++] = carry.toInt()
            carry = carry ushr 32
        }
        return count
    }

    private fun digit(input: String, index: Int): Int {
        val c = input[index]
        val digit = if (c.code < 128) INDEXES[c.code].toInt() else -1
        require(digit >= 0) { "Illegal character $c at $index" }
        return digit
    }
}
