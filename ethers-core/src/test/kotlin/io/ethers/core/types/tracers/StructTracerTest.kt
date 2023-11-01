package io.ethers.core.types.tracers

import io.ethers.core.Jackson
import io.ethers.core.Jackson.createAndInitParser
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

class StructTracerTest : FunSpec({
    val structTracer = StructTracer(
        enableMemory = true,
        disableStack = true,
        disableStorage = true,
        enableReturnData = true,
        debug = true,
        limit = 1,
        overrides = hashMapOf(
            "override_1" to "value_1",
            "override_2" to "value_2",
            "override_3" to "value_3",
        ),
    )

    test("tracer name is not supported") {
        shouldThrow<UnsupportedOperationException> {
            structTracer.name
        }
    }

    test("encode tracer") {
        Jackson.MAPPER.writeValueAsString(TracerConfig(structTracer)) shouldEqualJson """
            {
              "enableMemory": ${structTracer.enableMemory},
              "disableStack": ${structTracer.disableStack},
              "disableStorage": ${structTracer.disableStorage},
              "enableReturnData": ${structTracer.enableReturnData},
              "debug": ${structTracer.debug},
              "limit": ${structTracer.limit},
              "overrides": ${Jackson.MAPPER.writeValueAsString(structTracer.overrides)}
            }
        """
    }

    test("decodeResult(diffMode = false)") {
        @Language("JSON")
        val jsonString = """
            {
              "gas": 159625,
              "failed": false,
              "returnValue": "0x010203",
              "structLogs": [
                {
                  "pc": 7,
                  "op": "PUSH1",
                  "gas": 187764,
                  "gasCost": 3,
                  "depth": 1,
                  "error": "error",
                  "stack": [
                    "0xa0",
                    "0x40"
                  ],
                  "memory": [
                    "0xb0",
                    "0x50"
                  ],
                  "storage": {
                    "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c": "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                    "0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d": "0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"
                  },
                  "refundCounter": 10
                }
              ]
            }
        """.trimIndent()

        val jsonParser = Jackson.MAPPER.createAndInitParser(jsonString)
        val result = structTracer.decodeResult(jsonParser)

        val expectedResult = StructTracer.ExecutionResult(
            159625L,
            false,
            Bytes(byteArrayOf(0x01, 0x02, 0x03)),
            structLogs = listOf(
                StructTracer.StructLog(
                    7,
                    "PUSH1",
                    187764,
                    3,
                    1,
                    "error",
                    listOf(Bytes("0xa0"), Bytes("0x40")),
                    listOf(Bytes("0xb0"), Bytes("0x50")),
                    hashMapOf(
                        Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c") to Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                        Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d") to Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                    ),
                    10,
                ),
            ),
        )

        result shouldBe expectedResult
    }
})
