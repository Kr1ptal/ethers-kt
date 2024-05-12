package io.ethers.core.types.transactions

import fixtures.TxDynamicFeeFactory
import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger

class TxDynamicFeeTest : FunSpec({

    context("initialization") {
        test("invalid chainId") {
            shouldThrow<IllegalArgumentException> {
                TxDynamicFeeFactory.create(chainId = -1L)
            }
        }
    }

    context("copy") {
        val txDynamicFee = TxDynamicFeeFactory.create(chainId = 1)

        test("override 'to' parameter") {
            val to = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
            val copy = txDynamicFee.copy(to = to)

            copy shouldNotBe txDynamicFee
            copy.to shouldBe to
        }

        test("override 'value' parameter") {
            val value = BigInteger("11650662055314012")
            val copy = txDynamicFee.copy(value = value)

            copy shouldNotBe txDynamicFee
            copy.value shouldBe value
        }

        test("override 'nonce' parameter") {
            val nonce = 39L
            val copy = txDynamicFee.copy(nonce = nonce)

            copy shouldNotBe txDynamicFee
            copy.nonce shouldBe nonce
        }

        test("override 'gas' parameter") {
            val gas = 27_527L
            val copy = txDynamicFee.copy(gas = gas)

            copy shouldNotBe txDynamicFee
            copy.gas shouldBe gas
        }

        test("override 'gasFeeCap' parameter") {
            val gasFeeCap = BigInteger("5393791319")
            val copy = txDynamicFee.copy(gasFeeCap = gasFeeCap)

            copy shouldNotBe txDynamicFee
            copy.gasFeeCap shouldBe gasFeeCap
            copy.gasPrice shouldBe gasFeeCap
        }

        test("override 'gasTipCap' parameter") {
            val gasTipCap = BigInteger("393791319")
            val gasFeeCap = BigInteger("5393791319")
            val copy = txDynamicFee.copy(gasFeeCap = gasFeeCap, gasTipCap = gasTipCap)

            copy shouldNotBe txDynamicFee
            copy.gasTipCap shouldBe gasTipCap
            copy.gasPrice shouldBe gasFeeCap
        }

        test("override 'data' parameter") {
            val data = Bytes("666666666666666666666666")
            val copy = txDynamicFee.copy(data = data)

            copy shouldNotBe txDynamicFee
            copy.data shouldBe data
        }

        test("override 'chainId' parameter") {
            val chainId = 2L
            val copy = txDynamicFee.copy(chainId = chainId)

            copy shouldNotBe txDynamicFee
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
            val copy = txDynamicFee.copy(accessList = accessList)

            copy shouldNotBe txDynamicFee
            copy.accessList shouldBe accessList
        }
    }
})
