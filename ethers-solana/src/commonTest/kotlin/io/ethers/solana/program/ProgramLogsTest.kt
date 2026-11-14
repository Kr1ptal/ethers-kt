package io.ethers.solana.program

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

private val A = SolanaAddress(ByteArray(32) { 1 })
private val B = SolanaAddress(ByteArray(32) { 2 })
private val C = SolanaAddress(ByteArray(32) { 3 })

private fun data(vararg bytes: Int) = "Program data: " + Base64.encode(ByteArray(bytes.size) { bytes[it].toByte() })

private fun ProgramLogs.Data.summary() = listOf(programId, data, instructionIndex, logIndex, stackHeight)

class ProgramLogsTest : FunSpec({
    test("data is attributed to the program running when it was logged, and to its top-level instruction") {
        val logs = ProgramLogs.parse(
            listOf(
                "Program $A invoke [1]",
                "Program log: Instruction: Swap",
                data(1),
                "Program $B invoke [2]",
                data(2),
                "Program $B success",
                data(3),
                "Program $A consumed 100 of 200 compute units",
                "Program $A success",
                "Program $C invoke [1]",
                data(4),
                "Program $C success",
            ),
        )
        logs.data.map { it.summary() } shouldBe listOf(
            listOf(A, SolanaBytes.fromHex("01"), 0, 2, 1),
            listOf(B, SolanaBytes.fromHex("02"), 0, 4, 2),
            listOf(A, SolanaBytes.fromHex("03"), 0, 6, 1),
            listOf(C, SolanaBytes.fromHex("04"), 1, 10, 1),
        )
        logs.failure.shouldBeNull()
        logs.truncated shouldBe false
    }

    test("the first failed line names the program that raised the error, not the callers it propagated through") {
        val failure = ProgramLogs.parse(
            listOf(
                "Program $A invoke [1]",
                "Program $A success",
                "Program $A invoke [1]",
                "Program $B invoke [2]",
                "Program $B failed: custom program error: 0x1770",
                "Program $A failed: custom program error: 0x1770",
            ),
        ).failure!!
        failure.programId shouldBe B
        failure.code shouldBe 6000L
        failure.instructionIndex shouldBe 1
        failure.stackHeight shouldBe 2
    }

    test("a failure that is not a custom program error has no code") {
        ProgramLogs.parse(listOf("Program $A invoke [1]", "Program $A failed: invalid account data for instruction")).failure!!.code.shouldBeNull()
    }

    test("truncation is reported, and malformed or unattributable lines are ignored") {
        val logs = ProgramLogs.parse(
            listOf(
                data(9),
                "Program $A invoke [1]",
                "Program data: !!!",
                "Program data: AQ== Ag==",
                "Program notAnAddress invoke [1]",
                "Log truncated",
            ),
        )
        // data logged before any invocation has no program; multiple base64 fields are joined
        logs.data.map { it.summary() } shouldBe listOf(listOf(A, SolanaBytes.fromHex("0102"), 0, 3, 1))
        logs.truncated shouldBe true
    }

    test("data can be skipped, or restricted to one program, without changing attribution") {
        val lines = listOf(
            "Program $A invoke [1]",
            data(1),
            "Program $B invoke [2]",
            data(2),
            "Program $B failed: custom program error: 0xABCDEF01",
        )
        ProgramLogs.parse(lines, onlyProgram = B).data.map { it.summary() } shouldBe listOf(listOf(B, SolanaBytes.fromHex("02"), 0, 3, 2))
        val withoutData = ProgramLogs.parse(lines, collectData = false)
        withoutData.data shouldBe emptyList()
        withoutData.failure!!.code shouldBe 0xABCDEF01L
        withoutData.failure.programId shouldBe B
    }

    test("other Program lines are not mistaken for invocations") {
        val logs = ProgramLogs.parse(
            listOf(
                "Program $A invoke [1]",
                "Program log: failed: not a failure",
                "Program return: $A AQ==",
                "Program consumption: 1000 units remaining",
                "Program $A consumed 100 of 200 compute units",
                data(5),
                "Program $A success",
            ),
        )
        logs.data.single().summary() shouldBe listOf(A, SolanaBytes.fromHex("05"), 0, 5, 1)
        logs.failure.shouldBeNull()
    }

    test("a malformed error code or depth is not parsed") {
        ProgramLogs.parse(listOf("Program $A invoke [1]", "Program $A failed: custom program error: 0x123456789")).failure!!.code.shouldBeNull()
        ProgramLogs.parse(listOf("Program $A invoke [1]", "Program $A failed: custom program error: 0xz1")).failure!!.code.shouldBeNull()
        ProgramLogs.parse(listOf("Program $A invoke [1]", "Program $A failed: custom program error: 0xé1")).failure!!.code.shouldBeNull()
        // an odd number of digits has an implicit leading zero
        ProgramLogs.parse(listOf("Program $A invoke [1]", "Program $A failed: custom program error: 0x177")).failure!!.code shouldBe 0x177L
        ProgramLogs.parse(listOf("Program $A invoke [x]", data(1))).data shouldBe emptyList()
    }

    test("the stack resynchronises with the reported depth when a completion line is missing") {
        val logs = ProgramLogs.parse(
            listOf(
                "Program $A invoke [1]",
                "Program $B invoke [2]",
                // B's success line is missing
                "Program $C invoke [2]",
                data(7),
            ),
        )
        logs.data.single().summary() shouldBe listOf(C, SolanaBytes.fromHex("07"), 0, 3, 2)
    }
})
