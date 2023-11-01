package io.ethers.abigen.cases

import io.ethers.abi.call.FunctionCall
import io.ethers.abi.call.PayableFunctionCall
import io.ethers.abi.call.ReceiveFunctionCall
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.parametrizedBy
import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredFunctions

class ReceiveFallbackTest : FunSpec({
    context("abigen validation") {
        val clazz = AbigenCompiler.compile("/abi/ReceiveAndFallback.json")
        val clazzNonPayableFallback = AbigenCompiler.compile(
            "/abi/NonpayableFallback.json",
        )

        context("functions") {
            test("has payable receive function") {
                val function = clazz.declaredFunctions.single { it.name == "receive" }

                function.parameters[1].type shouldBe BigInteger::class.createType()
                function.returnType shouldBe ReceiveFunctionCall::class.createType()
            }

            test("has payable fallback function") {
                val function = clazz.declaredFunctions.single { it.name == "fallback" }

                function.parameters[1].type shouldBe Bytes::class.createType()
                function.returnType shouldBe PayableFunctionCall::class.parametrizedBy(Bytes::class)
            }

            test("another contract has non-payable fallback function") {
                val function = clazzNonPayableFallback.declaredFunctions.single { it.name == "fallback" }

                function.parameters[1].type shouldBe Bytes::class.createType()
                function.returnType shouldBe FunctionCall::class.parametrizedBy(Bytes::class)
            }
        }
    }
})
