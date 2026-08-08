package io.ethers.abi.call

import io.ethers.core.ThrowableError
import io.ethers.core.asTypeOrNull
import io.ethers.core.types.CallRequest
import io.ethers.signers.Signer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class CallSigningErrorTest : FunSpec({
    test("IncompleteCall names the call that could not be signed") {
        val call = CallRequest().apply { gas = 21000L }
        val error = CallSigningError.IncompleteCall(call)

        error.call shouldBe call
        error.message shouldContain "missing fields required for signing"

        val exception = error.toException()
        exception.toString() shouldContain "IncompleteCall"
    }

    test("SigningFailed keeps the SigningError and chains its exception") {
        val cause = IllegalStateException("hardware wallet disconnected")
        val signingError = Signer.SigningError("Error signing transaction: tx", cause)
        val error = CallSigningError.SigningFailed(signingError)

        // the cause stays a fully typed SigningError, not a flattened exception
        error.error shouldBe signingError
        error.message shouldBe "Error signing transaction: tx"

        val exception = error.toException()
        exception.message shouldBe "Error signing transaction: tx"
        exception.cause.shouldBeInstanceOf<ThrowableError.Exception>().error shouldBe signingError
    }

    test("the two failure modes stay distinguishable through asTypeOrNull") {
        val incomplete: ThrowableError = CallSigningError.IncompleteCall(CallRequest())
        val failed: ThrowableError = CallSigningError.SigningFailed(Signer.SigningError("nope"))

        incomplete.asTypeOrNull<CallSigningError.IncompleteCall>() shouldBe incomplete
        incomplete.asTypeOrNull<CallSigningError.SigningFailed>() shouldBe null

        failed.asTypeOrNull<CallSigningError.SigningFailed>() shouldBe failed
        failed.asTypeOrNull<CallSigningError.IncompleteCall>() shouldBe null
    }
})
