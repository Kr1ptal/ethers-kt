package io.ethers.core.types.tracers

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.json.jackson.Jackson
import io.ethers.json.jackson.Jackson.createAndInitParser
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class CallTracerTest : FunSpec({
    val callTracer = CallTracer(onlyTopCall = true, withLog = true)

    test("encode tracer") {
        Jackson.MAPPER.writeValueAsString(TracerConfig(callTracer)) shouldEqualJson """
            {
              "tracer": "callTracer",
              "tracerConfig": {
                "onlyTopCall": ${callTracer.onlyTopCall},
                "withLog": ${callTracer.withLog}
              }
            }
        """
    }

    test("decode result") {
        @Language("JSON")
        val jsonString = """
            {
              "type": "CALL",
              "from": "0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5",
              "gas": "0x6b8a",
              "gasUsed": "0x6b87",
              "input": "0x01",
              "to": "0xc4356af40cc379b15925fc8c21e52c00f474e8e9",
              "output": "0x02",
              "error": "no_error",
              "revertReason": "no_revert",
              "calls": [
                {
                  "type": "CALL",
                  "from": "0xc4356af40cc379b15925fc8c21e52c00f474e8e9",
                  "gas": "0x5208",
                  "gasUsed": "0x5208",
                  "input": "0x00"
                }
              ],
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
              "value": "0x2964372534825c",
              "beforeEVMTransfers": [
                {
                  "purpose": "feePayment",
                  "from": "0x0000000000000000000000000000000000000000",
                  "to": null,
                  "value": "0x0"
                }
              ],
              "afterEVMTransfers": [
                {
                  "purpose": "gasRefund",
                  "from": null,
                  "to": "0x0000000000000000000000000000000000000000",
                  "value": "0x0"
                },
                {
                  "purpose": "feeCollection",
                  "from": null,
                  "to": "0xbF5041Fc07E1c866D15c749156657B8eEd0fb649",
                  "value": "0x42d1574900"
                }
              ]
            }
        """.trimIndent()

        val jsonParser = Jackson.MAPPER.createAndInitParser(jsonString)
        val result = callTracer.decodeResult(jsonParser)

        val expectedResult = CallTracer.CallFrame(
            type = "CALL",
            from = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
            gas = 27_530L,
            gasUsed = 27_527L,
            to = Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"),
            input = Bytes("0x01"),
            output = Bytes("0x02"),
            error = "no_error",
            revertReason = "no_revert",
            calls = listOf(
                CallTracer.CallFrame(
                    type = "CALL",
                    from = Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"),
                    gas = 21_000L,
                    gasUsed = 21_000L,
                    input = Bytes("0x0"),
                ),
            ),
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
            otherFields = mapOf(
                "beforeEVMTransfers" to Jackson.MAPPER.readTree("[{\"purpose\":\"feePayment\",\"from\":\"0x0000000000000000000000000000000000000000\",\"to\":null,\"value\":\"0x0\"}]"),
                "afterEVMTransfers" to Jackson.MAPPER.readTree("[{\"purpose\":\"gasRefund\",\"from\":null,\"to\":\"0x0000000000000000000000000000000000000000\",\"value\":\"0x0\"},{\"purpose\":\"feeCollection\",\"from\":null,\"to\":\"0xbF5041Fc07E1c866D15c749156657B8eEd0fb649\",\"value\":\"0x42d1574900\"}]"),
            ),
        )

        result shouldBe expectedResult
    }
})
