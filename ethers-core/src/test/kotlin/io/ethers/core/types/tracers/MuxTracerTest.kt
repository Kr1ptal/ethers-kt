package io.ethers.core.types.tracers

import io.ethers.core.Jackson
import io.ethers.core.Jackson.createAndInitParser
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class MuxTracerTest : FunSpec({
    val callTracer = CallTracer(onlyTopCall = true, withLog = true)
    val fourByteTracer = FourByteTracer
    val noopTracer = NoopTracer
    val muxTracer = MuxTracer(listOf(callTracer, fourByteTracer, noopTracer))

    test("encode tracer") {
        Jackson.MAPPER.writeValueAsString(TracerConfig(muxTracer)) shouldEqualJson """
            {
              "tracer": "muxTracer",
              "tracerConfig": {
                "${callTracer.name}": {
                    "onlyTopCall": ${callTracer.onlyTopCall},
                    "withLog": ${callTracer.withLog}
                },
                "${fourByteTracer.name}": {},
                "${noopTracer.name}": {}
              }
            }
        """
    }

    context("decodeResult") {
        test("success") {
            @Language("JSON")
            val jsonString = """
                {
                  "callTracer": {
                    "type": "CALL",
                    "from": "0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5",
                    "gas": "0x6b8a",
                    "gasUsed": "0x6b87",
                    "input": "0x01",
                    "to": "0xc4356af40cc379b15925fc8c21e52c00f474e8e9",
                    "output": "0x02",
                    "error": "no_error",
                    "revertReason": "no_revert",
                    "logs": [
                      {
                        "address": "0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5",
                        "topics": [
                          "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"
                        ],
                        "data": "0x03"
                      },
                      {
                        "address": "0xc4356af40cc379b15925fc8c21e52c00f474e8e9",
                        "topics": [
                          "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"
                        ],
                        "data": "0x04"
                      }
                    ],
                    "value": "0x2964372534825c"
                  },
                  "4byteTracer": {
                    "0x0902f1ac-0": 1,
                    "0x022c0d9f-160": 1,
                    "0x3593564c-640": 1,
                    "0xd0e30db0-0": 1,
                    "0xa9059cbb-64": 2,
                    "0x70a08231-32": 5
                  },
                  "noopTracer": {}
                }
            """.trimIndent()

            val jsonParser = Jackson.MAPPER.createAndInitParser(jsonString)
            val result = muxTracer.decodeResult(jsonParser)

            val callTracerExpectedResult = CallTracer.CallFrame(
                type = "CALL",
                from = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
                gas = 27_530L,
                gasUsed = 27_527L,
                to = Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"),
                input = Bytes("0x01"),
                output = Bytes("0x02"),
                error = "no_error",
                revertReason = "no_revert",
                logs = listOf(
                    CallTracer.CallLog(
                        Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
                        listOf(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")),
                        Bytes("0x03"),
                    ),
                    CallTracer.CallLog(
                        Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"),
                        listOf(Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b")),
                        Bytes("0x04"),
                    ),
                ),
                value = BigInteger("11650662055314012"),
            )
            val fourByteTracerExpectedResult = hashMapOf(
                "0x022c0d9f-160" to 1,
                "0x0902f1ac-0" to 1,
                "0x3593564c-640" to 1,
                "0x70a08231-32" to 5,
                "0xa9059cbb-64" to 2,
                "0xd0e30db0-0" to 1,
            )

            result shouldBe MuxTracer.Result(
                muxTracer.tracers,
                arrayOf(
                    callTracerExpectedResult,
                    fourByteTracerExpectedResult,
                    Unit,
                ),
            )
        }

        test("tracer not found") {
            shouldThrow<Exception> {
                val jsonString = """{"unknown_tracer": {}}"""
                val jsonParser = Jackson.MAPPER.createAndInitParser(jsonString)
                muxTracer.decodeResult(jsonParser)
            }
        }
    }

    context("MuxTracer.Result") {
        val result = MuxTracer.Result(
            listOf(fourByteTracer),
            arrayOf(
                hashMapOf(
                    "0x022c0d9f-160" to 1,
                    "0x0902f1ac-0" to 1,
                    "0x3593564c-640" to 1,
                    "0x70a08231-32" to 5,
                    "0xa9059cbb-64" to 2,
                    "0xd0e30db0-0" to 1,
                ),
            ),
        )

        context("cast result object to expected result class") {
            withData(
                result[fourByteTracer],
                result[FourByteTracer::class.java],
            ) { r ->
                r shouldBeSameInstanceAs result.results[0]
                r shouldBe result.results[0]
            }
        }

        test("fail when casting to unknown tracer") {
            listOf({ result[NoopTracer] }, { result[NoopTracer::class.java] }).forAll { r ->
                shouldThrow<NoSuchElementException> {
                    r()
                }
            }
        }
    }

    test("fail when multiple tracers of the same type are added") {
        shouldThrow<IllegalArgumentException> {
            MuxTracer(callTracer, callTracer)
        }
    }
})
