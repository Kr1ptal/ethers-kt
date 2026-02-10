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

internal class NF(dec: Decoder) {
    val exclusions: ReadOnlyIntSet
    val quickCheck: ReadOnlyIntSet
    val decomps = HashMap<Int, IntArray>()
    val recomps = HashMap<Int, HashMap<Int, Int>>()
    val ranks = HashMap<Int, Int>()

    init {
        dec.readString() // unicodeVersion - read and discard
        exclusions = ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique())
        quickCheck = ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique())
        val decomp1 = dec.readSortedUnique()
        val decomp1A = dec.readUnsortedDeltas(decomp1.size)
        for (i in decomp1.indices) {
            decomps[decomp1[i]] = intArrayOf(decomp1A[i])
        }
        val decomp2 = dec.readSortedUnique()
        val n = decomp2.size
        val decomp2A = dec.readUnsortedDeltas(n)
        val decomp2B = dec.readUnsortedDeltas(n)
        for (i in 0 until n) {
            val cp = decomp2[i]
            val cpA = decomp2A[i]
            val cpB = decomp2B[i]
            decomps[cp] = intArrayOf(cpB, cpA) // reversed
            if (!exclusions.contains(cp)) {
                val recomp = recomps.getOrPut(cpA) { HashMap() }
                recomp[cpB] = cp
            }
        }
        var rank = 0
        while (true) {
            rank += 1 shl SHIFT
            val v = dec.readUnique()
            if (v.isEmpty()) break
            for (cp in v) {
                ranks[cp] = rank
            }
        }
    }

    private fun composePair(a: Int, b: Int): Int {
        if (a in L0 until L1 && b in V0 until V1) {
            return S0 + (a - L0) * N_COUNT + (b - V0) * T_COUNT
        } else if (isHangul(a) && b > T0 && b < T1 && (a - S0) % T_COUNT == 0) {
            return a + (b - T0)
        } else {
            val map = recomps[a]
            if (map != null) {
                val boxed = map[b]
                if (boxed != null) return boxed
            }
            return NONE
        }
    }

    private inner class Packer {
        val buf = IntList()
        var check = false

        fun add(cp: Int) {
            var packed = cp
            val cc = ranks.getOrDefault(cp, 0)
            if (cc != 0) {
                check = true
                packed = packed or cc
            }
            buf.add(packed)
        }

        fun fixOrder() {
            if (!check) return
            val v = buf.array
            var prev = unpackCC(v[0])
            for (i in 1 until buf.count) {
                val cc = unpackCC(v[i])
                if (cc == 0 || prev <= cc) {
                    prev = cc
                    continue
                }
                var j = i - 1
                while (true) {
                    val temp = v[j]
                    v[j] = v[j + 1]
                    v[j + 1] = temp
                    if (j == 0) break
                    prev = unpackCC(v[--j])
                    if (prev <= cc) break
                }
                prev = unpackCC(v[i])
            }
        }
    }

    fun decomposed(cps: IntArray): IntArray {
        val p = Packer()
        val tmpBuf = IntList()
        for (cp0 in cps) {
            var cp = cp0
            while (true) {
                if (cp < 0x80) {
                    p.buf.add(cp)
                } else if (isHangul(cp)) {
                    val sIndex = cp - S0
                    val lIndex = sIndex / N_COUNT
                    val vIndex = (sIndex % N_COUNT) / T_COUNT
                    val tIndex = sIndex % T_COUNT
                    p.add(L0 + lIndex)
                    p.add(V0 + vIndex)
                    if (tIndex > 0) p.add(T0 + tIndex)
                } else {
                    val decomp = decomps[cp]
                    if (decomp != null) {
                        for (x in decomp) tmpBuf.add(x)
                    } else {
                        p.add(cp)
                    }
                }
                if (tmpBuf.count == 0) break
                cp = tmpBuf.pop()
            }
        }
        p.fixOrder()
        return p.buf.consume()
    }

    private fun composedFromPacked(packed: IntArray): IntArray {
        val cps = IntList()
        val stack = IntList()
        var prevCp = NONE
        var prevCc = 0
        for (p in packed) {
            val cc = unpackCC(p)
            val cp = unpackCP(p)
            if (prevCp == NONE) {
                if (cc == 0) {
                    prevCp = cp
                } else {
                    cps.add(cp)
                }
            } else if (prevCc > 0 && prevCc >= cc) {
                if (cc == 0) {
                    cps.add(prevCp)
                    cps.add(stack)
                    stack.count = 0
                    prevCp = cp
                } else {
                    stack.add(cp)
                }
                prevCc = cc
            } else {
                val composed = composePair(prevCp, cp)
                if (composed != NONE) {
                    prevCp = composed
                } else if (prevCc == 0 && cc == 0) {
                    cps.add(prevCp)
                    prevCp = cp
                } else {
                    stack.add(cp)
                    prevCc = cc
                }
            }
        }
        if (prevCp != NONE) {
            cps.add(prevCp)
            cps.add(stack)
        }
        return cps.consume()
    }

    @Suppress("ktlint:standard:function-naming")
    fun NFD(vararg cps: Int): IntArray {
        val v = decomposed(cps)
        for (i in v.indices) {
            v[i] = unpackCP(v[i])
        }
        return v
    }

    @Suppress("ktlint:standard:function-naming")
    fun NFC(vararg cps: Int): IntArray = composedFromPacked(decomposed(cps))

    companion object {
        private const val SHIFT = 24
        private const val MASK = (1 shl SHIFT) - 1
        private const val NONE = -1

        private const val S0 = 0xAC00
        private const val L0 = 0x1100
        private const val V0 = 0x1161
        private const val T0 = 0x11A7
        private const val L_COUNT = 19
        private const val V_COUNT = 21
        private const val T_COUNT = 28
        private const val N_COUNT = V_COUNT * T_COUNT
        private const val S_COUNT = L_COUNT * N_COUNT
        private const val S1 = S0 + S_COUNT
        private const val L1 = L0 + L_COUNT
        private const val V1 = V0 + V_COUNT
        private const val T1 = T0 + T_COUNT

        private fun isHangul(cp: Int): Boolean = cp in S0 until S1
        fun unpackCC(packed: Int): Int = packed shr SHIFT
        fun unpackCP(packed: Int): Int = packed and MASK
    }
}
