package io.ethers.core.types

import io.ethers.core.Kotlinx
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class BlockIdTest : FunSpec({
    test("Hash serialization") {
        val hashString = "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"

        val hash = BlockId.Hash(Hash(hashString))
        Kotlinx.DEFAULT.encodeToString(hash) shouldBe "\"$hashString\""
    }

    test("Number serialization") {
        Arb.long(min = 0L).checkAll {
            val numberHex = "0x" + (it).toString(16)

            val number = BlockId.Number(it)
            Kotlinx.DEFAULT.encodeToString(number) shouldBe "\"$numberHex\""
        }
    }

    test("Name serialization") {
        Kotlinx.DEFAULT.encodeToString(BlockId.LATEST) shouldBe "\"latest\""
    }
})
