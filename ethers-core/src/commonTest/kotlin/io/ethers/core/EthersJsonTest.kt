package io.ethers.core

import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class EthersJsonTest : FunSpec({
    context("Bytes serialization") {
        withData(
            Bytes(byteArrayOf()) to "\"0x\"",
            Bytes(byteArrayOf(0x01)) to "\"0x01\"",
            Bytes(byteArrayOf(0x01, 0x02, 0x03, 0x04)) to "\"0x01020304\"",
            Bytes(byteArrayOf(124, -12, 1, 0, 31, 21, 55, -91)) to "\"0x7cf401001f1537a5\"",
        ) { (input, expected) ->
            Kotlinx.DEFAULT.encodeToString(input) shouldBe expected
        }
    }
})
