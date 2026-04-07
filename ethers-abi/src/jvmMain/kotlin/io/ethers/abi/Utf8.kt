/*
 * SOURCE: https://github.com/google/guava/blob/master/guava/src/com/google/common/base/Utf8.java
 *
 * Copyright (C) 2013 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.ethers.abi

/**
 * Low-level, high-performance utility methods related to the [UTF-8][Charsets.UTF_8]
 * character encoding. UTF-8 is defined in section D92 of [The Unicode Standard Core
 * Specification, Chapter 3](http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf).
 *
 *
 * The variant of UTF-8 implemented by this class is the restricted definition of UTF-8
 * introduced in Unicode 3.1. One implication of this is that it rejects ["non-shortest form"](http://www.unicode.org/versions/corrigendum1.html) byte sequences,
 * even though the JDK decoder may accept them.
 *
 * @author Martin Buchholz
 * @author Clément Roux
 * @since 16.0
 */
internal object Utf8 {
    /**
     * Returns the number of bytes in the UTF-8-encoded form of `sequence`. For a string, this
     * method is equivalent to `string.getBytes(UTF_8).length`, but is more efficient in both
     * time and space.
     *
     * @throws IllegalArgumentException if `sequence` contains ill-formed UTF-16 (unpaired
     * surrogates)
     */
    fun encodedLength(sequence: CharSequence): Int {
        // Warning to maintainers: this implementation is highly optimized.
        val utf16Length = sequence.length
        var utf8Length = utf16Length
        var i = 0

        // This loop optimizes for pure ASCII.
        while (i < utf16Length && sequence[i].code < 0x80) {
            i++
        }

        // This loop optimizes for chars less than 0x800.
        while (i < utf16Length) {
            val c = sequence[i]
            if (c.code < 0x800) {
                utf8Length += 0x7f - c.code ushr 31 // branch free!
            } else {
                utf8Length += encodedLengthGeneral(sequence, i)
                break
            }
            i++
        }
        require(utf8Length >= utf16Length) {
            // Necessary and sufficient condition for overflow because of maximum 3x expansion
            "UTF-8 length does not fit in int: " + (utf8Length + (1L shl 32))
        }
        return utf8Length
    }

    private fun encodedLengthGeneral(sequence: CharSequence, start: Int): Int {
        val utf16Length = sequence.length
        var utf8Length = 0
        var i = start
        while (i < utf16Length) {
            val c = sequence[i]
            if (c.code < 0x800) {
                utf8Length += 0x7f - c.code ushr 31 // branch free!
            } else {
                utf8Length += 2
                // jdk7+: if (Character.isSurrogate(c)) {
                if (Character.MIN_SURROGATE <= c && c <= Character.MAX_SURROGATE) {
                    // Check that we have a well-formed surrogate pair.
                    require(Character.codePointAt(sequence, i) != c.code) { unpairedSurrogateMsg(i) }
                    i++
                }
            }
            i++
        }
        return utf8Length
    }

    private fun unpairedSurrogateMsg(i: Int): String {
        return "Unpaired surrogate at index $i"
    }
}
