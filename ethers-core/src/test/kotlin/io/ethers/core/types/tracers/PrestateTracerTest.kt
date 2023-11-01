package io.ethers.core.types.tracers

import io.ethers.core.Jackson
import io.ethers.core.Jackson.createAndInitParser
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class PrestateTracerTest : FunSpec({
    val prestateTracer = PrestateTracer(true)

    test("encode tracer") {
        Jackson.MAPPER.writeValueAsString(TracerConfig(prestateTracer)) shouldEqualJson """
            {
              "tracer": "prestateTracer",
              "tracerConfig": {
                "diffMode": ${prestateTracer.diffMode}
              }
            }
        """
    }

    test("decodeResult(diffMode = false)") {
        @Language("JSON")
        val jsonString = """
            {
              "0x1f9090aae28b8a3dceadf281b0f12828e676c326": {
                "nonce": 287343,
                "balance": "28b1e58026aa81fa"
              },
              "0x35a9f94af726f07b5162df7e828cc9dc8439e7d0": {
                "nonce": 5,
                "balance": "202aa8",
                "code": "0x010203"
              },
              "0xc8ba32cab1757528daf49033e3673fae77dcf05d": {
                "nonce": -1,
                "balance": "f712",
                "storage": {
                  "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c": "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                  "0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d": "0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"
                }
              }
            }
        """.trimIndent()

        val jsonParser = Jackson.MAPPER.createAndInitParser(jsonString)
        val result = PrestateTracer(false).decodeResult(jsonParser)

        val expectedResult = PrestateTracer.Result(
            hashMapOf(
                Address("0x1f9090aae28b8a3dceadf281b0f12828e676c326") to PrestateTracer.Account(
                    nonce = 287343L,
                    balance = BigInteger("28b1e58026aa81fa", 16),
                ),
                Address("0x35a9f94af726f07b5162df7e828cc9dc8439e7d0") to PrestateTracer.Account(
                    nonce = 5L,
                    balance = BigInteger("202aa8", 16),
                    code = Bytes(byteArrayOf(0x01, 0x02, 0x03)),
                ),
                Address("0xc8ba32cab1757528daf49033e3673fae77dcf05d") to PrestateTracer.Account(
                    balance = BigInteger("f712", 16),
                    storage = hashMapOf(
                        Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c") to Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                        Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d") to Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                    ),
                ),
            ),
            hashMapOf(),
        )

        result shouldBe expectedResult
    }

    test("decodeResult(diffMode = true)") {
        @Language("JSON")
        val jsonString = """
            {
              "pre": {
                "0x35a9f94af726f07b5162df7e828cc9dc8439e7d0": {
                  "nonce": 5,
                  "balance": "202aa8"
                }
              },
              "post": {
                "0x35a9f94af726f07b5162df7e828cc9dc8439e7d0": {
                  "nonce": 6
                }
              }
            }
        """.trimIndent()

        val jsonParser = Jackson.MAPPER.createAndInitParser(jsonString)
        val result = PrestateTracer(true).decodeResult(jsonParser)

        val expectedResult = PrestateTracer.Result(
            hashMapOf(
                Address("0x35a9f94af726f07b5162df7e828cc9dc8439e7d0") to PrestateTracer.Account(
                    nonce = 5,
                    balance = BigInteger("202aa8", 16),
                ),
            ),
            hashMapOf(
                Address("0x35a9f94af726f07b5162df7e828cc9dc8439e7d0") to PrestateTracer.Account(
                    nonce = 6,
                ),
            ),
        )

        result shouldBe expectedResult
    }
})
