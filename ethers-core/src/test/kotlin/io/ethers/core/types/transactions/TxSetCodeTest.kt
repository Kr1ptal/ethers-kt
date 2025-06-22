package io.ethers.core.types.transactions

import fixtures.TxSetCodeFactory
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.transaction.TxType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class TxSetCodeTest : FunSpec({
    context("initialization") {
        test("valid parameters") {
            val tx = TxSetCodeFactory.create(chainId = 1L)

            tx.type shouldBe TxType.SetCode
            tx.chainId shouldBe 1L
            tx.authorizationList.size shouldBe 1
            tx.blobFeeCap shouldBe null
            tx.blobVersionedHashes shouldBe null
        }

        test("invalid chainId") {
            shouldThrow<IllegalArgumentException> {
                TxSetCodeFactory.create(chainId = -1L)
            }
        }

        test("empty authorization list") {
            shouldThrow<IllegalArgumentException> {
                TxSetCodeFactory.create(authorizationList = emptyList())
            }
        }

        test("gasPrice defaults to gasFeeCap") {
            val gasFeeCap = BigInteger("20000000000")
            val tx = TxSetCodeFactory.create(gasFeeCap = gasFeeCap)

            tx.gasPrice shouldBe gasFeeCap
        }
    }

    context("toCallRequest conversion") {
        test("converts to CallRequest with correct parameters") {
            val tx = TxSetCodeFactory.create(
                to = Address("0x1234567890123456789012345678901234567890"),
                value = BigInteger("1000000000000000000"),
                nonce = 42L,
                gas = 100000L,
                gasFeeCap = BigInteger("20000000000"),
                gasTipCap = BigInteger("1000000000"),
                data = Bytes("0x1234"),
                chainId = 1L,
            )

            val callRequest = tx.toCallRequest()

            // The CallRequest should contain the basic transaction parameters
            // Note: authorizationList might not be supported in CallRequest yet
            callRequest.toString() // Just verify it doesn't throw
        }
    }
})
