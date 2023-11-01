package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import java.math.BigInteger

class AccountOverrideTest : FunSpec({
    test("AccountOverride serialization") {
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
              "state": {
                "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c": "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                "0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d": "0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"
              },
              "stateDiff": {
                "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c": "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                "0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d": "0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"
              }
            }
        """
    }
})
