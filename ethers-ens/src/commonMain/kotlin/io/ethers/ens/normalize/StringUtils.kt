// Ported from https://github.com/adraffy/ens-normalize.java (MIT License)
// Copyright 2023 Andrew Raffensperger
// Permission is hereby granted, free of charge, to any person obtaining a copy of this
// software and associated documentation files (the "Software"), to deal in the Software
// without restriction, including without limitation the rights to use, copy, modify, merge,
// publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
// to whom the Software is furnished to do so, subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or
// substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.

package io.ethers.ens.normalize

internal object StringUtils {
    private const val UTF16_BMP = 0x10000
    private const val UTF16_BITS = 10
    private const val UTF16_HEAD = -1 shl UTF16_BITS // upper 6 bits
    private const val UTF16_DATA = (1 shl UTF16_BITS) - 1 // lower 10 bits
    private const val UTF16_HI = 0xD800 // 110110*
    private const val UTF16_LO = 0xDC00 // 110111*

    fun appendCodepoint(sb: StringBuilder, cp: Int) {
        if (cp < UTF16_BMP) {
            sb.append(cp.toChar())
        } else {
            val adjusted = cp - UTF16_BMP
            sb.append((UTF16_HI or ((adjusted shr UTF16_BITS) and UTF16_DATA)).toChar())
            sb.append((UTF16_LO or (adjusted and UTF16_DATA)).toChar())
        }
    }

    fun appendHex(sb: StringBuilder, cp: Int) {
        if (cp < 16) sb.append('0')
        sb.append(cp.toString(16).uppercase())
    }

    fun implode(cps: IntArray): String {
        val sb = StringBuilder(utf16Length(cps))
        for (cp in cps) appendCodepoint(sb, cp)
        return sb.toString()
    }

    fun explode(s: String): IntArray = explode(s, 0, s.length)

    fun explode(s: String, a: Int, b: Int): IntArray {
        val buf = IntList(b - a)
        var i = a
        while (i < b) {
            val ch0 = s[i++].code
            val head = ch0 and UTF16_HEAD
            if (head == UTF16_HI && i < b) {
                val ch1 = s[i].code
                if ((ch1 and UTF16_HEAD) == UTF16_LO) {
                    buf.add(UTF16_BMP + (((ch0 and UTF16_DATA) shl UTF16_BITS) or (ch1 and UTF16_DATA)))
                    i++
                    continue
                }
            }
            buf.add(ch0)
        }
        return buf.consume()
    }

    fun toHexSequence(cps: IntArray): String {
        val n = cps.size
        if (n == 0) return ""
        val sb = StringBuilder(n * 5)
        appendHex(sb, cps[0])
        for (i in 1 until n) {
            sb.append(' ')
            appendHex(sb, cps[i])
        }
        return sb.toString()
    }

    private fun utf16Length(cps: IntArray): Int {
        var n = 0
        for (cp in cps) n += if (cp < UTF16_BMP) 1 else 2
        return n
    }
}
