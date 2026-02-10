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

internal class IntList(capacity: Int = 16) {
    var array = IntArray(capacity)
    var count = 0

    fun add(x: Int) {
        if (array.size == count) {
            array = array.copyOf(count shl 1)
        }
        array[count++] = x
    }

    fun add(xs: IntArray) {
        for (x in xs) add(x)
    }

    fun add(other: IntList) {
        for (i in 0 until other.count) add(other.array[i])
    }

    fun pop(): Int = array[--count]

    fun consume(): IntArray = if (count == array.size) array else toArray()

    fun toArray(): IntArray = array.copyOf(count)

    fun stream(): Sequence<Int> = array.asSequence().take(count)
}
