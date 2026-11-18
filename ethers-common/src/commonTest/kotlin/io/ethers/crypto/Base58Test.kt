package io.ethers.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class Base58Test : FunSpec({
    test("decodeOrNull decodes what decode does") {
        for (bytes in listOf(ByteArray(0), byteArrayOf(0), byteArrayOf(0, 0, 1), ByteArray(32) { it.toByte() })) {
            val text = Base58.encode(bytes)
            Base58.decodeOrNull(text) shouldBe bytes
            Base58.decode(text) shouldBe bytes
        }
    }

    test("a character outside the alphabet is null from decodeOrNull and thrown by decode") {
        for (text in listOf("0", "O", "I", "l", "1 1", "é", "11111\u0000")) {
            Base58.decodeOrNull(text) shouldBe null
            shouldThrow<IllegalArgumentException> { Base58.decode(text) }
        }
        shouldThrow<IllegalArgumentException> { Base58.decode("abc0") }.message shouldBe "Illegal character 0 at 3"
    }
})
