package io.ethers.abigen.cases

import io.ethers.abi.AbiFunction
import io.ethers.abi.call.FunctionCall
import io.ethers.abi.call.PayableFunctionCall
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.ArgDescriptor
import io.ethers.abigen.FunctionDescriptor
import io.ethers.abigen.getAbiFunctionField
import io.ethers.abigen.getDeclaredFunctions
import io.ethers.abigen.nestedClass
import io.ethers.abigen.parametrizedBy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class FunctionsTest : FunSpec({
    context("abigen validation: Functions") {
        val clazz = AbigenCompiler.getContract("Functions")

        test("class name") {
            clazz.simpleName shouldBe "Functions"
        }

        test("functions") {
            clazz.getDeclaredFunctions() shouldContainAll listOf(
                FunctionDescriptor(
                    "noArgs",
                    emptyList(),
                    FunctionCall::class.parametrizedBy(Unit::class),
                ),
                FunctionDescriptor(
                    "noArgsReturns",
                    emptyList(),
                    FunctionCall::class.parametrizedBy(BigInteger::class),
                ),
                FunctionDescriptor(
                    "noArgsMultiReturns",
                    emptyList(),
                    FunctionCall::class.parametrizedBy(clazz.nestedClass("NoArgsMultiReturnsResult")),
                ),
                FunctionDescriptor(
                    "simpleArgs",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                    ),
                    FunctionCall::class.parametrizedBy(Unit::class),
                ),
                FunctionDescriptor(
                    "complexArgs",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor(
                            "details",
                            List::class.parametrizedBy(List::class.parametrizedBy(clazz.nestedClass("Details"))),
                        ),
                    ),
                    FunctionCall::class.parametrizedBy(BigInteger::class),
                ),
                FunctionDescriptor(
                    "complexArgs2",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor(
                            "differentName",
                            List::class.parametrizedBy(List::class.parametrizedBy(clazz.nestedClass("Details"))),
                        ),
                    ),
                    FunctionCall::class.parametrizedBy(BigInteger::class),
                ),
                FunctionDescriptor(
                    "overloaded",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                    ),
                    FunctionCall::class.parametrizedBy(Unit::class),
                ),
                FunctionDescriptor(
                    "overloaded",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                        ArgDescriptor("done", Boolean::class),
                    ),
                    FunctionCall::class.parametrizedBy(Unit::class),
                ),
                FunctionDescriptor(
                    "get_dy",
                    listOf(
                        ArgDescriptor("i", BigInteger::class),
                        ArgDescriptor("j", BigInteger::class),
                        ArgDescriptor("dx", BigInteger::class),
                    ),
                    ReadFunctionCall::class.parametrizedBy(BigInteger::class),
                ),
                FunctionDescriptor(
                    "get_dy_0x5e0d443f",
                    listOf(
                        ArgDescriptor("i", BigInteger::class),
                        ArgDescriptor("j", BigInteger::class),
                        ArgDescriptor("dx", BigInteger::class),
                    ),
                    ReadFunctionCall::class.parametrizedBy(BigInteger::class),
                ),
                // ---------------- RESERVED KEYWORDS ----------------
                FunctionDescriptor(
                    "_class",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                    ),
                    FunctionCall::class.parametrizedBy(Unit::class),
                ),
                FunctionDescriptor(
                    "_package",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                    ),
                    FunctionCall::class.parametrizedBy(Unit::class),
                ),
                // ---------------------------------------------------
                FunctionDescriptor(
                    "payableArgs",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                    ),
                    PayableFunctionCall::class.parametrizedBy(Unit::class),
                ),
                FunctionDescriptor(
                    "viewArgs",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                    ),
                    ReadFunctionCall::class.parametrizedBy(Unit::class),
                ),
            )
        }

        test("AbiFunction fields") {
            clazz.getAbiFunctionField("FUNCTION_GET_E2_MODE_CATEGORY_DATA") shouldBe AbiFunction(
                "getE2ModeCategoryData",
                emptyList(),
                emptyList(),
            )

            clazz.getAbiFunctionField("FUNCTION_JSON_ABI") shouldBe AbiFunction(
                "JsonABI",
                emptyList(),
                emptyList(),
            )

            clazz.getAbiFunctionField("FUNCTION_REPAY_WITH_A_TOKENS") shouldBe AbiFunction(
                "repayWithATokens",
                emptyList(),
                emptyList(),
            )
        }
    }
})
