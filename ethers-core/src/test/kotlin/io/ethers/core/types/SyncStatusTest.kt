package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

class SyncStatusTest : FunSpec({
    test("SyncStatus deserialization - finished") {
        @Language("JSON")
        val jsonString = "false"
        val result = Jackson.MAPPER.readValue(jsonString, SyncStatus::class.java)

        result.isFinished shouldBe true
        result shouldBe SyncStatus.Finished
    }

    // oldestBlock is a decimal number in the old format
    test("SyncStatus deserialization - in progress (geth)") {
        @Language("JSON")
        val jsonString = """
            {
                "currentBlock": "0xeaa2b4",
                "healedBytecodeBytes": "0xaad91fe",
                "healedBytecodes": "0x61d3",
                "healedTrienodeBytes": "0x156ac02b1",
                "healedTrienodes": "0x2885aa4",
                "healingBytecode": "0x0",
                "healingTrienodes": "0x454",
                "highestBlock": "0xeaa329",
                "startingBlock": "0xea97ee",
                "syncedAccountBytes": "0xa29fec90d",
                "syncedAccounts": "0xa7ed9ad",
                "syncedBytecodeBytes": "0xdec39008",
                "syncedBytecodes": "0x8d407",
                "syncedStorage": "0x2a517da1",
                "syncedStorageBytes": "0x23634dbedf"
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, SyncStatus::class.java)

        result.isInProgress shouldBe true
        result shouldBe SyncStatus.InProgress(
            startingBlock = 15374318,
            currentBlock = 15377076,
            highestBlock = 15377193,
        )
    }
})
