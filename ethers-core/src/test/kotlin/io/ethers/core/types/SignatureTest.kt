package io.ethers.core.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class SignatureTest : FunSpec({
    context("invalid recoveryId") {
        val invalidSignature = Signature(
            v = Signature.V_EIP155_OFFSET - 1,
            r = BigInteger.ZERO,
            s = BigInteger.ZERO,
        )

        test("recoveryId() fails") {
            shouldThrow<IllegalStateException> {
                invalidSignature.recoveryId()
            }
        }

        test("recoverFromHash() fails") {
            invalidSignature.recoverFromHash(ByteArray(0)) shouldBe null
        }
    }
})
