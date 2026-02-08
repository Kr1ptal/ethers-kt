package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class AccountOverrideTest : FunSpec({
    test("serialization - state") {
        val accountOverride = AccountOverride {
            nonce(455_629L)
            code(Bytes("0x01"))
            balance(BigInteger("11650662055314013"))
            state(
                mapOf(
                    Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c") to Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                    Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d") to Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                ),
            )
        }

        Jackson.MAPPER.writeValueAsString(accountOverride) shouldEqualJson """
            {
              "nonce": "0x${accountOverride.nonce.toString(16)}",
              "code": "${accountOverride.code!!}",
              "balance": "0x${accountOverride.balance!!.toString(16)}",
              "state": {
                "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c": "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                "0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d": "0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"
              }
            }
        """
    }

    test("serialization - stateDiff") {
        val accountOverride = AccountOverride {
            nonce(455_629L)
            code(Bytes("0x01"))
            balance(BigInteger("11650662055314013"))
            stateDiff(
                mapOf(
                    Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c") to Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                    Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d") to Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                ),
            )
        }

        Jackson.MAPPER.writeValueAsString(accountOverride) shouldEqualJson """
            {
              "nonce": "0x${accountOverride.nonce.toString(16)}",
              "code": "${accountOverride.code!!}",
              "balance": "0x${accountOverride.balance!!.toString(16)}",
              "stateDiff": {
                "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c": "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                "0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d": "0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"
              }
            }
        """
    }

    context("mergeChanges") {
        val hash1 = Hash(ByteArray(32) { 1 })
        val hash2 = Hash(ByteArray(32) { 2 })
        val hash3 = Hash(ByteArray(32) { 3 })
        val accState1 = AccountOverride {
            nonce(1)
            code(Bytes("0x01"))
            balance(BigInteger("1"))
            state(
                mapOf(
                    hash1 to hash1,
                ),
            )
        }
        val accState2 = AccountOverride {
            nonce(2)
            code(Bytes("0x02"))
            balance(BigInteger("2"))
            state(
                mapOf(
                    hash2 to hash2,
                ),
            )
        }

        val accStateDiff1 = AccountOverride {
            nonce(1)
            code(Bytes("0x01"))
            balance(BigInteger("1"))
            stateDiff(
                mapOf(
                    hash1 to hash1,
                ),
            )
        }

        val accStateDiff2 = AccountOverride {
            nonce(3)
            code(Bytes("0x03"))
            balance(BigInteger("3"))
            stateDiff(
                mapOf(
                    hash3 to hash3,
                ),
            )
        }

        // state overridden by second account
        test("merge accounts: state") {
            val merged = accState1.mergeChanges(accState2)
            merged shouldBe AccountOverride {
                nonce(2)
                code(Bytes("0x02"))
                balance(BigInteger("2"))
                state(
                    mapOf(
                        hash2 to hash2,
                    ),
                )
            }
        }

        test("merge accounts: stateDiff") {
            val merged = accStateDiff1.mergeChanges(accStateDiff2)
            merged shouldBe AccountOverride {
                nonce(3)
                code(Bytes("0x03"))
                balance(BigInteger("3"))
                stateDiff(
                    mapOf(
                        hash1 to hash1,
                        hash3 to hash3,
                    ),
                )
            }
        }

        // diff changes are applied to state
        test("merge accounts: state + stateDiff") {
            val merged = accState1.mergeChanges(accStateDiff2)
            merged shouldBe AccountOverride {
                nonce(3)
                code(Bytes("0x03"))
                balance(BigInteger("3"))
                state(
                    mapOf(
                        hash1 to hash1,
                        hash3 to hash3,
                    ),
                )
            }
        }

        // stateDiff overridden by state
        test("merge accounts: stateDiff + state") {
            val merged = accStateDiff2.mergeChanges(accState1)
            merged shouldBe AccountOverride {
                nonce(1)
                code(Bytes("0x01"))
                balance(BigInteger("1"))
                state(
                    mapOf(
                        hash1 to hash1,
                    ),
                )
            }
        }
    }

    test("cant set both state and stateDiff") {
        // state already set
        shouldThrowUnit<IllegalArgumentException> {
            val acc = AccountOverride()
            acc.state = emptyMap()
            acc.stateDiff = emptyMap()
        }
        shouldThrowUnit<IllegalArgumentException> {
            val acc = AccountOverride()
            acc.state(emptyMap())
            acc.stateDiff(emptyMap())
        }

        // stateDiff already set
        shouldThrowUnit<IllegalArgumentException> {
            val acc = AccountOverride()
            acc.stateDiff = emptyMap()
            acc.state = emptyMap()
        }
        shouldThrowUnit<IllegalArgumentException> {
            val acc = AccountOverride()
            acc.stateDiff(emptyMap())
            acc.state(emptyMap())
        }
    }
})
