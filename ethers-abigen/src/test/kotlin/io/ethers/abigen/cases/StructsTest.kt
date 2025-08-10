package io.ethers.abigen.cases

import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.StructFactory
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.ArgDescriptor
import io.ethers.abigen.ClassDescriptor
import io.ethers.abigen.getDeclaredStructs
import io.ethers.abigen.nestedClass
import io.ethers.abigen.parametrizedBy
import io.ethers.abigen.typedNestedClass
import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.primaryConstructor

class StructsTest : FunSpec({
    context("abigen validation: Structs") {
        val clazz = AbigenCompiler.getContract("Structs")

        val (classes, descriptors) = clazz.getDeclaredStructs()

        test("all structs should have a factory") {
            classes.all { it.companionObjectInstance is StructFactory<*> } shouldBe true
        }

        test("all factories have correct abi signature") {
            val factories = classes.map { it.companionObjectInstance as StructFactory<*> }
            factories.map { it.abi.type } shouldContainExactlyInAnyOrder listOf(
                AbiType.Tuple.struct(
                    clazz.typedNestedClass("Simple"),
                    AbiType.Bool,
                    AbiType.Bytes,
                ),
                AbiType.Tuple.struct(
                    clazz.typedNestedClass("Complex"),
                    AbiType.Array(AbiType.Array(AbiType.Array(AbiType.UInt(256)))),
                    AbiType.FixedArray(3, AbiType.String),
                ),
                AbiType.Tuple.struct(
                    clazz.typedNestedClass("Nested"),
                    AbiType.String,
                    AbiType.Tuple.struct(
                        clazz.typedNestedClass("Simple"),
                        AbiType.Bool,
                        AbiType.Bytes,
                    ),
                    AbiType.Tuple.struct(
                        clazz.typedNestedClass("Complex"),
                        AbiType.Array(AbiType.Array(AbiType.Array(AbiType.UInt(256)))),
                        AbiType.FixedArray(3, AbiType.String),
                    ),
                ),
                AbiType.Tuple.struct(
                    clazz.typedNestedClass("classStruct"),
                    AbiType.String,
                ),
                AbiType.Tuple.struct(
                    clazz.typedNestedClass("packageStruct"),
                    AbiType.String,
                ),
            )
        }

        test("nested struct encodes/decodes correctly") {
            val nested = clazz.nestedClass("Nested")
            val simple = clazz.nestedClass("Simple")
            val complex = clazz.nestedClass("Complex")

            val desc = "hello"
            val simpleInstance = simple.primaryConstructor!!.call(true, Bytes("0x12345654123763afed"))
            val complexInstance = complex.primaryConstructor!!.call(
                listOf(listOf(listOf(BigInteger("1"), BigInteger("2")))),
                listOf("hello", "world", "evm"),
            )

            val instance = nested.primaryConstructor!!.call(desc, simpleInstance, complexInstance) as ContractStruct

            val expected = listOf(desc, simpleInstance, complexInstance)
            instance.tuple shouldBe expected

            val factory = nested.companionObjectInstance!! as StructFactory<*>
            factory.fromTuple(instance.tuple) shouldBe instance
            factory.fromTuple(expected) shouldBe instance
        }

        test("all structs are generated, with correct argument names and types") {
            descriptors shouldContainExactlyInAnyOrder listOf(
                ClassDescriptor(
                    "Simple",
                    listOf(
                        ArgDescriptor("success", Boolean::class),
                        ArgDescriptor("data", Bytes::class),
                    ),
                ),
                ClassDescriptor(
                    "Complex",
                    listOf(
                        ArgDescriptor(
                            "status",
                            List::class.parametrizedBy(
                                List::class.parametrizedBy(
                                    List::class.parametrizedBy(BigInteger::class),
                                ),
                            ),
                        ),
                        ArgDescriptor("msg", List::class.parametrizedBy(String::class)),
                    ),
                ),
                ClassDescriptor(
                    "Nested",
                    listOf(
                        ArgDescriptor("desc", String::class),
                        ArgDescriptor("simple", clazz.nestedClass("Simple")),
                        ArgDescriptor("complex", clazz.nestedClass("Complex")),
                    ),
                ),
                ClassDescriptor(
                    "classStruct",
                    listOf(ArgDescriptor("desc", String::class)),
                ),
                ClassDescriptor(
                    "packageStruct",
                    listOf(ArgDescriptor("desc", String::class)),
                ),
            )
        }
    }
})
