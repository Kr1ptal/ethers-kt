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

@file:OptIn(ExperimentalEncodingApi::class)

package io.ethers.ens.normalize

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object EnsNormalize {
    private val nf = NF(decoder(EnsNormalizeData.NF_BASE64))
    private val ensip15 = ENSIP15(nf, decoder(EnsNormalizeData.SPEC_BASE64))

    fun normalize(name: String): String = ensip15.normalize(name)

    internal fun nfForTesting(): NF = nf

    private fun decoder(base64: String): Decoder {
        val bytes = Base64.decode(base64)
        val ints = IntArray(bytes.size / 4) { i ->
            (bytes[i * 4].toInt() and 0xFF) or
                ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24)
        }
        return Decoder(ints)
    }
}
