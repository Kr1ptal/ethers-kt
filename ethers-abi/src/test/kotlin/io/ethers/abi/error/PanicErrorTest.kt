package io.ethers.abi.error

import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class PanicErrorTest : FunSpec({
    test("decode panic error correctly") {
        Exhaustive.of(*PanicError.Kind.entries.toTypedArray()).checkAll { kind ->
            val encoded = PanicError.FUNCTION.encodeCall(arrayOf(kind.code))
            val decoded = ContractError.getOrNull(encoded)

            decoded shouldBe PanicError(kind)
        }
    }

    test("decoding unknown panic error returns null") {
        Arb.int(PanicError.Kind.entries.last().code.toInt() + 10..Int.MAX_VALUE).checkAll { code ->
            val encoded = PanicError.FUNCTION.encodeCall(arrayOf(code.toBigInteger()))
            val decoded = PanicError.getOrNull(encoded)

            decoded shouldBe null
        }
    }

    test("decoding non-panic error returns null") {
        listOf(
            // too short
            Bytes("0x3192"),
            // wrong selector
            Bytes("0x31920d0e0000000000000000000000000000000000000000000000000000000000000001"),
        ).forAll {
            PanicError.getOrNull(it) shouldBe null
        }
    }
})
