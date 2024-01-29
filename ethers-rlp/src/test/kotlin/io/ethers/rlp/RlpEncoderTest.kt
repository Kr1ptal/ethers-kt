package io.ethers.rlp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class RlpEncoderTest : FunSpec({
    context("encode - BigInteger") {
        context("success") {
            val maxUint256 = BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)
            withData(
                null to "80",
                BigInteger.ZERO to "80",
                "130".toBigInteger() to "8182",
                "73".toBigInteger(16) to "73",
                "abc12841ff".toBigInteger(16) to "85abc12841ff",
                maxUint256 to "a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            ) { (input, result) ->
                val encoder = RlpEncoder(1)
                encoder.encode(input)
                encoder.toHexString() shouldBe result
            }
        }

        test("failure - negative BigInteger value") {
            shouldThrow<IllegalArgumentException> {
                val encoder = RlpEncoder(1)
                encoder.encode(BigInteger.ONE.negate())
            }
        }

        test("failure - too big BigInteger value") {
            shouldThrow<IllegalArgumentException> {
                val encoder = RlpEncoder(1)
                encoder.encode(BigInteger.TWO.pow(256))
            }
        }
    }

    context("encode - Long") {
        context("success") {
            withData(
                0L to "80",
                0x20L to "20",
                0xabc12841ffL to "85abc12841ff",
                Long.MAX_VALUE to "887fffffffffffffff",
            ) { (input, result) ->
                val encoder = RlpEncoder(1)
                encoder.encode(input)
                encoder.toHexString() shouldBe result
            }
        }

        test("failure - negative Long value") {
            shouldThrow<IllegalArgumentException> {
                val encoder = RlpEncoder(1)
                encoder.encode(-1L)
            }
        }
    }

    context("encode - ByteArray") {
        withData(
            null to RLP_NULL.toByte().toHexString(),
            byteArrayOf() to RLP_NULL.toByte().toHexString(),
            byteArrayOf(0) to RLP_NULL.toByte().toHexString(),
            byteArrayOf(123) to "7b",
            byteArrayOf(-128) to "8180",
            byteArrayOf(-85, -70) to "82abba",
            ByteArray(55) { -81 } to ("b7" + "af".repeat(55)),
            ByteArray(0xff + 1) { 109 } to ("b90100" + "6d".repeat(256)),
            ByteArray(0xffff + 2) { 71 } to ("ba010001" + "47".repeat(65537)),
        ) { (input, result) ->
            val encoder = RlpEncoder(1)
            encoder.encode(input)
            encoder.toHexString() shouldBe result
        }
    }

    context("encode - List") {
        test("empty list") {
            val encoder = RlpEncoder(1)
            encoder.encodeList(emptyList())
            encoder.toHexString() shouldBe "c0"
        }

        test("list of one") {
            val encoder = RlpEncoder(1)
            encoder.encodeList {
                encode(1)
            }
            encoder.toHexString() shouldBe "c101"
        }

        test("list of two longs") {
            val encoder = RlpEncoder(1)
            encoder.encodeList {
                encode(0xFFCCB5)
                encode(0xFFC0B5)
            }
            encoder.toHexString() shouldBe "c883ffccb583ffc0b5"
        }

        test("nested list") {
            val encoder = RlpEncoder(1)
            encoder.encodeList {
                encoder.encodeList {
                    encode(0xFFCCB5)
                    encode(0xFFC0B5)
                }
                encoder.encodeList {
                    encode(0xFFCCB5)
                    encode(0xFFC0B5)
                }
            }
            encoder.toHexString() shouldBe "d2c883ffccb583ffc0b5c883ffccb583ffc0b5"
        }

        test("list with many elements") {
            val encoder = RlpEncoder()
            encoder.encodeList {
                encode("dog".toByteArray())
                encode("god".toByteArray())
                encode("cat".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
                encode("tac".toByteArray())
            }
            encoder.toHexString() shouldBe "f84483646f6783676f64836361748374616383746163837461638374616383746163837461638374616383746163837461638374616383746163837461638374616383746163"
        }

        test("encode via Runnable") {
            val encoder = RlpEncoder()
            encoder.encodeList(
                Runnable {
                    listOf("dog", "god", "cat", "tac", "tac").forEach { encoder.encode(it.toByteArray()) }
                },
            )

            encoder.toHexString() shouldBe "d483646f6783676f64836361748374616383746163"
        }

        test("manual list encoding") {
            val encoder = RlpEncoder()

            val bufferStartPosition = encoder.startList()
            listOf("dog", "god", "cat", "tac", "tac", "tac").forEach { encoder.encode(it.toByteArray()) }
            encoder.finishList(bufferStartPosition)

            encoder.toHexString() shouldBe "d883646f6783676f6483636174837461638374616383746163"
        }

        test("throw exception if manual list encoding is not finished before calling toByteArray()") {
            val encoder = RlpEncoder()
            encoder.startList()
            shouldThrow<IllegalStateException> {
                encoder.toByteArray()
            }
        }
    }
})

internal fun RlpEncoder.toHexString() = BigInteger(1, toByteArray()).toString(16)
