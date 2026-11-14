package io.ethers.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlin.random.Random

class Base58Test : FunSpec({
    context("known vectors") {
        // from bitcoin core's base58_encode_decode.json, plus all-zero Solana addresses
        withData(
            "" to "",
            "61" to "2g",
            "626262" to "a3gV",
            "636363" to "aPEr",
            "73696d706c792061206c6f6e6720737472696e67" to "2cFupjhnEsSn59qHXstmK2ffpLv2",
            "00eb15231dfceb60925886b67d065299925915aeb172c06647" to "1NS17iag9jJgTHD1VXjvLCEnZuQ3rJDE9L",
            "516b6fcd0f" to "ABnLTmg",
            "bf4f89001e670274dd" to "3SEo3LWLoPntC",
            "572e4794" to "3EFU7m",
            "ecac89cad93923c02321" to "EJDM8drfXA6uyA",
            "10c8511e" to "Rt5zm",
            "00000000000000000000" to "1111111111",
            "00" to "1",
            "0000" to "11",
            "39" to "z",
            "3a" to "21",
            "0000000000000000000000000000000000000000000000000000000000000000" to "11111111111111111111111111111111",
            "0000000000000000000000000000000000000000000000000000000000000001" to "11111111111111111111111111111112",
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" to "JEKNVnkbo3jma5nREBBJCDoXFVeKkD56V3xKrvRmWxFG",
        ) { (hex, base58) ->
            val bytes = hex.hexToByteArray()
            Base58.encode(bytes) shouldBe base58
            Base58.decode(base58) shouldBe bytes
        }
    }

    test("encode matches reference implementation") {
        checkAll(Arb.byteArray(Arb.int(0..300), Arb.byte()), Arb.int(0..8)) { bytes, leadingZeros ->
            bytes.fill(0, 0, minOf(leadingZeros, bytes.size))
            val encoded = Base58.encode(bytes)

            encoded shouldBe Reference.encode(bytes)
            Base58.decode(encoded) shouldBe bytes
        }
    }

    test("decode matches reference implementation") {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val digits = Arb.string(0..400, Arb.element(alphabet.map { Codepoint(it.code) }))
        checkAll(digits, Arb.int(0..8)) { digits, leadingOnes ->
            val input = "1".repeat(leadingOnes) + digits
            Base58.decode(input) shouldBe Reference.decode(input)
        }
    }

    test("round-trips every length and boundary value up to 8 KiB") {
        val random = Random(58)
        for (size in (0..96) + listOf(255, 256, 257, 1023, 1024, 1025, 4096, 8191, 8192)) {
            val samples = listOf(
                random.nextBytes(size),
                ByteArray(size) { -1 },
                ByteArray(size).also { if (size > 0) it[size - 1] = 1 },
                ByteArray(size).also { if (size > 0) it[0] = 1 },
            )
            for (bytes in samples) {
                val encoded = Base58.encode(bytes)
                Base58.decode(encoded) shouldBe bytes
                if (size <= 1024) encoded shouldBe Reference.encode(bytes)
            }
        }
    }

    test("decodes the largest value of every length, which needs the most output space") {
        // "zzz...z" is 58^n - 1, the largest value n digits can hold, so it fills the output buffer the most
        for (length in (1..600) + listOf(1400, 2801, 5602, 11203)) {
            val maxValue = "z".repeat(length)
            for (input in listOf(maxValue, "111$maxValue")) {
                val decoded = Base58.decode(input)
                if (length <= 600) decoded shouldBe Reference.decode(input)
                Base58.encode(decoded) shouldBe input
            }
        }
    }

    context("decode rejects characters outside the alphabet") {
        withData(
            mapOf(
                "zero" to "0",
                "capital O" to "O",
                "capital I" to "I",
                "lowercase l" to "l",
                "plus" to "+",
                "slash" to "/",
                "inner space" to "a b",
                "trailing space" to "1 ",
                "trailing zero" to "abc0",
                "latin-1 letter" to "\u00e9",
                "first non-ASCII code" to "\u0080",
                "max char" to "\uffff",
            ),
        ) { input ->
            shouldThrow<IllegalArgumentException> { Base58.decode(input) }
        }
    }

    test("decode reports the offending character and position") {
        shouldThrow<IllegalArgumentException> { Base58.decode("11abc0def") }.message shouldBe "Illegal character 0 at 5"
    }
})

/** The previous digit-at-a-time implementation, kept as an oracle for the limb-based one. */
private object Reference {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val number = input.copyOf()
        var zeroCount = 0
        while (zeroCount < number.size && number[zeroCount].toInt() == 0) ++zeroCount
        val temp = CharArray(number.size * 2)
        var j = temp.size
        var startAt = zeroCount
        while (startAt < number.size) {
            var remainder = 0
            for (i in startAt..<number.size) {
                val acc = remainder * 256 + (number[i].toInt() and 0xFF)
                number[i] = (acc / 58).toByte()
                remainder = acc % 58
            }
            if (number[startAt].toInt() == 0) ++startAt
            temp[--j] = ALPHABET[remainder]
        }
        while (j < temp.size && temp[j] == ALPHABET[0]) ++j
        repeat(zeroCount) { temp[--j] = ALPHABET[0] }
        return temp.concatToString(j, temp.size)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val number = ByteArray(input.length) { ALPHABET.indexOf(input[it]).also { d -> require(d >= 0) }.toByte() }
        var zeroCount = 0
        while (zeroCount < number.size && number[zeroCount].toInt() == 0) ++zeroCount
        val temp = ByteArray(input.length)
        var j = temp.size
        var startAt = zeroCount
        while (startAt < number.size) {
            var remainder = 0
            for (i in startAt..<number.size) {
                val acc = remainder * 58 + number[i]
                number[i] = (acc / 256).toByte()
                remainder = acc % 256
            }
            if (number[startAt].toInt() == 0) ++startAt
            temp[--j] = remainder.toByte()
        }
        while (j < temp.size && temp[j].toInt() == 0) ++j
        return temp.copyOfRange(j - zeroCount, temp.size)
    }
}
