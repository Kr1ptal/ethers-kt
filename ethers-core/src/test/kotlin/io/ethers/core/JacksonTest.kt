package io.ethers.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class JacksonTest : FunSpec({
    context("ByteArray serialization") {
        withData(
            byteArrayOf() to "\"0x\"",
            byteArrayOf(0x01) to "\"0x01\"",
            byteArrayOf(0x01, 0x02, 0x03, 0x04) to "\"0x01020304\"",
            byteArrayOf(124, -12, 1, 0, 31, 21, 55, -91) to "\"0x7cf401001f1537a5\"",
        ) { (input, expected) ->
            Jackson.MAPPER.writeValueAsString(input) shouldBe expected
        }
    }
})
