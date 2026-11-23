package io.ethers.aa.bundle

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class StandardCallDataDecoderTest : FunSpec({
    val sender = Address("0x1111111111111111111111111111111111111111")
    val a = Call(Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), bigIntegerOf(1), Bytes("0x01"))
    val b = Call(Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), bigIntegerOf(0), Bytes("0x0203"))

    fun decode(data: Bytes) = StandardCallDataDecoder.decodeCalls(sender, data)

    test("execute(address,uint256,bytes)") {
        decode(AbiFunction.parseSignature("execute(address,uint256,bytes)").encodeCall(listOf(a.to, a.value, a.data))) shouldBe listOf(a)
    }

    test("executeBatch((address,uint256,bytes)[])") {
        val data = AbiFunction.parseSignature("executeBatch((address,uint256,bytes)[])")
            .encodeCall(listOf(listOf(listOf(a.to, a.value, a.data), listOf(b.to, b.value, b.data))))
        decode(data) shouldBe listOf(a, b)
    }

    test("executeBatch(address[],uint256[],bytes[]), with and without values") {
        val function = AbiFunction.parseSignature("executeBatch(address[],uint256[],bytes[])")
        decode(function.encodeCall(listOf(listOf(a.to, b.to), listOf(a.value, b.value), listOf(a.data, b.data)))) shouldBe listOf(a, b)
        decode(function.encodeCall(listOf(listOf(a.to), emptyList<Any>(), listOf(a.data)))) shouldBe listOf(a.copy(value = bigIntegerOf(0)))
        decode(function.encodeCall(listOf(listOf(a.to, b.to), listOf(a.value), listOf(a.data, b.data)))).shouldBeNull()
    }

    test("executeBatch(address[],bytes[])") {
        val data = AbiFunction.parseSignature("executeBatch(address[],bytes[])").encodeCall(listOf(listOf(b.to), listOf(b.data)))
        decode(data) shouldBe listOf(b)
    }

    context("ERC-7579 execute(bytes32,bytes)") {
        val function = AbiFunction.parseSignature("execute(bytes32,bytes)")
        fun mode(callType: Int) = Bytes(ByteArray(32).also { it[0] = callType.toByte() })

        test("single call") {
            val executionData = Bytes(
                a.to.asByteArray() + AbiCodec.encode(AbiType.UInt(256), a.value) + a.data.asByteArray(),
            )
            decode(function.encodeCall(listOf(mode(0x00), executionData))) shouldBe listOf(a)
        }

        test("batch") {
            val executions = AbiCodec.encode(
                AbiType.Array(AbiType.Tuple(AbiType.Address, AbiType.UInt(256), AbiType.Bytes)),
                listOf(listOf(a.to, a.value, a.data), listOf(b.to, b.value, b.data)),
            )
            decode(function.encodeCall(listOf(mode(0x01), Bytes(executions)))) shouldBe listOf(a, b)
        }

        test("delegatecall is not a plain call") {
            decode(function.encodeCall(listOf(mode(0xff), Bytes(ByteArray(52))))).shouldBeNull()
        }
    }

    test("executeUserOp prefix is unwrapped") {
        val inner = AbiFunction.parseSignature("execute(address,uint256,bytes)").encodeCall(listOf(a.to, a.value, a.data))
        decode(Bytes(Bytes("0x8dd7712f").asByteArray() + inner.asByteArray())) shouldBe listOf(a)
    }

    test("unknown or malformed call data decodes to null") {
        decode(Bytes("0x12345678")).shouldBeNull()
        decode(Bytes("0x12")).shouldBeNull()
        decode(Bytes("0xb61d27f60000")).shouldBeNull()
    }
})
