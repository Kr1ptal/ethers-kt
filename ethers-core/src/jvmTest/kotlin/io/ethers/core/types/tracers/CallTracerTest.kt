package io.ethers.core.types.tracers

import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class CallTracerTest : FunSpec({
    val callTracer = CallTracer(onlyTopCall = true, withLog = true)

    val addr1 = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
    val addr2 = Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9")
    val topic1 = Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")
    val topic2 = Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b")

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

        val result = Jackson.MAPPER.readValue(jsonString, callTracer.resultType.java)

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

    context("config selection") {
        withData(
            nameFn = { (flags, _) -> "onlyTopCall=${flags.first}, withLog=${flags.second}" },
            (true to true) to mapOf("onlyTopCall" to true, "withLog" to true),
            (true to false) to mapOf("onlyTopCall" to true, "withLog" to false),
            (false to true) to mapOf("onlyTopCall" to false, "withLog" to true),
            (false to false) to mapOf("onlyTopCall" to false, "withLog" to false),
        ) { (flags, expectedConfig) ->
            CallTracer(onlyTopCall = flags.first, withLog = flags.second).config shouldBe expectedConfig
        }
    }

    context("CallFrame.isError") {
        fun frame(error: String? = null, revertReason: String? = null) = CallTracer.CallFrame(
            type = "CALL",
            from = addr1,
            gas = 21_000L,
            gasUsed = 21_000L,
            input = Bytes("0x"),
            error = error,
            revertReason = revertReason,
        )

        withData(
            nameFn = { it.first },
            "error set" to (frame(error = "execution reverted") to true),
            "revertReason set" to (frame(revertReason = "some reason") to true),
            "neither set" to (frame() to false),
        ) { (_, data) ->
            data.first.isError shouldBe data.second
        }
    }

    context("CallFrame.flatten") {
        test("single frame returns list of one") {
            val frame = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
            )
            val flat = frame.flatten()
            flat shouldHaveSize 1
            flat[0] shouldBe frame
        }

        test("nested frames are flattened parent-first") {
            val child1 = CallTracer.CallFrame(
                type = "CALL",
                from = addr2,
                gas = 10_000L,
                gasUsed = 10_000L,
                input = Bytes("0x01"),
            )
            val child2 = CallTracer.CallFrame(
                type = "CALL",
                from = addr2,
                gas = 5_000L,
                gasUsed = 5_000L,
                input = Bytes("0x02"),
            )
            val grandchild = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 1_000L,
                gasUsed = 1_000L,
                input = Bytes("0x03"),
            )
            val child1WithSub = child1.copy(calls = listOf(grandchild))
            val parent = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
                calls = listOf(child1WithSub, child2),
            )

            val flat = parent.flatten()
            flat shouldHaveSize 4
            flat[0] shouldBe parent
            flat[1] shouldBe child1WithSub
            flat[2] shouldBe grandchild
            flat[3] shouldBe child2
        }
    }

    context("CallFrame.getAllLogs") {
        test("returns empty list when no logs or calls") {
            val frame = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
            )
            frame.getAllLogs().shouldBeEmpty()
        }

        test("collects logs from child calls before parent, sorted by index") {
            val childLog = CallTracer.CallLog(addr2, listOf(topic2), Bytes("0x02"))
            val child = CallTracer.CallFrame(
                type = "CALL",
                from = addr2,
                gas = 10_000L,
                gasUsed = 10_000L,
                input = Bytes("0x01"),
                logs = listOf(childLog),
            )
            val parentLog = CallTracer.CallLog(addr1, listOf(topic1), Bytes("0x01"))
            val parent = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
                calls = listOf(child),
                logs = listOf(parentLog),
            )

            val logs = parent.getAllLogs()
            logs shouldHaveSize 2
            // child log comes first (index 0), parent log second (index 1)
            logs[0].address shouldBe addr2
            logs[1].address shouldBe addr1
        }

        test("skips logs from errored call frames") {
            val erroredLog = CallTracer.CallLog(addr2, listOf(topic2), Bytes("0x02"))
            val erroredChild = CallTracer.CallFrame(
                type = "CALL",
                from = addr2,
                gas = 10_000L,
                gasUsed = 10_000L,
                input = Bytes("0x01"),
                error = "reverted",
                logs = listOf(erroredLog),
            )
            val parentLog = CallTracer.CallLog(addr1, listOf(topic1), Bytes("0x01"))
            val parent = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
                calls = listOf(erroredChild),
                logs = listOf(parentLog),
            )

            val logs = parent.getAllLogs()
            logs shouldHaveSize 1
            logs[0].address shouldBe addr1
        }

        test("skips all logs when parent frame is errored") {
            val parentLog = CallTracer.CallLog(addr1, listOf(topic1), Bytes("0x01"))
            val parent = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
                error = "reverted",
                logs = listOf(parentLog),
            )

            parent.getAllLogs().shouldBeEmpty()
        }
    }

    context("CallFrame.getAllCallLogs") {
        test("returns empty list when no logs") {
            val frame = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
            )
            frame.getAllCallLogs().shouldBeEmpty()
        }

        test("collects CallLog instances from all frames") {
            val childLog = CallTracer.CallLog(addr2, listOf(topic2), Bytes("0x02"))
            val child = CallTracer.CallFrame(
                type = "CALL",
                from = addr2,
                gas = 10_000L,
                gasUsed = 10_000L,
                input = Bytes("0x01"),
                logs = listOf(childLog),
            )
            val parentLog = CallTracer.CallLog(addr1, listOf(topic1), Bytes("0x01"))
            val parent = CallTracer.CallFrame(
                type = "CALL",
                from = addr1,
                gas = 21_000L,
                gasUsed = 21_000L,
                input = Bytes("0x"),
                calls = listOf(child),
                logs = listOf(parentLog),
            )

            val callLogs = parent.getAllCallLogs()
            callLogs shouldHaveSize 2
            callLogs[0].address shouldBe addr2
            callLogs[1].address shouldBe addr1
        }
    }

    context("CallLog.toLog") {
        test("maps fields correctly with default logIndex") {
            val callLog = CallTracer.CallLog(addr1, listOf(topic1), Bytes("0x01"))
            val log = callLog.toLog()

            log.address shouldBe addr1
            log.topics shouldBe listOf(topic1)
            log.data shouldBe Bytes("0x01")
            log.blockHash shouldBe Hash.ZERO
            log.blockNumber shouldBe -1L
            log.transactionHash shouldBe Hash.ZERO
            log.transactionIndex shouldBe -1
            log.logIndex shouldBe -1
            log.removed shouldBe false
        }

        test("uses provided logIndex parameter") {
            val callLog = CallTracer.CallLog(addr1, listOf(topic1), Bytes("0x01"))
            val log = callLog.toLog(logIndex = 5)
            log.logIndex shouldBe 5
        }

        test("uses internal index over provided logIndex when index is set") {
            val callLog = CallTracer.CallLog(addr1, listOf(topic1), Bytes("0x01"), index = 3)
            val log = callLog.toLog(logIndex = 5)
            log.logIndex shouldBe 3
        }
    }
})
