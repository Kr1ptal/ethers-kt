package io.ethers.ens

import io.ethers.abi.error.DecodingError
import io.ethers.core.asTypeOrNull
import io.ethers.core.types.Bytes
import io.ethers.providers.RpcError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EnsErrorTest : FunSpec({
    context("errors carrying a structured cause") {
        test("CcipCallbackFailed keeps the RpcError and chains its exception") {
            val cause = RpcError(-32000, "execution reverted", null)
            val error = EnsMiddleware.Error.CcipCallbackFailed(cause)

            // the cause stays a fully typed RpcError, not a flattened exception
            error.cause shouldBe cause
            error.cause.code shouldBe -32000

            val exception = error.toException()
            exception.message shouldBe "CCIP callback call failed"
            exception.cause?.message shouldBe cause.toException().message
        }

        test("AvatarNftCallFailed keeps the ContractError and chains its exception") {
            val cause = DecodingError(Bytes("0x1234"), "failed to decode tokenURI", null)
            val error = EnsMiddleware.Error.AvatarNftCallFailed("Error when retrieving metadata URL", cause)

            // the cause stays a fully typed ContractError, not a flattened exception
            error.cause shouldBe cause
            error.cause.asTypeOrNull<DecodingError>()?.result shouldBe Bytes("0x1234")

            val exception = error.toException()
            exception.message shouldBe "Error when retrieving metadata URL"
            exception.cause?.message shouldBe "failed to decode tokenURI"
        }
    }

    context("errors carrying a plain throwable cause") {
        test("Normalisation keeps the original throwable and chains it") {
            val cause = IllegalArgumentException("invalid label")
            val error = EnsMiddleware.Error.Normalisation(cause)

            error.cause shouldBe cause

            val exception = error.toException()
            exception.message shouldBe "Normalisation failed"
            exception.cause shouldBe cause
        }

        test("AvatarParsing chains a null cause without failing") {
            val error = EnsMiddleware.Error.AvatarParsing("Unsupported URI link", null)

            val exception = error.toException()
            exception.message shouldBe "Unsupported URI link"
            exception.cause shouldBe null
        }
    }
})
