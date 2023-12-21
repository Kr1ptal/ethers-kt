package io.ethers.abigen.cases

import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.ArgDescriptor
import io.ethers.abigen.ClassDescriptor
import io.ethers.abigen.FunctionDescriptor
import io.ethers.abigen.getAbiFunctionField
import io.ethers.abigen.getDeclaredErrors
import io.ethers.abigen.getDeclaredFunctions
import io.ethers.abigen.getDeclaredStructs
import io.ethers.abigen.parametrizedBy
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class OffchainResolverTest : FunSpec({
    context("abigen validation") {
        val clazz = AbigenCompiler.compile("/abi/OffchainResolver.json")

        test("class name") {
            clazz.simpleName shouldBe "OffchainResolver"
        }

        test("functions") {
            clazz.getDeclaredFunctions() shouldContainAll listOf(
                FunctionDescriptor(
                    "resolve",
                    listOf(
                        ArgDescriptor("name", Bytes::class),
                        ArgDescriptor("data", Bytes::class),
                    ),
                    ReadFunctionCall::class.parametrizedBy(Bytes::class),
                ),
            )
        }

        test("structs") {
            clazz.getDeclaredStructs().classes.size shouldBe 0
        }

        test("errors") {
            val errors = clazz.getDeclaredErrors()
            errors.classes.size shouldBe 1

            errors.descriptors shouldContainAll listOf(
                ClassDescriptor(
                    "OffchainLookup",
                    listOf(
                        ArgDescriptor("sender", Address::class),
                        ArgDescriptor("urls", Array::class.parametrizedBy(String::class)),
                        ArgDescriptor("callData", Bytes::class),
                        ArgDescriptor("callbackFunction", Bytes::class),
                        ArgDescriptor("extraData", Bytes::class),
                    ),
                ),
            )
        }

        test("AbiFunction field") {
            clazz.getAbiFunctionField("FUNCTION_RESOLVE") shouldBe AbiFunction(
                name = "resolve",
                inputs = listOf(
                    AbiType.Bytes,
                    AbiType.Bytes,
                ),
                outputs = listOf(AbiType.Bytes),
            )
        }
    }
})
