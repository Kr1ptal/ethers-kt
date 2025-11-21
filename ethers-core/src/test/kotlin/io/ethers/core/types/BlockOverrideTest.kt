package io.ethers.core.types

import io.ethers.json.jackson.Jackson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import java.math.BigInteger

class BlockOverrideTest : FunSpec({
    test("BlockOverride serialization") {
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

    test("fail on setting invalid values") {
        shouldThrow<IllegalArgumentException> { BlockOverride { difficulty(BigInteger("-1")) } }
        shouldThrow<IllegalArgumentException> { BlockOverride { difficulty = BigInteger("-1") } }

        shouldThrow<IllegalArgumentException> { BlockOverride { baseFee(BigInteger("-1")) } }
        shouldThrow<IllegalArgumentException> { BlockOverride { baseFee = BigInteger("-1") } }
    }
})
