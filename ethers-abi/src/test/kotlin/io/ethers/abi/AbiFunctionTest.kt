package io.ethers.abi

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AbiFunctionTest : FunSpec({
    test("different arguments should produce different method id") {
        val coder1 = AbiFunction.parseSignature("flashLoan(uint16)")
        val coder2 = AbiFunction.parseSignature("flashLoan(uint256)")

        coder1.selector shouldNotBe coder2.selector
    }

    listOf(
        TestCase("noArgsFunction()", "noArgsFunction", null, null),
        TestCase(
            "hello(uint256,address)",
            "hello",
            listOf(AbiType.UInt(256), AbiType.Address),
            null,
        ),
        TestCase(
            "callSomething(uint256,address)(address,    uint256)",
            "callSomething",
            listOf(AbiType.UInt(256), AbiType.Address),
            listOf(AbiType.Address, AbiType.UInt(256)),
        ),
        TestCase(
            "onlyReturnValues()(uint256,address)",
            "onlyReturnValues",
            null,
            listOf(AbiType.UInt(256), AbiType.Address),
        ),
        TestCase(
            "  function    fullAbiSignature(address,  uint256, bytes[])   returns(uint256,address)",
            "fullAbiSignature",
            listOf(AbiType.Address, AbiType.UInt(256), AbiType.Array(AbiType.Bytes)),
            listOf(AbiType.UInt(256), AbiType.Address),
        ),
        TestCase(
            "inputOutputEmpty()()",
            "inputOutputEmpty",
            null,
            null,
        ),
        TestCase(
            "complexSignature(address,int256[],uint64[2],(string,bytes12))",
            "complexSignature",
            listOf(
                AbiType.Address,
                AbiType.Array(AbiType.Int(256)),
                AbiType.FixedArray(2, AbiType.UInt(64)),
                AbiType.Tuple.raw(AbiType.String, AbiType.FixedBytes(12)),
            ),
            null,
        ),
        TestCase(
            "complexSignatureWithOutput(address, int256[],  uint64[2],(string,bytes12)) ((address , string))",
            "complexSignatureWithOutput",
            listOf(
                AbiType.Address,
                AbiType.Array(AbiType.Int(256)),
                AbiType.FixedArray(2, AbiType.UInt(64)),
                AbiType.Tuple.raw(AbiType.String, AbiType.FixedBytes(12)),
            ),
            listOf(AbiType.Tuple.raw(AbiType.Address, AbiType.String)),
        ),
    ).forAll { (signature, name, inputs, outputs) ->
        test("parse valid signature: '$signature'") {
            val parsed = AbiFunction.parseSignature(signature)
            parsed shouldBe AbiFunction(name, inputs ?: emptyList(), outputs ?: emptyList())
        }
    }

    listOf(
        "hello(int8)" to "68006de4",
        "complexSignatureWithOutput(address, int256[],  uint64[2],(string,bytes12)) ((address , string))" to "a02c8e7b",
        "inputOutputEmpty()()" to "4a1be7ed",
        "noBitsInts(int,uint,int16)" to "81c9c8a5",
    ).forAll { (signature, selector) ->
        test("4byte selector of '$signature': '$selector'") {
            AbiFunction.parseSignature(signature).selector.toHexString() shouldBe selector
        }
    }

    listOf(
        "",
        "()",
        "()()",
        "functionName",
        "()functionName",
        "()functionName()",
        "()()functionName",
        "(uint256   ,  address)",
        "(uint256,address)()",
    ).forAll { signature ->
        test("fail invalid signature: '$signature'") {
            shouldThrow<IllegalArgumentException> { AbiFunction.parseSignature(signature) }
        }
    }
}) {
    private data class TestCase(
        val signature: String,
        val name: String,
        val inputs: List<AbiType>?,
        val outputs: List<AbiType>?,
    )
}
