package io.ethers.core.types.transactions

import fixtures.TxDynamicFeeFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class TxDynamicFeeTest : FunSpec({
    context("initialization") {
        test("invalid chainId") {
            shouldThrow<IllegalArgumentException> {
                TxDynamicFeeFactory.create(chainId = -1L)
            }
        }
    }
})
