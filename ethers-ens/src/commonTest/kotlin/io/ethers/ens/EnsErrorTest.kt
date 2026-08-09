package io.ethers.ens

import io.ethers.abi.error.DecodingError
import io.ethers.core.ThrowableError
import io.ethers.core.asTypeOrNull
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.providers.RpcError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EnsErrorTest : FunSpec({
    context("errors carrying a structured cause") {
        test("CcipCallbackFailed keeps the RpcError and chains its exception") {
            val rpcError = RpcError(-32000, "execution reverted", null)
            val error = EnsResolver.Error.CcipCallbackFailed(rpcError)

            // the cause stays a fully typed RpcError, not a flattened exception
            error.error shouldBe rpcError
            error.error.code shouldBe -32000

            val exception = error.toException()
            exception.message shouldBe "CCIP callback call failed"
            exception.toString() shouldBe "CcipCallbackFailed: CCIP callback call failed"
            exception.cause.shouldBeInstanceOf<ThrowableError.Exception>().error shouldBe rpcError
        }

        test("AvatarNftCallFailed keeps the ContractError and chains its exception") {
            val decodingError = DecodingError(Bytes("0x1234"), "failed to decode tokenURI", null)
            val error = EnsResolver.Error.AvatarNftCallFailed("Error when retrieving metadata URL", decodingError)

            // the cause stays a fully typed ContractError, not a flattened exception
            error.error shouldBe decodingError
            error.error.asTypeOrNull<DecodingError>()?.result shouldBe Bytes("0x1234")

            val exception = error.toException()
            exception.message shouldBe "Error when retrieving metadata URL"
            exception.cause?.message shouldBe "failed to decode tokenURI"
        }
    }

    context("errors carrying a plain throwable cause") {
        test("Normalisation keeps the original throwable and chains it") {
            val cause = IllegalArgumentException("invalid label")
            val error = EnsResolver.Error.Normalisation(cause)

            error.cause shouldBe cause

            val exception = error.toException()
            exception.message shouldBe "Failed to normalise ENS name"
            exception.cause shouldBe cause
        }

        test("AvatarParsing chains a null cause without failing") {
            val error = EnsResolver.Error.AvatarParsing("Unsupported URI link", null)

            val exception = error.toException()
            exception.message shouldBe "Unsupported URI link"
            exception.cause shouldBe null
        }
    }

    context("the thrown exception stays typed") {
        test("the original error is recoverable from a catch block") {
            val error = EnsResolver.Error.UnknownEnsName(
                Address("0x0000000000000000000000000000000000000001"),
                "0xabcd",
            )

            val caught = try {
                throw error.toException()
            } catch (e: ThrowableError.Exception) {
                e
            }

            caught.error.asTypeOrNull<EnsResolver.Error.UnknownEnsName>()?.nameHash shouldBe "0xabcd"
        }
    }
})
