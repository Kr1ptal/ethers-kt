package io.ethers.abi.error

import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class RevertErrorTest : FunSpec({
    test("decode revert error correctly") {
        data class TestCase(val message: String, val data: Bytes)
        Exhaustive.of(
            TestCase(
                "Example revert message",
                Bytes("0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000164578616d706c6520726576657274206d65737361676500000000000000000000"),
            ),
            TestCase(
                "ERR",
                Bytes("0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000034552520000000000000000000000000000000000000000000000000000000000"),
            ),
            TestCase(
                "",
                Bytes("0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000000"),
            ),
        ).checkAll { (message, data) ->
            val decoded = ContractError.getOrNull(data)

            decoded shouldBe RevertError(message)
        }
    }

    test("decoding non-revert error returns null") {
        listOf(
            // too short
            Bytes("0x3192"),
            // wrong selector
            Bytes("0x31920d0e0000000000000000000000000000000000000000000000000000000000000001"),
        ).forAll {
            RevertError.getOrNull(it) shouldBe null
        }
    }
})
