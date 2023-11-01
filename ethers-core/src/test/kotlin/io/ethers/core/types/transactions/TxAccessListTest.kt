package io.ethers.core.types.transactions

import fixtures.TxAccessListFactory
import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger

class TxAccessListTest : FunSpec({

    context("initialization") {
        test("invalid chainId") {
            shouldThrow<IllegalArgumentException> {
                TxAccessListFactory.create(chainId = -1L)
            }
        }
    }

    context("copy") {
        val txAccessList = TxAccessListFactory.create(chainId = 1)

        test("override 'to' parameter") {
            val to = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
            val copy = txAccessList.copy(to = to)

            copy shouldNotBe txAccessList
            copy.to shouldBe to
        }

        test("override 'value' parameter") {
            val value = BigInteger("11650662055314012")
            val copy = txAccessList.copy(value = value)

            copy shouldNotBe txAccessList
            copy.value shouldBe value
        }

        test("override 'nonce' parameter") {
            val nonce = 39L
            val copy = txAccessList.copy(nonce = nonce)

            copy shouldNotBe txAccessList
            copy.nonce shouldBe nonce
        }

        test("override 'gas' parameter") {
            val gas = 27_527L
            val copy = txAccessList.copy(gas = gas)

            copy shouldNotBe txAccessList
            copy.gas shouldBe gas
        }

        test("override 'gasPrice' parameter") {
            val gasPrice = BigInteger("5393791319")
            val copy = txAccessList.copy(gasPrice = gasPrice)

            copy shouldNotBe txAccessList
            copy.gasPrice shouldBe gasPrice
            copy.gasFeeCap shouldBe gasPrice
            copy.gasTipCap shouldBe gasPrice
        }

        test("override 'data' parameter") {
            val data = Bytes("666666666666666666666666")
            val copy = txAccessList.copy(data = data)

            copy shouldNotBe txAccessList
            copy.data shouldBe data
        }

        test("override 'chainId' parameter") {
            val chainId = 2L
            val copy = txAccessList.copy(chainId = chainId)

            copy shouldNotBe txAccessList
            copy.chainId shouldBe chainId
        }

        test("override 'accessList' parameter") {
            val accessList = listOf(
                AccessList.Item(
                    Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
                    listOf(
                        Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
                    ),
                ),
            )
            val copy = txAccessList.copy(accessList = accessList)

            copy shouldNotBe txAccessList
            copy.accessList shouldBe accessList
        }
    }
})
