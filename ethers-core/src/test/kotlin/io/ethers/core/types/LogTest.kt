package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

class LogTest : FunSpec({
    test("deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
              "topics": [
                "0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65",
                "0x000000000000000000000000b0bababe78a9be0810fadf99dd2ed31ed12568be"
              ],
              "data": "0x0000000000000000000000000000000000000000000000000110c4f5fd7772ba",
              "blockNumber": "0x117c277",
              "transactionHash": "0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8",
              "transactionIndex": "0xa0",
              "blockHash": "0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560",
              "logIndex": "0x10",
              "removed": true
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, Log::class.java)

        val expectedResult = Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(
                Hash("0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65"),
                Hash("0x000000000000000000000000b0bababe78a9be0810fadf99dd2ed31ed12568be"),
            ),
            data = Bytes("0x0000000000000000000000000000000000000000000000000110c4f5fd7772ba"),
            blockNumber = 18334327L,
            transactionHash = Hash("0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8"),
            transactionIndex = 160,
            blockHash = Hash("0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"),
            logIndex = 16,
            removed = true,
        )

        result shouldBe expectedResult
    }
})
