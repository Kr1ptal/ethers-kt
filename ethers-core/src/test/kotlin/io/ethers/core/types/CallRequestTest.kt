package io.ethers.core.types

import io.ethers.core.Jackson
import io.ethers.core.types.transaction.TxAccessList
import io.ethers.core.types.transaction.TxBlob
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigInteger

class CallRequestTest : FunSpec({
    test("CallRequest serialization") {
        val callRequest = CallRequest {
            from(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
            to(Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"))
            gas(27_527L)
            gasPrice(BigInteger("6439232586"))
            gasFeeCap(BigInteger("6439232587"))
            gasTipCap(BigInteger("1"))
            value(BigInteger("11650662055314012"))
            nonce(455_628L)
            data(Bytes("0x01"))
            accessList(
                listOf(
                    AccessList.Item(
                        Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
                        listOf(
                            Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"),
                            Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                            Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d"),
                            Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                        ),
                    ),
                ),
            )
            chainId(1)
        }

        Jackson.MAPPER.writeValueAsString(callRequest) shouldEqualJson """
            {
              "from": "${callRequest.from!!}",
              "to": "${callRequest.to!!}",
              "gas": "0x${callRequest.gas.toString(16)}",
              "gasPrice": "0x${callRequest.gasPrice!!.toString(16)}",
              "maxFeePerGas": "0x${callRequest.gasFeeCap!!.toString(16)}",
              "maxPriorityFeePerGas": "0x${callRequest.gasTipCap!!.toString(16)}",
              "value": "0x${callRequest.value!!.toString(16)}",
              "nonce": "0x${callRequest.nonce.toString(16)}",
              "data": "${callRequest.data!!}",
              "accessList": [
                {
                  "address": "${callRequest.accessList[0].address}",
                  "storageKeys": [
                    "${callRequest.accessList[0].storageKeys[0]}",
                    "${callRequest.accessList[0].storageKeys[1]}",
                    "${callRequest.accessList[0].storageKeys[2]}",
                    "${callRequest.accessList[0].storageKeys[3]}"
                  ]
                }
              ],
              "chainId": "0x${callRequest.chainId.toString(16)}"
            }
        """
    }

    test("serialization with blob fields") {
        val hash1 = Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")
        val callRequest = CallRequest {
            gas(21_000L)
            nonce(1)
            gasFeeCap(BigInteger.TEN)
            gasTipCap(BigInteger.ONE)
            blobFeeCap(BigInteger("100"))
            blobVersionedHashes(listOf(hash1))
            chainId(1)
        }

        Jackson.MAPPER.writeValueAsString(callRequest) shouldEqualJson """
            {
              "gas": "0x5208",
              "maxFeePerGas": "0xa",
              "maxPriorityFeePerGas": "0x1",
              "nonce": "0x1",
              "chainId": "0x1",
              "maxFeePerBlobGas": "0x64",
              "blobVersionedHashes": ["$hash1"]
            }
        """
    }

    test("throws on negative BigInteger assignment") {
        val request = CallRequest()

        shouldThrowUnit<IllegalArgumentException> { request.gasPrice = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.gasPrice(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.gasFeeCap = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.gasFeeCap(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.gasTipCap = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.gasTipCap(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.value = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.value(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.blobFeeCap = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.blobFeeCap(BigInteger.ONE.negate()) }
    }

    context("toUnsignedTransactionOrNull") {
        val addr = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")

        test("returns null when nonce is negative") {
            val request = CallRequest {
                gas(21_000L)
                gasPrice(BigInteger.TEN)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }

        test("returns null when gas is below 21000") {
            val request = CallRequest {
                nonce(0)
                gas(20_999L)
                gasPrice(BigInteger.TEN)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }

        test("returns TxDynamicFee when gasFeeCap and gasTipCap set with valid chainId") {
            val request = CallRequest {
                to(addr)
                nonce(1)
                gas(21_000L)
                gasFeeCap(BigInteger.TEN)
                gasTipCap(BigInteger.ONE)
                value(BigInteger("1000"))
                data(Bytes("0xabcd"))
                chainId(1)
            }

            val tx = request.toUnsignedTransactionOrNull()
            tx.shouldBeInstanceOf<TxDynamicFee>()
            tx.to shouldBe addr
            tx.nonce shouldBe 1L
            tx.gas shouldBe 21_000L
            tx.gasFeeCap shouldBe BigInteger.TEN
            tx.gasTipCap shouldBe BigInteger.ONE
            tx.value shouldBe BigInteger("1000")
            tx.data shouldBe Bytes("0xabcd")
            tx.chainId shouldBe 1L
        }

        test("returns TxDynamicFee with default zero value when value is null") {
            val request = CallRequest {
                nonce(0)
                gas(21_000L)
                gasFeeCap(BigInteger.TEN)
                gasTipCap(BigInteger.ONE)
                chainId(1)
            }

            val tx = request.toUnsignedTransactionOrNull()
            tx.shouldBeInstanceOf<TxDynamicFee>()
            tx.value shouldBe BigInteger.ZERO
        }

        test("returns null for TxDynamicFee when chainId is invalid") {
            val request = CallRequest {
                nonce(0)
                gas(21_000L)
                gasFeeCap(BigInteger.TEN)
                gasTipCap(BigInteger.ONE)
                chainId(-1)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }

        test("returns TxBlob when blob fields are set") {
            val blobHash = Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")
            val request = CallRequest {
                to(addr)
                nonce(1)
                gas(21_000L)
                gasFeeCap(BigInteger.TEN)
                gasTipCap(BigInteger.ONE)
                blobFeeCap(BigInteger("100"))
                blobVersionedHashes(listOf(blobHash))
                chainId(1)
            }

            val tx = request.toUnsignedTransactionOrNull()
            tx.shouldBeInstanceOf<TxBlob>()
            tx.to shouldBe addr
            tx.blobFeeCap shouldBe BigInteger("100")
            tx.blobVersionedHashes shouldBe listOf(blobHash)
        }

        test("returns null for TxBlob when to is null") {
            val request = CallRequest {
                nonce(1)
                gas(21_000L)
                gasFeeCap(BigInteger.TEN)
                gasTipCap(BigInteger.ONE)
                blobFeeCap(BigInteger("100"))
                blobVersionedHashes(listOf(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")))
                chainId(1)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }

        test("returns null for TxBlob when blobFeeCap is null") {
            val request = CallRequest {
                to(addr)
                nonce(1)
                gas(21_000L)
                gasFeeCap(BigInteger.TEN)
                gasTipCap(BigInteger.ONE)
                blobVersionedHashes(listOf(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")))
                chainId(1)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }

        test("returns null for TxBlob when chainId is invalid") {
            val request = CallRequest {
                to(addr)
                nonce(1)
                gas(21_000L)
                gasFeeCap(BigInteger.TEN)
                gasTipCap(BigInteger.ONE)
                blobFeeCap(BigInteger("100"))
                blobVersionedHashes(listOf(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")))
                chainId(-1)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }

        test("returns TxAccessList when gasPrice and non-empty accessList set") {
            val accessListItem = AccessList.Item(
                addr,
                listOf(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")),
            )
            val request = CallRequest {
                to(addr)
                nonce(1)
                gas(21_000L)
                gasPrice(BigInteger.TEN)
                accessList(listOf(accessListItem))
                chainId(1)
            }

            val tx = request.toUnsignedTransactionOrNull()
            tx.shouldBeInstanceOf<TxAccessList>()
            tx.gasPrice shouldBe BigInteger.TEN
            tx.accessList shouldBe listOf(accessListItem)
        }

        test("returns null for TxAccessList when chainId is invalid") {
            val request = CallRequest {
                nonce(1)
                gas(21_000L)
                gasPrice(BigInteger.TEN)
                accessList(
                    listOf(
                        AccessList.Item(
                            addr,
                            listOf(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")),
                        ),
                    ),
                )
                chainId(-1)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }

        test("returns TxLegacy when only gasPrice set with empty accessList") {
            val request = CallRequest {
                to(addr)
                nonce(1)
                gas(21_000L)
                gasPrice(BigInteger.TEN)
                chainId(1)
            }

            val tx = request.toUnsignedTransactionOrNull()
            tx.shouldBeInstanceOf<TxLegacy>()
            tx.gasPrice shouldBe BigInteger.TEN
            tx.chainId shouldBe 1L
        }

        test("returns null when no gas pricing fields set") {
            val request = CallRequest {
                nonce(1)
                gas(21_000L)
                chainId(1)
            }
            request.toUnsignedTransactionOrNull().shouldBeNull()
        }
    }

    context("copy constructor") {
        test("produces equal but distinct instance") {
            val original = CallRequest {
                from(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
                to(Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"))
                gas(21_000L)
                gasPrice(BigInteger.TEN)
                gasFeeCap(BigInteger("100"))
                gasTipCap(BigInteger.ONE)
                value(BigInteger("1000"))
                nonce(1)
                data(Bytes("0x01"))
                chainId(1)
                blobFeeCap(BigInteger("50"))
                blobVersionedHashes(listOf(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")))
            }

            val copy = CallRequest(original)
            copy shouldBe original
            (copy !== original) shouldBe true
        }
    }

    context("equals and hashCode") {
        test("equal CallRequests have same hashCode") {
            val a = CallRequest {
                from(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
                nonce(1)
                gas(21_000L)
                gasPrice(BigInteger.TEN)
            }
            val b = CallRequest {
                from(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
                nonce(1)
                gas(21_000L)
                gasPrice(BigInteger.TEN)
            }

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        test("different CallRequests are not equal") {
            val a = CallRequest { nonce(1) }
            val b = CallRequest { nonce(2) }
            a shouldNotBe b
        }

        test("not equal to null or different type") {
            val a = CallRequest { nonce(1) }
            a.equals(null) shouldBe false
            a.equals("string") shouldBe false
        }

        test("same instance is equal") {
            val a = CallRequest { nonce(1) }
            a shouldBe a
        }
    }
})
