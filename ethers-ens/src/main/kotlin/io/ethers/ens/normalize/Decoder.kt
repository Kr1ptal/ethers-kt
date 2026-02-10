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

internal class Decoder(private val buf: IntArray) {
    private var pos = 0
    private var word = 0
    private var bits = 0
    private val magic: IntArray

    init {
        magic = readMagic()
    }

    private fun nextInt(): Int = buf[pos++]

    private fun readMagic(): IntArray {
        val list = mutableListOf<Int>()
        var w = 0
        while (true) {
            val dw = readUnary()
            if (dw == 0) break
            w += dw
            list.add(w)
        }
        return list.toIntArray()
    }

    fun readBit(): Boolean {
        if (bits == 0) {
            word = nextInt()
            bits = 1
        }
        val bit = (word and bits) != 0
        bits = bits shl 1
        return bit
    }

    fun readUnary(): Int {
        var x = 0
        while (readBit()) x++
        return x
    }

    fun readBinary(w: Int): Int {
        var x = 0
        var b = 1 shl (w - 1)
        while (b != 0) {
            if (readBit()) x = x or b
            b = b shr 1
        }
        return x
    }

    fun readUnsigned(): Int {
        var a = 0
        var w: Int
        var i = 0
        while (true) {
            w = magic[i]
            val n = 1 shl w
            if (++i == magic.size || !readBit()) break
            a += n
        }
        return a + readBinary(w)
    }

    fun readSortedAscending(n: Int): IntArray = readArray(n) { prev, x -> prev + 1 + x }
    fun readUnsortedDeltas(n: Int): IntArray = readArray(n) { prev, x -> prev + asSigned(x) }

    fun readArray(count: Int, fn: (Int, Int) -> Int): IntArray {
        val v = IntArray(count)
        var prev = -1
        for (i in 0 until count) {
            prev = fn(prev, readUnsigned())
            v[i] = prev
        }
        return v
    }

    fun readUnique(): IntArray {
        val pos = readUnsigned()
        val v = readSortedAscending(pos)
        val n = readUnsigned()
        if (n > 0) {
            val vX = readSortedAscending(n)
            val vS = readUnsortedDeltas(n)
            val total = pos + vS.sum()
            val result = v.copyOf(total)
            var idx = pos
            for (i in 0 until n) {
                var x = vX[i]
                val e = x + vS[i]
                while (x < e) {
                    result[idx++] = x++
                }
            }
            return result
        }
        return v
    }

    fun <T> readTree(fn: (IntArray) -> T): ArrayList<T> {
        val ret = ArrayList<T>()
        readTree(ret, fn, IntList())
        return ret
    }

    private fun <T> readTree(ret: ArrayList<T>, fn: (IntArray) -> T, path: IntList) {
        val i = path.count
        path.add(0)
        for (x in readSortedAscending(readUnsigned())) {
            path.array[i] = x
            ret.add(fn(path.toArray()))
        }
        for (x in readSortedAscending(readUnsigned())) {
            path.array[i] = x
            readTree(ret, fn, path)
        }
        path.count = i
    }

    fun readString(): String = StringUtils.implode(readUnsortedDeltas(readUnsigned()))

    fun readSortedUnique(): IntArray {
        val v = readUnique()
        v.sort()
        return v
    }

    companion object {
        private fun asSigned(i: Int): Int = if ((i and 1) != 0) i.inv() shr 1 else i shr 1
    }
}
