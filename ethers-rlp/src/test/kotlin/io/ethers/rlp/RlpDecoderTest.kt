package io.ethers.rlp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class RlpDecoderTest : FunSpec({
    context("decode - BigInteger") {
        val maxUint256 = BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)
        withData(
            BigInteger.ZERO to "80",
            "73".toBigInteger(16) to "73",
            "abc12841ff".toBigInteger(16) to "85abc12841ff",
            maxUint256 to "a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        ) { (result, input) ->
            val rlp = RlpDecoder(input.hexToByteArray())
            val v = rlp.decodeBigInteger()
            v shouldBe result
        }
    }

    context("decode - Long") {
        withData(
            0L to "80",
            0x20L to "20",
            0xabc12841ffL to "85abc12841ff",
            Long.MAX_VALUE to "887fffffffffffffff",
        ) { (result, input) ->
            val rlp = RlpDecoder(input.hexToByteArray())
            val v = rlp.decodeLong()
            v shouldBe result
        }
    }

    context("decode - ByteArray") {
        context("decode") {
            withData(
                byteArrayOf() to "80",
                byteArrayOf(123) to "7b",
                byteArrayOf(-128) to "8180",
                byteArrayOf(-85, -70) to "82abba",
                ByteArray(190) { -81 } to ("b8be" + "af".repeat(190)),
            ) { (result, input) ->
                val rlp = RlpDecoder(input.hexToByteArray())
                val v = rlp.decodeByteArray()
                v shouldBe result
            }
        }

        context("decode and transform") {
            withData(
                byteArrayOf() to "80",
                byteArrayOf(123) to "7b",
                byteArrayOf(-128) to "8180",
                byteArrayOf(-85, -70) to "82abba",
            ) { (result, input) ->
                val rlp = RlpDecoder(input.hexToByteArray())
                val v = rlp.decodeByteArray().toHexString()
                v shouldBe result.toHexString()
            }
        }
    }

    context("decodeList") {
        test("empty list") {
            val rlp = RlpDecoder("c0".hexToByteArray())
            val v = rlp.decodeListOrNull { } ?: emptyList<Any>()
            v shouldBe emptyList<Any>()
        }

        test("list of one") {
            val rlp = RlpDecoder("c101".hexToByteArray())
            val v = rlp.decodeListOrNull { decodeLong() }
            v shouldBe 1L
        }

        test("list of two longs") {
            val rlp = RlpDecoder("c883ffccb583ffc0b5".hexToByteArray())
            val v = rlp.decodeListOrNull {
                listOf(decodeLong(), decodeLong())
            }
            v shouldContainExactly listOf(0xFFCCB5L, 0xFFC0B5L)
        }

        test("nested list") {
            val rlp = RlpDecoder("d2c883ffccb583ffc0b5c883ffccb583ffc0b5".hexToByteArray())
            val v = rlp.decodeListOrNull {
                listOf(
                    decodeListOrNull { listOf(decodeLong(), decodeLong()) },
                    decodeListOrNull { listOf(decodeLong(), decodeLong()) },
                )
            }
            v shouldContainExactly listOf(
                listOf(0xFFCCB5L, 0xFFC0B5L),
                listOf(0xFFCCB5L, 0xFFC0B5L),
            )
        }

        test("list with many elements") {
            val rlp = RlpDecoder(
                "f84483646f6783676f64836361748374616383746163837461638374616383746163837461638374616383746163837461638374616383746163837461638374616383746163".hexToByteArray(),
            )
            val v = rlp.decodeAsListOrNull { decodeByteArray() }

            v shouldContainExactly listOf(
                "dog".toByteArray(),
                "god".toByteArray(),
                "cat".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
            )
        }

        test("decode via Supplier") {
            val rlp = RlpDecoder("d483646f6783676f64836361748374616383746163".hexToByteArray())
            val v = rlp.decodeListOrNull(
                RlpDecoder.Supplier {
                    listOf(
                        rlp.decodeByteArray(),
                        rlp.decodeByteArray(),
                        rlp.decodeByteArray(),
                        rlp.decodeByteArray(),
                        rlp.decodeByteArray(),
                    )
                },
            )

            v shouldContainExactly listOf(
                "dog".toByteArray(),
                "god".toByteArray(),
                "cat".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
            )
        }

        test("manual list decoding") {
            val rlp = RlpDecoder("d883646f6783676f6483636174837461638374616383746163".hexToByteArray())
            val ret = arrayListOf<ByteArray?>()

            rlp.startList()
            ret.add(rlp.decodeByteArray())
            ret.add(rlp.decodeByteArray())
            ret.add(rlp.decodeByteArray())
            ret.add(rlp.decodeByteArray())
            ret.add(rlp.decodeByteArray())
            ret.add(rlp.decodeByteArray())
            rlp.finishList(rlp.position)

            ret shouldContainExactly listOf(
                "dog".toByteArray(),
                "god".toByteArray(),
                "cat".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
                "tac".toByteArray(),
            )
        }

        context("manual list decoding fails") {
            test("incorrect finish decoding") {
                val rlp = RlpDecoder("d883646f6783676f6483636174837461638374616383746163".hexToByteArray())
                val ret = arrayListOf<ByteArray?>()

                val listEndPosition = rlp.startList()
                ret.add(rlp.decodeByteArray())
                ret.add(rlp.decodeByteArray())
                ret.add(rlp.decodeByteArray())
                ret.add(rlp.decodeByteArray())
                ret.add(rlp.decodeByteArray())
                ret.add(rlp.decodeByteArray())

                rlp.finishList(listEndPosition)

                shouldThrow<RlpDecoderException> {
                    rlp.finishList(listEndPosition)
                }
            }

            test("not all list elements were decoded AND/OR elements were wrongly decoded, e.g. as BigInteger instead of ByteArray") {
                val rlp = RlpDecoder("d883646f6783676f6483636174837461638374616383746163".hexToByteArray())
                val ret = arrayListOf<BigInteger?>()

                val listEndPosition = rlp.startList()
                ret.add(rlp.decodeBigInteger())
                ret.add(rlp.decodeBigInteger())
                ret.add(rlp.decodeBigInteger())
                ret.add(rlp.decodeBigInteger())

                shouldThrow<RlpDecoderException> {
                    rlp.finishList(listEndPosition)
                }
            }
        }
    }
})

internal fun String.hexToByteArray() = removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
