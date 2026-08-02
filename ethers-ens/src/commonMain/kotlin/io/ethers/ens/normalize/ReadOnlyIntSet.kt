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

internal class ReadOnlyIntSet private constructor(array: IntArray) : ReadOnlyIntList(array) {
    override fun contains(value: Int): Boolean = array.binarySearch(value) >= 0

    companion object {
        val EMPTY = ReadOnlyIntSet(IntArray(0))

        fun fromOwnedUnsorted(v: IntArray): ReadOnlyIntSet {
            v.sort()
            return ReadOnlyIntSet(v)
        }
    }
}
