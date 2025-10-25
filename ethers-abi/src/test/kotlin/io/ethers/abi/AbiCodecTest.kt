package io.ethers.abi

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.math.BigInteger

class AbiCodecTest : FunSpec({
    context("encoding") {
        test("function without arguments") {
            val function = AbiFunction.parseSignature("oracle()")
            val result = AbiCodec.encodeWithPrefix(function.selector, emptyList(), emptyList()).toHexString()

            result shouldBe "7dc0d1d0"
        }

        test("non-256-bit numbers") {
            val function = AbiFunction.parseSignature(
                "flashLoan(address,address[],uint256[],uint256[],address,bytes,uint16)",
            )
            val params = listOf(
                Address("0xdeadeadeadeadeadeadeadeadeadeadeadeadead"),
                listOf(Address("0xdeadeadeadeadeadeadeadeadeadeadeadeadead")),
                listOf(BigInteger.TEN),
                listOf(BigInteger.TEN),
                Address("0xdeadeadeadeadeadeadeadeadeadeadeadeadead"),
                Bytes(byteArrayOf(0, 1, 2, 3, 4, 5)),
                "11424".toBigInteger(),
            )

            val encoded = AbiCodec.encodeWithPrefix(
                function.selector,
                function.inputs,
                params,
            ).toHexString()

            val expected = """
                ab9c4b5d
                000000000000000000000000deadeadeadeadeadeadeadeadeadeadeadeadead
                00000000000000000000000000000000000000000000000000000000000000e0
                0000000000000000000000000000000000000000000000000000000000000120
                0000000000000000000000000000000000000000000000000000000000000160
                000000000000000000000000deadeadeadeadeadeadeadeadeadeadeadeadead
                00000000000000000000000000000000000000000000000000000000000001a0
                0000000000000000000000000000000000000000000000000000000000002ca0
                0000000000000000000000000000000000000000000000000000000000000001
                000000000000000000000000deadeadeadeadeadeadeadeadeadeadeadeadead
                0000000000000000000000000000000000000000000000000000000000000001
                000000000000000000000000000000000000000000000000000000000000000a
                0000000000000000000000000000000000000000000000000000000000000001
                000000000000000000000000000000000000000000000000000000000000000a
                0000000000000000000000000000000000000000000000000000000000000006
                0001020304050000000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("positive BigInteger to hex") {
            Arb.bigInt(0, 256).checkAll {
                val encodedByCoder = AbiCodec.encode(listOf(AbiType.UInt(256)), listOf(it)).toHexString()
                val encodedByJava = it.toString(16).padStart(64, '0')

                encodedByCoder shouldBe encodedByJava
            }
        }
        test("negative BigInteger to hex") {
            Arb.bigInt(0, 255).checkAll {
                val num = it.negate()
                val numTwosComplement = if (num.signum() == -1) num.add(BigInteger.ONE.shiftLeft(256)) else num
                val encodedByCoder = AbiCodec.encode(listOf(AbiType.Int(256)), listOf(num)).toHexString()
                val encodedByJava = numTwosComplement.toString(16).padStart(64, '0')

                encodedByCoder shouldBe encodedByJava
            }
        }
        test("fixed array of static tuples followed by dynamic type") {
            val function = AbiFunction.parseSignature("someName((int8,int8,address)[2],string)")
            val params = listOf(
                listOf(
                    listOf(
                        BigInteger("93"),
                        BigInteger("35"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                    listOf(
                        BigInteger("124"),
                        BigInteger("-45"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                ),
                "gavofyork",
            )

            val encoded = AbiCodec.encode(
                function.inputs,
                params,
            ).toHexString()

            val expected = """
                000000000000000000000000000000000000000000000000000000000000005d
                0000000000000000000000000000000000000000000000000000000000000023
                0000000000000000000000004444444444444444444444444444444444444444
                000000000000000000000000000000000000000000000000000000000000007c
                ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd3
                0000000000000000000000002222222222222222222222222222222222222222
                00000000000000000000000000000000000000000000000000000000000000e0
                0000000000000000000000000000000000000000000000000000000000000009
                6761766f66796f726b0000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("fixed array of fixed arrays") {
            val function = AbiFunction.parseSignature("someName(address[2][2])")

            val params = listOf(
                listOf(
                    listOf(
                        Address("0x1111111111111111111111111111111111111111"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                    listOf(
                        Address("0x3333333333333333333333333333333333333333"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                ),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                0000000000000000000000001111111111111111111111111111111111111111
                0000000000000000000000002222222222222222222222222222222222222222
                0000000000000000000000003333333333333333333333333333333333333333
                0000000000000000000000004444444444444444444444444444444444444444
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("fixed array of dynamic types") {
            val function = AbiFunction.parseSignature("someName(address[][2])")

            val params = listOf(
                listOf(
                    listOf(
                        Address("0x1111111111111111111111111111111111111111"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                    listOf(
                        Address("0x3333333333333333333333333333333333333333"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                ),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                0000000000000000000000000000000000000000000000000000000000000020
                0000000000000000000000000000000000000000000000000000000000000040
                00000000000000000000000000000000000000000000000000000000000000a0
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000001111111111111111111111111111111111111111
                0000000000000000000000002222222222222222222222222222222222222222
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000003333333333333333333333333333333333333333
                0000000000000000000000004444444444444444444444444444444444444444
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("dynamic array of dynamic arrays") {
            val function = AbiFunction.parseSignature("someName(address[][])")
            val params = listOf(
                listOf(
                    listOf(
                        Address("0x1111111111111111111111111111111111111111"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                    listOf(
                        Address("0x3333333333333333333333333333333333333333"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                ),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                0000000000000000000000000000000000000000000000000000000000000020
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000000000000000000000000000000000000000000040
                00000000000000000000000000000000000000000000000000000000000000a0
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000001111111111111111111111111111111111111111
                0000000000000000000000002222222222222222222222222222222222222222
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000003333333333333333333333333333333333333333
                0000000000000000000000004444444444444444444444444444444444444444
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("combination of static and dynamic types with function name") {
            val function = AbiFunction.parseSignature("execute(bool,int256,string,int256,int256,int256,int256[],bool)")
            val params = listOf(
                true,
                BigInteger("1"),
                "gavofyork",
                BigInteger("2"),
                BigInteger("3"),
                BigInteger("4"),
                listOf(BigInteger("5"), BigInteger("6"), BigInteger("7")),
                false,
            )

            val encoded = AbiCodec.encodeWithPrefix(
                function.selector,
                function.inputs,
                params,
            ).toHexString()

            val expected = """
                31920d0e
                0000000000000000000000000000000000000000000000000000000000000001
                0000000000000000000000000000000000000000000000000000000000000001
                0000000000000000000000000000000000000000000000000000000000000100
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000000000000000000000000000000000000000000003
                0000000000000000000000000000000000000000000000000000000000000004
                0000000000000000000000000000000000000000000000000000000000000140
                0000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000009
                6761766f66796f726b0000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000003
                0000000000000000000000000000000000000000000000000000000000000005
                0000000000000000000000000000000000000000000000000000000000000006
                0000000000000000000000000000000000000000000000000000000000000007
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("empty array") {
            val function = AbiFunction.parseSignature("someName(address[],address[])")
            val params = listOf<List<String>>(
                emptyList(),
                emptyList(),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                0000000000000000000000000000000000000000000000000000000000000040
                0000000000000000000000000000000000000000000000000000000000000060
                0000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("fixed and static bytes") {
            val function = AbiFunction.parseSignature("someName(bytes,bytes32)")

            val params = listOf(
                Bytes("4444444444444444444444444444444444444444444444444444444444444444444444444444"),
                Bytes("6666666666666666666666666666666666666666666666666666666666666666"),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                0000000000000000000000000000000000000000000000000000000000000040
                6666666666666666666666666666666666666666666666666666666666666666
                0000000000000000000000000000000000000000000000000000000000000026
                4444444444444444444444444444444444444444444444444444444444444444
                4444444444440000000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("negative and positive numbers") {
            val function = AbiFunction.parseSignature("someName(int,uint,int,uint256)")

            val params = listOf(
                BigInteger("-9413"),
                BigInteger("12341"),
                BigInteger("854121"),
                BigInteger("1234885311"),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffdb3b
                0000000000000000000000000000000000000000000000000000000000003035
                00000000000000000000000000000000000000000000000000000000000d0869
                00000000000000000000000000000000000000000000000000000000499adabf
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("tuple with dynamic elements") {
            val function = AbiFunction.parseSignature("someName((string,string))")

            val params = listOf(
                listOf("gavofyork", "gavofyork"),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                0000000000000000000000000000000000000000000000000000000000000020
                0000000000000000000000000000000000000000000000000000000000000040
                0000000000000000000000000000000000000000000000000000000000000080
                0000000000000000000000000000000000000000000000000000000000000009
                6761766f66796f726b0000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000009
                6761766f66796f726b0000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("nested tuple of tuples, with dynamic and static elements") {
            val function = AbiFunction.parseSignature("someName((string,bool,string,(string,string,(string,string))))")

            val params = listOf(
                listOf("test", true, "cyborg", listOf("night", "day", listOf("weee", "funtests"))),
            )

            val encoded = AbiCodec.encode(function.inputs, params).toHexString()

            val expected = """
                0000000000000000000000000000000000000000000000000000000000000020
                0000000000000000000000000000000000000000000000000000000000000080
                0000000000000000000000000000000000000000000000000000000000000001
                00000000000000000000000000000000000000000000000000000000000000c0
                0000000000000000000000000000000000000000000000000000000000000100
                0000000000000000000000000000000000000000000000000000000000000004
                7465737400000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000006
                6379626f72670000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000060
                00000000000000000000000000000000000000000000000000000000000000a0
                00000000000000000000000000000000000000000000000000000000000000e0
                0000000000000000000000000000000000000000000000000000000000000005
                6e69676874000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000003
                6461790000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000040
                0000000000000000000000000000000000000000000000000000000000000080
                0000000000000000000000000000000000000000000000000000000000000004
                7765656500000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000008
                66756e7465737473000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "")

            encoded shouldBe expected
        }

        test("invalid fixed bytes length") {
            val function = AbiFunction.parseSignature("someName(bytes8)")

            Exhaustive.of(
                // too short
                Bytes(byteArrayOf(0, 0, 0, 0, 0, 0)),
                // too long
                Bytes(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)),
            ).checkAll {
                val params = listOf(it)
                shouldThrow<AbiCodecException> { AbiCodec.encode(function.inputs, params) }
            }
        }

        test("invalid int") {
            val function = AbiFunction.parseSignature("someName(int256)")

            Exhaustive.of(
                // too high by one
                BigInteger.ONE.shiftLeft(255),
                // too high
                BigInteger.ONE.shiftLeft(256),
                BigInteger.ONE.shiftLeft(260),
                // too low by one
                BigInteger.ONE.shiftLeft(255).add(BigInteger.ONE).negate(),
                // too low
                BigInteger.ONE.shiftLeft(256).negate(),
                BigInteger.ONE.shiftLeft(260).negate(),
            ).checkAll {
                val params = listOf(it)
                shouldThrow<AbiCodecException> { AbiCodec.encode(function.inputs, params) }
            }
        }

        test("invalid uint") {
            val function = AbiFunction.parseSignature("someName(uint256)")

            Exhaustive.of(
                // too high by one
                BigInteger.ONE.shiftLeft(256),
                // too high
                BigInteger.ONE.shiftLeft(260),
                // negative
                BigInteger.ONE.negate(),
            ).checkAll {
                val params = listOf(it)
                shouldThrow<AbiCodecException> { AbiCodec.encode(function.inputs, params) }
            }
        }
    }

    context("decoding") {
        context("failure") {
            test("decode function") {
                val function = AbiFunction.parseSignature("hello(uint256,int16)")
                listOf(
                    "", // no data
                    "dd1a544b", // only selector
                    "dd1a544b000000000000000000000000000000000000000000000000000000000000005d", // one arg only
                ).forAll { data ->
                    shouldThrow<AbiCodecException> {
                        AbiCodec.decodeWithPrefix(
                            function.selector.size,
                            function.inputs,
                            data.hexToByteArray(),
                        )
                    }
                }
            }

            test("decode input") {
                val function = AbiFunction.parseSignature("someName(uint256,int16)")
                listOf(
                    "", // no data
                    "dd1a544b", // not enough data
                    "dd1a544b000000000000000000000000000000000000000000000000000000000000005d", // one arg only
                ).forAll { data ->
                    shouldThrow<AbiCodecException> { AbiCodec.decode(function.inputs, data.hexToByteArray()) }
                }
            }

            test("decode output") {
                val function = AbiFunction.parseSignature("someName()(uint256,int16)")
                listOf(
                    "", // no data
                    "dd1a544b", // not enough data
                    "dd1a544b000000000000000000000000000000000000000000000000000000000000005d", // one arg only
                ).forAll { data ->
                    shouldThrow<AbiCodecException> { AbiCodec.decode(function.outputs, data.hexToByteArray()) }
                }
            }

            test("invalid array length should throw an exception") {
                val function = AbiFunction.parseSignature("test(uint32[0][])")
                val data = Bytes("000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000FFFFFFFF")
                shouldThrow<AbiCodecException> { function.decodeArgs(data) }
            }

            test("invalid RLP input should throw an exception") {
                val function = AbiFunction.parseSignature("test(uint32[0][])")
                val data = "0x02f904d50183098f4e84b2d05e0085011f8bdc4483042a4894def171fe48cf0115b1d80b88dc8eab59176fee5780b904642298207a0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb480000000000000000000000007fc66500c84a76ad7e9c93437bfc5ac33e2ddae9000000000000000000000000000000000000000000000000000000000207e2bf00000000000000000000000000000000000000000000000001901d03236214000000000000000000000000000000000000000000000000000000000002054c9a00000000000000000000000000000000000000000000000000000000000001e00000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000000000038000000000000000000000000000000000000000000000000000000000000003e00000000000000000000000003d92b0d1b443d76159189ff581af04da50d9bc2a0000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000042000000000000000000000000000000000000000000000000000000000686e723425b32652c50e4b56932119091c6d2d36000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000e592427a0aece92de3edee1f18e0157c058615640000000000000000000000000000000000000000000000000000000000000124f28c0498000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000def171fe48cf0115b1d80b88dc8eab59176fee57000000000000000000000000000000000000000000000000000000006877585300000000000000000000000000000000000000000000000001901d0323621400000000000000000000000000000000000000000000000000000000000207e2bf000000000000000000000000000000000000000000000000000000000000002b7fc66500c84a76ad7e9c93437bfc5ac33e2ddae9000bb8a0b86991c6218b36c1d19d4a2e9eb0ce3606eb4800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000124000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c001a0f86f63f953af3ec103691832952e3b7658c7c34af4d6ef724fcbac5bd782fb01a00a45288ffb92a3ac3540c2f394acf81ddc4369fbffd7589db0c44c16dbfe560a"
                shouldThrow<AbiCodecException> { function.decodeCall(Bytes(data)) }
            }

            test("recursion depth above limit should throw an exception") {
                val depth = "[]".repeat(17)
                val function = AbiFunction.parseSignature("test(uint32$depth)")
                val data = function.encodeCall(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf(listOf<BigInteger>()))))))))))))))))))
                shouldThrow<AbiCodecException> { function.decodeCall(data) }
            }
        }

        test("positive BigInteger from hex") {
            Arb.bigInt(0, 256).checkAll {
                val encodedByJava = it.toString(16).padStart(64, '0').hexToByteArray()
                val decoded = AbiCodec.decode(listOf(AbiType.UInt(256)), encodedByJava)[0]

                decoded shouldBeEqualComparingTo it
            }
        }
        test("negative BigInteger from hex") {
            Arb.bigInt(0, 255).checkAll {
                val num = it.negate()
                val numTwosComplement = if (num.signum() == -1) num.add(BigInteger.ONE.shiftLeft(256)) else num
                val encodedByJava = numTwosComplement.toString(16).padStart(64, '0').hexToByteArray()
                val decoded = AbiCodec.decode(listOf(AbiType.Int(256)), encodedByJava)[0]

                decoded shouldBeEqualComparingTo num
            }
        }
        test("fixed array of static tuples followed by dynamic type") {
            val function = AbiFunction.parseSignature("someName((int8,int8,address)[2],string)")

            val dataHex = """
                000000000000000000000000000000000000000000000000000000000000005d
                0000000000000000000000000000000000000000000000000000000000000023
                0000000000000000000000004444444444444444444444444444444444444444
                000000000000000000000000000000000000000000000000000000000000007c
                ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd3
                0000000000000000000000002222222222222222222222222222222222222222
                00000000000000000000000000000000000000000000000000000000000000e0
                0000000000000000000000000000000000000000000000000000000000000009
                6761766f66796f726b0000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.inputs, dataHex)

            val expected = listOf(
                listOf(
                    listOf(
                        BigInteger("93"),
                        BigInteger("35"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                    listOf(
                        BigInteger("124"),
                        BigInteger("-45"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                ),
                "gavofyork",
            )

            decoded shouldBe expected
        }

        test("fixed array of fixed arrays") {
            val function = AbiFunction.parseSignature("someName(address[2][2])")

            val dataHex = """
                0000000000000000000000001111111111111111111111111111111111111111
                0000000000000000000000002222222222222222222222222222222222222222
                0000000000000000000000003333333333333333333333333333333333333333
                0000000000000000000000004444444444444444444444444444444444444444
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.inputs, dataHex)

            val expected = listOf(
                listOf(
                    listOf(
                        Address("0x1111111111111111111111111111111111111111"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                    listOf(
                        Address("0x3333333333333333333333333333333333333333"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                ),
            )

            decoded shouldBe expected
        }

        test("fixed array of dynamic types") {
            val function = AbiFunction.parseSignature("someName(address[][2])")

            val dataHex = """
                0000000000000000000000000000000000000000000000000000000000000020
                0000000000000000000000000000000000000000000000000000000000000040
                00000000000000000000000000000000000000000000000000000000000000a0
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000001111111111111111111111111111111111111111
                0000000000000000000000002222222222222222222222222222222222222222
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000003333333333333333333333333333333333333333
                0000000000000000000000004444444444444444444444444444444444444444
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.inputs, dataHex)
            val expected = listOf(
                listOf(
                    listOf(
                        Address("0x1111111111111111111111111111111111111111"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                    listOf(
                        Address("0x3333333333333333333333333333333333333333"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                ),
            )

            decoded shouldBe expected
        }

        test("dynamic array of dynamic arrays") {
            val function = AbiFunction.parseSignature("someName(address[][])")

            val dataHex = """
                0000000000000000000000000000000000000000000000000000000000000020
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000000000000000000000000000000000000000000040
                00000000000000000000000000000000000000000000000000000000000000a0
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000001111111111111111111111111111111111111111
                0000000000000000000000002222222222222222222222222222222222222222
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000003333333333333333333333333333333333333333
                0000000000000000000000004444444444444444444444444444444444444444
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.inputs, dataHex)

            val expected = listOf(
                listOf(
                    listOf(
                        Address("0x1111111111111111111111111111111111111111"),
                        Address("0x2222222222222222222222222222222222222222"),
                    ),
                    listOf(
                        Address("0x3333333333333333333333333333333333333333"),
                        Address("0x4444444444444444444444444444444444444444"),
                    ),
                ),
            )

            decoded shouldBe expected
        }

        test("combination of static and dynamic types with function name") {
            val function = AbiFunction.parseSignature("execute(bool,int256,string,int256,int256,int256,int256[],bool)")

            val dataHex = """
                31920d0e
                0000000000000000000000000000000000000000000000000000000000000001
                0000000000000000000000000000000000000000000000000000000000000001
                0000000000000000000000000000000000000000000000000000000000000100
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000000000000000000000000000000000000000000003
                0000000000000000000000000000000000000000000000000000000000000004
                0000000000000000000000000000000000000000000000000000000000000140
                0000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000009
                6761766f66796f726b0000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000003
                0000000000000000000000000000000000000000000000000000000000000005
                0000000000000000000000000000000000000000000000000000000000000006
                0000000000000000000000000000000000000000000000000000000000000007
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decodeWithPrefix(function.selector.size, function.inputs, dataHex)

            val expected = listOf(
                true,
                BigInteger("1"),
                "gavofyork",
                BigInteger("2"),
                BigInteger("3"),
                BigInteger("4"),
                listOf(BigInteger("5"), BigInteger("6"), BigInteger("7")),
                false,
            )

            decoded shouldBe expected
        }

        test("empty array") {
            val function = AbiFunction.parseSignature("someName()(address[],address[])")

            val dataHex = """
                0000000000000000000000000000000000000000000000000000000000000040
                0000000000000000000000000000000000000000000000000000000000000060
                0000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.outputs, dataHex)

            val expected = listOf<List<String>>(
                emptyList(),
                emptyList(),
            )

            decoded shouldBe expected
        }

        test("dynamic and fixed bytes") {
            val function = AbiFunction.parseSignature("someName()(bytes,bytes32)")

            val dataHex = """
                0000000000000000000000000000000000000000000000000000000000000040
                6666666666666666666666666666666666666666666666666666666666666666
                0000000000000000000000000000000000000000000000000000000000000026
                4444444444444444444444444444444444444444444444444444444444444444
                4444444444440000000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.outputs, dataHex)

            val expected = listOf(
                Bytes("4444444444444444444444444444444444444444444444444444444444444444444444444444"),
                Bytes("6666666666666666666666666666666666666666666666666666666666666666"),
            )

            decoded shouldBe expected
        }

        test("array of dynamic and fixed bytes") {
            val function = AbiFunction.parseSignature("someName()(bytes[],bytes32[3])")

            val dataHex = """
                0000000000000000000000000000000000000000000000000000000000000080
                0101010101010101010101010101010101010101010101010101010101010101
                0202020202020202020202020202020202020202020202020202020202020202
                0303030303030303030303030303030303030303030303030303030303030303
                0000000000000000000000000000000000000000000000000000000000000002
                0000000000000000000000000000000000000000000000000000000000000040
                0000000000000000000000000000000000000000000000000000000000000080
                0000000000000000000000000000000000000000000000000000000000000002
                0102000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000002
                0304000000000000000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.outputs, dataHex)

            val expected = listOf(
                listOf(Bytes(byteArrayOf(1, 2)), Bytes(byteArrayOf(3, 4))),
                listOf(Bytes(ByteArray(32) { 1 }), Bytes(ByteArray(32) { 2 }), Bytes(ByteArray(32) { 3 })),
            )

            decoded shouldBe expected
        }

        test("nested tuple of tuples, with dynamic and static elements") {
            val function = AbiFunction.parseSignature("someName((string,bool,string,(string,string,(string,string))))")

            val dataHex = """
                0000000000000000000000000000000000000000000000000000000000000020
                0000000000000000000000000000000000000000000000000000000000000080
                0000000000000000000000000000000000000000000000000000000000000001
                00000000000000000000000000000000000000000000000000000000000000c0
                0000000000000000000000000000000000000000000000000000000000000100
                0000000000000000000000000000000000000000000000000000000000000004
                7465737400000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000006
                6379626f72670000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000060
                00000000000000000000000000000000000000000000000000000000000000a0
                00000000000000000000000000000000000000000000000000000000000000e0
                0000000000000000000000000000000000000000000000000000000000000005
                6e69676874000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000003
                6461790000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000040
                0000000000000000000000000000000000000000000000000000000000000080
                0000000000000000000000000000000000000000000000000000000000000004
                7765656500000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000008
                66756e7465737473000000000000000000000000000000000000000000000000
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.inputs, dataHex)
            val expected = listOf(
                listOf("test", true, "cyborg", listOf("night", "day", listOf("weee", "funtests"))),
            )

            decoded shouldBe expected
        }

        test("negative and positive numbers") {
            val function = AbiFunction.parseSignature("someName()(int,uint,int,uint256)")

            val dataHex = """
                ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffdb3b
                0000000000000000000000000000000000000000000000000000000000003035
                00000000000000000000000000000000000000000000000000000000000d0869
                00000000000000000000000000000000000000000000000000000000499adabf
            """.trimIndent().replace(System.lineSeparator(), "").hexToByteArray()

            val decoded = AbiCodec.decode(function.outputs, dataHex)

            val expected = listOf(
                BigInteger("-9413"),
                BigInteger("12341"),
                BigInteger("854121"),
                BigInteger("1234885311"),
            )

            decoded shouldBe expected
        }
    }

    context("encodePacked") {
        test("encode complex signature") {
            val signature = listOf(
                AbiType.FixedArray(1, AbiType.Int(16)),
                AbiType.Int(16),
                AbiType.Bool,
                AbiType.FixedBytes(12),
                AbiType.Bytes,
            )

            val params = listOf(
                listOf(BigInteger("-5")),
                BigInteger("-5"),
                true,
                Bytes("abcdef124493534081243514"),
                Bytes("abcdef12449353408124351400001240124141941358142723456789876543234567898765432345678909876543456789876543"),
            )

            val encoded = AbiCodec.encodePacked(signature, params).toString()
            encoded shouldBe "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffbfffb01abcdef124493534081243514abcdef12449353408124351400001240124141941358142723456789876543234567898765432345678909876543456789876543"
        }

        test("encode addresses in fixed array") {
            val signature = listOf(AbiType.FixedArray(2, AbiType.Address))

            val params = listOf(listOf(Address("0xabcdef1244935340812435142435142435141243"), Address.ZERO))

            val encoded = AbiCodec.encodePacked(signature, params).toString()
            encoded shouldBe "0x000000000000000000000000abcdef12449353408124351424351424351412430000000000000000000000000000000000000000000000000000000000000000"
        }

        test("encode single address") {
            val params = listOf(Address("0xabcdef1244935340812435142435142435141243"))
            val encoded = AbiCodec.encodePacked(listOf(AbiType.Address), params).toString()
            encoded shouldBe "0xabcdef1244935340812435142435142435141243"
        }

        test("encode single bool") {
            val encoded = AbiCodec.encodePacked(listOf(AbiType.Bool), listOf(true)).toString()
            encoded shouldBe "0x01"
        }

        test("encode bool in fixed array") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.FixedArray(1, AbiType.Bool)),
                listOf(listOf(true)),
            ).toString()
            encoded shouldBe "0x0000000000000000000000000000000000000000000000000000000000000001"
        }

        test("encode bytes12") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.FixedBytes(12)),
                listOf(Bytes("abcdef124493534081243514")),
            ).toString()

            encoded shouldBe "0xabcdef124493534081243514"
        }

        test("encode bytes12 in fixed array") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.FixedArray(1, AbiType.FixedBytes(12))),
                listOf(listOf(Bytes("abcdef124493534081243514"))),
            ).toString()

            encoded shouldBe "0xabcdef1244935340812435140000000000000000000000000000000000000000"
        }

        test("encode single positive int16") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.Int(16)),
                listOf(BigInteger("5")),
            ).toString()

            encoded shouldBe "0x0005"
        }

        test("encode single positive int16 in fixed array") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.FixedArray(1, AbiType.Int(16))),
                listOf(listOf(BigInteger("5"))),
            ).toString()

            encoded shouldBe "0x0000000000000000000000000000000000000000000000000000000000000005"
        }

        test("encode single negative max value int256") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.Int(256)),
                listOf(BigInteger.TWO.pow(255) - BigInteger.ONE),
            ).toString()

            encoded shouldBe "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        }

        test("encode single negative max value int256 in fixed array") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.FixedArray(1, AbiType.Int(256))),
                listOf(listOf(BigInteger.TWO.pow(255) - BigInteger.ONE)),
            ).toString()

            encoded shouldBe "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        }

        test("encode single value uint256") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.UInt(256)),
                listOf(BigInteger("5")),
            ).toString()

            encoded shouldBe "0x0000000000000000000000000000000000000000000000000000000000000005"
        }

        test("encode single value uint16") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.UInt(16)),
                listOf(BigInteger("5")),
            ).toString()

            encoded shouldBe "0x0005"
        }

        test("encode single value uint16 in fixed array") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.FixedArray(1, AbiType.UInt(16))),
                listOf(listOf(BigInteger("5"))),
            ).toString()

            encoded shouldBe "0x0000000000000000000000000000000000000000000000000000000000000005"
        }

        test("encode single max value uint256") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.UInt(256)),
                listOf(BigInteger.TWO.pow(256) - BigInteger.ONE),
            ).toString()

            encoded shouldBe "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        }

        test("encode single max value uint256 in fixed array") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.FixedArray(1, AbiType.UInt(256))),
                listOf(listOf(BigInteger.TWO.pow(256) - BigInteger.ONE)),
            ).toString()

            encoded shouldBe "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        }

        test("encode single max value uint256 in dynamic array") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.Array(AbiType.UInt(256))),
                listOf(listOf(BigInteger.TWO.pow(256) - BigInteger.ONE)),
            ).toString()

            encoded shouldBe "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        }

        test("encode single string") {
            val encoded = AbiCodec.encodePacked(
                listOf(AbiType.String),
                listOf("hello from a single string"),
            ).toString()

            encoded shouldBe "0x68656c6c6f2066726f6d20612073696e676c6520737472696e67"
        }

        data class FailCase(val description: String, val signature: List<AbiType<*>>, val params: List<Any>)

        listOf(
            FailCase(
                "tuple not supported",
                listOf(AbiType.Tuple(AbiType.Int(256))),
                listOf(listOf(BigInteger.ONE)),
            ),
            FailCase(
                "nested array not supported",
                listOf(AbiType.Array(AbiType.Array(AbiType.String))),
                listOf(listOf("fail")),
            ),
            FailCase(
                "nested array in fixed array not supported",
                listOf(AbiType.FixedArray(1, AbiType.Array(AbiType.String))),
                listOf(listOf("fail")),
            ),
            FailCase(
                "array of dynamic type not supported",
                listOf(AbiType.Array(AbiType.String)),
                listOf(listOf("fail")),
            ),
            FailCase(
                "fixed array of dynamic type not supported",
                listOf(AbiType.FixedArray(1, AbiType.String)),
                listOf(listOf("fail")),
            ),
        ).forAll { (description, signature, params) ->
            test(description) {
                shouldThrow<AbiCodecException> {
                    AbiCodec.encodePacked(signature, params)
                }
            }
        }
    }
})
