package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.types.Base58BytesSerializer
import io.ethers.solana.types.Base64TupleBytesSerializer
import io.ethers.solana.types.Programs
import io.ethers.solana.types.ReturnData
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaRPCInstruction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class SolanaBytesTest : FunSpec({
    test("fromBytes takes ownership, asByteArray exposes it, toByteArray copies") {
        val input = byteArrayOf(1, 2, 3)
        val bytes = SolanaBytes.fromBytes(input)
        val originalHash = bytes.hashCode()
        val keyed = mapOf(bytes to "value")

        // the array is adopted, not copied
        bytes.asByteArray() shouldBeSameInstanceAs input

        // toByteArray hands back a copy, so writing to it cannot change the value
        val output = bytes.toByteArray()
        output shouldNotBeSameInstanceAs input
        output[1] = 9
        bytes.toByteArray() shouldBe byteArrayOf(1, 2, 3)
        bytes.hashCode() shouldBe originalHash
        keyed[SolanaBytes.fromBytes(byteArrayOf(1, 2, 3))] shouldBe "value"
        bytes.toBase58() shouldBe "Ldp"
        bytes.toBase64() shouldBe "AQID"
        bytes.toHex() shouldBe "010203"
    }

    test("equality and hashing depend on contents not instance or source encoding") {
        val bytes = SolanaBytes.fromBytes(byteArrayOf(1, 2, 3))
        val values = listOf(bytes, SolanaBytes.fromHex("010203"), SolanaBytes.fromBase58("Ldp"), SolanaBytes.fromBase64("AQID"))
        values.toSet().size shouldBe 1
        values.forEach {
            it shouldBe bytes
            it.hashCode() shouldBe bytes.hashCode()
        }
        bytes.equals(null) shouldBe false
        bytes.equals(byteArrayOf(1, 2, 3)) shouldBe false
        bytes.equals("010203") shouldBe false
        bytes.equals(SolanaBytes.fromHex("010204")) shouldBe false
        bytes.equals(SolanaBytes.fromHex("0102")) shouldBe false
    }

    test("size byte access and diagnostic output have no implicit text encoding") {
        val bytes = SolanaBytes.fromHex("00ff80")
        bytes.size shouldBe 3
        bytes.isEmpty shouldBe false
        bytes[0] shouldBe 0.toByte()
        bytes[1] shouldBe (-1).toByte()
        bytes[2] shouldBe (-128).toByte()
        bytes.toString() shouldBe "SolanaBytes(size=3)"
        shouldThrow<IndexOutOfBoundsException> { bytes[-1] }
        shouldThrow<IndexOutOfBoundsException> { bytes[3] }
    }

    test("empty values work across factories encodings slicing and copying") {
        val empty = SolanaBytes.EMPTY
        listOf(
            SolanaBytes.fromBytes(byteArrayOf()),
            SolanaBytes.fromBase58(""),
            SolanaBytes.fromBase64(""),
            SolanaBytes.fromHex(""),
            SolanaBytes.fromHex("0x"),
            SolanaBytes.fromHex("0X"),
            empty.slice(0, 0),
        ).forEach { it shouldBe empty }
        empty.size shouldBe 0
        empty.isEmpty shouldBe true
        empty.toByteArray() shouldBe byteArrayOf()
        empty.toBase58() shouldBe ""
        empty.toBase64() shouldBe ""
        empty.toHex() shouldBe ""
        empty.toString() shouldBe "SolanaBytes(size=0)"
        empty.copyInto(byteArrayOf())
        empty.copyInto(byteArrayOf(1), 1)
        shouldThrow<IndexOutOfBoundsException> { empty[0] }
    }

    test("slices use exclusive end indices without padding or exposing mutable bytes") {
        val bytes = SolanaBytes.fromHex("00010203")
        bytes.slice(1, 3) shouldBe SolanaBytes.fromHex("0102")
        bytes.slice(0, bytes.size) shouldBe bytes
        bytes.slice(2, 2) shouldBe SolanaBytes.EMPTY
        bytes.slice(bytes.size, bytes.size) shouldBe SolanaBytes.EMPTY
        val slice = bytes.slice(1, 3)
        val copied = slice.toByteArray()
        copied[0] = 9
        slice shouldBe SolanaBytes.fromHex("0102")
        bytes shouldBe SolanaBytes.fromHex("00010203")
        for ((start, end) in listOf(-1 to 0, 0 to -1, 0 to 5, 5 to 5, Int.MIN_VALUE to Int.MAX_VALUE)) {
            shouldThrow<IndexOutOfBoundsException> { bytes.slice(start, end) }
        }
        shouldThrow<IllegalArgumentException> { bytes.slice(3, 2) }
    }

    test("copyInto validates all bounds before writing and never shares the destination") {
        val bytes = SolanaBytes.fromHex("010203")
        val target = ByteArray(5) { 9 }
        bytes.copyInto(target, 1)
        target shouldBe byteArrayOf(9, 1, 2, 3, 9)
        target[1] = 8
        bytes shouldBe SolanaBytes.fromHex("010203")
        val exact = ByteArray(3)
        bytes.copyInto(exact)
        exact shouldBe byteArrayOf(1, 2, 3)
        for (offset in listOf(-1, 1, 4, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val destination = ByteArray(3) { 9 }
            shouldThrow<IndexOutOfBoundsException> { bytes.copyInto(destination, offset) }
            destination shouldBe byteArrayOf(9, 9, 9)
        }
        shouldThrow<IndexOutOfBoundsException> { SolanaBytes.EMPTY.copyInto(ByteArray(0), 1) }
        shouldThrow<IndexOutOfBoundsException> { SolanaBytes.EMPTY.copyInto(ByteArray(0), -1) }
    }

    test("all byte values and leading zeroes roundtrip through explicit encodings") {
        for (input in listOf(byteArrayOf(0), byteArrayOf(0, 0, 1), byteArrayOf(-1), ByteArray(256) { it.toByte() })) {
            val bytes = SolanaBytes.fromBytes(input)
            SolanaBytes.fromBase58(bytes.toBase58()) shouldBe bytes
            SolanaBytes.fromBase64(bytes.toBase64()) shouldBe bytes
            SolanaBytes.fromHex(bytes.toHex()) shouldBe bytes
            SolanaBytes.fromHex("0x" + bytes.toHex()) shouldBe bytes
            SolanaBytes.fromHex("0X" + bytes.toHex().uppercase()) shouldBe bytes
        }
        SolanaBytes.fromBytes(byteArrayOf(0, 0)).toBase58() shouldBe "11"
        SolanaBytes.fromHex("AbCd").toHex() shouldBe "abcd"
    }

    test("invalid text is rejected without guessing the input encoding") {
        for (input in listOf("0", "O", "I", "l", " ", "é")) {
            shouldThrow<IllegalArgumentException> { SolanaBytes.fromBase58(input) }
        }
        for (input in listOf("!", "A", "====", "AQ!D")) {
            shouldThrow<IllegalArgumentException> { SolanaBytes.fromBase64(input) }
        }
        for (input in listOf("g0", "01xz", "é0", " 00", "0", "abc", "0x1", "0Xabc")) {
            shouldThrow<IllegalArgumentException> { SolanaBytes.fromHex(input) }
        }
    }

    test("field serializers choose base58 strings or base64 tuples for the same bytes") {
        val bytes = SolanaBytes.fromHex("010203")
        val json = Kotlinx.DEFAULT
        val base58 = JsonPrimitive("Ldp")
        val base64 = JsonArray(listOf(JsonPrimitive("AQID"), JsonPrimitive("base64")))
        json.encodeToJsonElement(Base58BytesSerializer, bytes) shouldBe base58
        json.decodeFromJsonElement(Base58BytesSerializer, base58) shouldBe bytes
        json.encodeToJsonElement(Base64TupleBytesSerializer, bytes) shouldBe base64
        json.decodeFromJsonElement(Base64TupleBytesSerializer, base64) shouldBe bytes
        val instruction = SolanaRPCInstruction(0, emptyList(), bytes)
        val returned = ReturnData(Programs.SYSTEM, bytes)
        json.encodeToJsonElement(instruction).jsonObject["data"] shouldBe base58
        json.encodeToJsonElement(returned).jsonObject["data"] shouldBe base64
        json.decodeFromString<SolanaRPCInstruction>(json.encodeToString(instruction)) shouldBe instruction
        json.decodeFromString<ReturnData>(json.encodeToString(returned)) shouldBe returned
        json.decodeFromString<ReturnData>(json.encodeToString(returned.copy(data = SolanaBytes.EMPTY))).data shouldBe SolanaBytes.EMPTY
        val copied = instruction.data.toByteArray()
        copied[0] = 9
        json.encodeToJsonElement(instruction).jsonObject["data"] shouldBe base58
    }
})
