package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger

class BlockOverrideTest : FunSpec({
    test("BlockOverride serialization - all fields") {
        val blockOverride = BlockOverride {
            number(18283547L)
            difficulty(BigInteger("58750003716598352816469"))
            time(1696499687L)
            gasLimit(30_000_000L)
            coinbase(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
            random(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"))
            baseFee(BigInteger("6439232586"))
        }

        Jackson.MAPPER.writeValueAsString(blockOverride) shouldEqualJson """
            {
              "number": "0x${blockOverride.number.toString(16)}",
              "difficulty": "0x${blockOverride.difficulty!!.toString(16)}",
              "time": "0x${blockOverride.time.toString(16)}",
              "gasLimit": "0x${blockOverride.gasLimit.toString(16)}",
              "coinbase": "${blockOverride.coinbase!!}",
              "random": "${blockOverride.random!!}",
              "baseFee": "0x${blockOverride.baseFee!!.toString(16)}"
            }
        """
    }

    test("serialization - empty BlockOverride produces empty object") {
        Jackson.MAPPER.writeValueAsString(BlockOverride()) shouldEqualJson "{}"
    }

    test("serialization - only number set") {
        val blockOverride = BlockOverride { number(100L) }
        Jackson.MAPPER.writeValueAsString(blockOverride) shouldEqualJson """
            { "number": "0x64" }
        """
    }

    test("serialization - only coinbase and baseFee set") {
        val blockOverride = BlockOverride {
            coinbase(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
            baseFee(BigInteger("1000"))
        }
        Jackson.MAPPER.writeValueAsString(blockOverride) shouldEqualJson """
            {
              "coinbase": "0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5",
              "baseFee": "0x3e8"
            }
        """
    }

    test("fail on setting invalid values") {
        shouldThrow<IllegalArgumentException> { BlockOverride { difficulty(BigInteger("-1")) } }
        shouldThrow<IllegalArgumentException> { BlockOverride { difficulty = BigInteger("-1") } }

        shouldThrow<IllegalArgumentException> { BlockOverride { baseFee(BigInteger("-1")) } }
        shouldThrow<IllegalArgumentException> { BlockOverride { baseFee = BigInteger("-1") } }
    }

    context("copy constructor") {
        test("produces equal but distinct instance") {
            val original = BlockOverride {
                number(100L)
                difficulty(BigInteger.TEN)
                time(1000L)
                gasLimit(30_000_000L)
                coinbase(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
                random(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"))
                baseFee(BigInteger("1000"))
            }

            val copy = BlockOverride(original)
            copy shouldBe original
            (copy !== original) shouldBe true
        }
    }

    context("equals and hashCode") {
        test("equal instances have same hashCode") {
            val a = BlockOverride {
                number(100L)
                time(1000L)
                baseFee(BigInteger.TEN)
            }
            val b = BlockOverride {
                number(100L)
                time(1000L)
                baseFee(BigInteger.TEN)
            }
            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        withData(
            nameFn = { it.first },
            "number" to (BlockOverride { number(100L) } to BlockOverride { number(200L) }),
            "difficulty" to (BlockOverride { difficulty(BigInteger.ONE) } to BlockOverride { difficulty(BigInteger.TEN) }),
            "time" to (BlockOverride { time(100L) } to BlockOverride { time(200L) }),
            "gasLimit" to (BlockOverride { gasLimit(100L) } to BlockOverride { gasLimit(200L) }),
            "coinbase" to (BlockOverride { coinbase(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")) } to BlockOverride { coinbase(Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9")) }),
            "random" to (BlockOverride { random(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")) } to BlockOverride { random(Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b")) }),
            "baseFee" to (BlockOverride { baseFee(BigInteger.ONE) } to BlockOverride { baseFee(BigInteger.TEN) }),
        ) { (_, pair) ->
            pair.first shouldNotBe pair.second
        }

        test("same instance is equal") {
            val a = BlockOverride { number(100L) }
            a shouldBe a
        }

        test("not equal to null or different type") {
            val a = BlockOverride { number(100L) }
            a.equals(null) shouldBe false
            a.equals("string") shouldBe false
        }
    }
})
