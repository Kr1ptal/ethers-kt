package io.ethers.core.types.transactions

import fixtures.TxAccessListFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class TxAccessListTest : FunSpec({
    context("initialization") {
        test("invalid chainId") {
            shouldThrow<IllegalArgumentException> {
                TxAccessListFactory.create(chainId = -1L)
            }
        }
    }
})
