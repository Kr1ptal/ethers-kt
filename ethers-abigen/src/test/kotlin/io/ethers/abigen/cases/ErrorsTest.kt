package io.ethers.abigen.cases

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.ArgDescriptor
import io.ethers.abigen.ClassDescriptor
import io.ethers.abigen.getDeclaredErrors
import io.ethers.abigen.nestedClass
import io.ethers.abigen.parametrizedBy
import io.ethers.abigen.typedArrayOf
import io.ethers.abigen.typedNestedClass
import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

class ErrorsTest : FunSpec({
    context("abigen validation: Errors") {
        val clazz = AbigenCompiler.getContract("Errors")

        val (baseClass, classes, descriptors, factories) = clazz.getDeclaredErrors()

        test("all implement base sealed class") {
            classes.all { it.isSubclassOf(baseClass) } shouldBe true
        }

        test("error with struct decoded correctly") {
            val structArgsError = factories.single { it.abi.name == "StructArgsError" }

            val detailsClass = clazz.typedNestedClass<ContractStruct>("Details")
            val detailsStruct1 = detailsClass.primaryConstructor!!.call(false, Bytes("0x123456"))
            val detailsStruct2 = detailsClass.primaryConstructor!!.call(false, Bytes("0xabcdef"))

            val encoded = AbiCodec.encodeWithPrefix(
                structArgsError.abi.selector,
                listOf(
                    AbiType.UInt(256),
                    AbiType.Array(
                        AbiType.Tuple.struct(detailsClass, AbiType.Bool, AbiType.Bytes),
                    ),
                ),
                arrayOf(
                    BigInteger("123"),
                    arrayOf(
                        detailsStruct1,
                        detailsStruct2,
                    ),
                ),
            )

            val expected = clazz.nestedClass("StructArgsError").primaryConstructor!!.call(
                BigInteger("123"),
                detailsClass.typedArrayOf(detailsStruct1, detailsStruct2),
            )

            structArgsError.decode(Bytes(encoded)) shouldBe expected
        }

        test("all factories have correct abi signature") {
            factories.map { it.abi } shouldContainExactlyInAnyOrder listOf(
                AbiFunction("NoArgsError", listOf(), emptyList()),
                AbiFunction(
                    "SimpleArgsError",
                    listOf(
                        AbiType.UInt(256),
                        AbiType.String,
                        AbiType.Bool,
                    ),
                    emptyList(),
                ),
                AbiFunction(
                    "ComplexArgsError",
                    listOf(
                        AbiType.Array(AbiType.Array(AbiType.Array(AbiType.UInt(256)))),
                        AbiType.FixedArray(3, AbiType.String),
                    ),
                    emptyList(),
                ),
                AbiFunction(
                    "StructArgsError",
                    listOf(
                        AbiType.UInt(256),
                        AbiType.Array(
                            AbiType.Tuple.struct(
                                clazz.typedNestedClass("Details"),
                                AbiType.Bool,
                                AbiType.Bytes,
                            ),
                        ),
                    ),
                    emptyList(),
                ),
            )
        }

        test("all events are generated, with correct argument names and types") {
            descriptors shouldContainExactlyInAnyOrder listOf(
                ClassDescriptor("NoArgsError", listOf()),
                ClassDescriptor(
                    "SimpleArgsError",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                        ArgDescriptor("done", Boolean::class),
                    ),
                ),
                ClassDescriptor(
                    "ComplexArgsError",
                    listOf(
                        ArgDescriptor(
                            "status",
                            Array::class.parametrizedBy(
                                Array::class.parametrizedBy(
                                    Array::class.parametrizedBy(BigInteger::class),
                                ),
                            ),
                        ),
                        ArgDescriptor("msg", Array::class.parametrizedBy(String::class)),
                    ),
                ),
                ClassDescriptor(
                    "StructArgsError",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("details", Array::class.parametrizedBy(clazz.nestedClass("Details"))),
                    ),
                ),
            )
        }
    }
})
