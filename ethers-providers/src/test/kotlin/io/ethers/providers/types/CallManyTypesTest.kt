package io.ethers.providers.types

import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import org.intellij.lang.annotations.Language

class CallManyTypesTest : FunSpec({
    test("CallManyBundle serialization") {
        val bundle = CallManyBundle(
            listOf(
                CallRequest {
                    from = Address.ZERO
                    to = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
                    data = Bytes("0x70a082310000000000000000000000000d4a11d5eeaac28ec3f61d100daf4d40471f1852")
                },
                CallRequest {
                    from = Address.ZERO
                    to = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
                    data = Bytes("0x70a082310000000000000000000000000d4a11d5eeaac28ec3f61d100daf4d40471f1852")
                },
            ),
            blockOverride = BlockOverride {
                number = 1234
                time = 5678
            },
        )

        @Language("JSON")
        val expected = """
            {
              "transactions": [
                {
                  "from": "0x0000000000000000000000000000000000000000",
                  "to": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                  "input": "0x70a082310000000000000000000000000d4a11d5eeaac28ec3f61d100daf4d40471f1852"
                },
                {
                  "from": "0x0000000000000000000000000000000000000000",
                  "to": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                  "input": "0x70a082310000000000000000000000000d4a11d5eeaac28ec3f61d100daf4d40471f1852"
                }
              ],
              "blockOverride": {
                "number": "0x4d2",
                "time": "0x162e"
              }
            }
        """.trimIndent()

        Jackson.MAPPER.writeValueAsString(bundle) shouldEqualJson expected
    }

    test("CallManyContext serialization") {
        val ctx = CallManyContext(
            blockNumber = BlockId.Number(1234),
            transactionIndex = -1,
        )

        @Language("JSON")
        val expected = """
            {
              "blockNumber": "0x4d2",
              "transactionIndex": -1
            }
        """.trimIndent()

        Jackson.MAPPER.writeValueAsString(ctx) shouldEqualJson expected
    }
})
