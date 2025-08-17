package io.ethers.abigen.cases

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiEvent
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.ArgDescriptor
import io.ethers.abigen.ClassDescriptor
import io.ethers.abigen.getDeclaredEvents
import io.ethers.abigen.nestedClass
import io.ethers.abigen.typedNestedClass
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

class EventsTest : FunSpec({
    context("abigen validation: Events") {
        val clazz = AbigenCompiler.getContract("Events")

        val (baseClass, classes, descriptors, factories) = clazz.getDeclaredEvents()

        test("all implement base sealed class") {
            classes.all { it.isSubclassOf(baseClass) } shouldBe true
        }

        test("event with topics and struct in data decoded correctly") {
            val indexedAndDataArgsEvent = factories.single {
                it.abi.name == "IndexedAndDataArgsEvent" && it.abi.tokens.size == 3
            }
            val eventClass = classes[factories.indexOf(indexedAndDataArgsEvent)]
            val detailsClass = clazz.typedNestedClass<ContractStruct>("Details")
            val detailsStruct = detailsClass.primaryConstructor!!.call(false, Bytes("0x123456"))

            val topic1 = BigInteger("1221452413")
            val errorCode = BigInteger("123")
            val logData = AbiCodec.encode(
                listOf(
                    AbiType.UInt(16),
                    AbiType.Struct(
                        clazz.typedNestedClass("Details"),
                        AbiType.Struct.Field("success", AbiType.Bool),
                        AbiType.Struct.Field("data", AbiType.Bytes),
                    ),
                ),
                listOf(errorCode, detailsStruct),
            )

            val log = Log(
                Address.ZERO,
                listOf(
                    indexedAndDataArgsEvent.abi.topicId,
                    Hash(AbiCodec.encode(AbiType.UInt(256), topic1)),
                ),
                Bytes(logData),
                Hash.ZERO,
                1L,
                1L,
                Hash.ZERO,
                1,
                1,
                false,
            )

            val decoded = indexedAndDataArgsEvent.decode(log)
            val expected = eventClass.primaryConstructor!!.call(
                topic1,
                errorCode,
                detailsStruct,
                log,
            )

            decoded shouldBe expected
        }

        test("all factories have correct abi signature") {
            factories.map { it.abi } shouldContainExactlyInAnyOrder listOf(
                AbiEvent(
                    "AnonymousEvent",
                    listOf(AbiEvent.Token(AbiType.UInt(256), true), AbiEvent.Token(AbiType.FixedBytes(32), true)),
                    true,
                ),
                AbiEvent(
                    "ComplexIndexedArgsEvent",
                    listOf(AbiEvent.Token(AbiType.UInt(256), true), AbiEvent.Token(AbiType.FixedBytes(32), true)),
                    false,
                ),
                // ----------------- OVERLOADS -----------------
                AbiEvent(
                    "IndexedAndDataArgsEvent",
                    listOf(
                        AbiEvent.Token(AbiType.UInt(256), true),
                        AbiEvent.Token(
                            AbiType.Struct(
                                clazz.typedNestedClass("Details"),
                                AbiType.Struct.Field("success", AbiType.Bool),
                                AbiType.Struct.Field("data", AbiType.Bytes),
                            ),
                            false,
                        ),
                    ),
                    false,
                ),
                AbiEvent(
                    "IndexedAndDataArgsEvent",
                    listOf(
                        AbiEvent.Token(AbiType.UInt(256), true),
                        AbiEvent.Token(AbiType.UInt(16), false),
                        AbiEvent.Token(
                            AbiType.Struct(
                                clazz.typedNestedClass("Details"),
                                AbiType.Struct.Field("success", AbiType.Bool),
                                AbiType.Struct.Field("data", AbiType.Bytes),
                            ),
                            false,
                        ),
                    ),
                    false,
                ),
                // ---------------------------------------------
                AbiEvent("NoArgsEvent", listOf(), false),
                AbiEvent(
                    "NoIndexedArgsEvent",
                    listOf(
                        AbiEvent.Token(AbiType.UInt(256), false),
                        AbiEvent.Token(AbiType.String, false),
                    ),
                    false,
                ),
                AbiEvent(
                    "OnlyIndexedArgsEvent",
                    listOf(
                        AbiEvent.Token(AbiType.UInt(256), true),
                        AbiEvent.Token(AbiType.Bool, true),
                    ),
                    false,
                ),
                AbiEvent(
                    "StructIndexedArgsEvent",
                    listOf(
                        AbiEvent.Token(AbiType.UInt(256), true),
                        AbiEvent.Token(AbiType.FixedBytes(32), true),
                    ),
                    false,
                ),
            )
        }

        test("all events are generated, with correct argument names and types") {
            descriptors shouldContainExactlyInAnyOrder listOf(
                ClassDescriptor(
                    "AnonymousEvent",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", Bytes::class),
                        ArgDescriptor("log", Log::class),
                    ),
                ),
                ClassDescriptor(
                    "ComplexIndexedArgsEvent",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", Bytes::class),
                        ArgDescriptor("log", Log::class),
                    ),
                ),
                // ----------------- OVERLOADS -----------------
                ClassDescriptor(
                    "IndexedAndDataArgsEvent",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("details", clazz.nestedClass("Details")),
                        ArgDescriptor("log", Log::class),
                    ),
                ),
                ClassDescriptor(
                    "IndexedAndDataArgsEvent_1",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("errorCode", BigInteger::class),
                        ArgDescriptor("details", clazz.nestedClass("Details")),
                        ArgDescriptor("log", Log::class),
                    ),
                ),
                // ---------------------------------------------
                ClassDescriptor(
                    "NoArgsEvent",
                    listOf(ArgDescriptor("log", Log::class)),
                ),
                ClassDescriptor(
                    "NoIndexedArgsEvent",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("msg", String::class),
                        ArgDescriptor("log", Log::class),
                    ),
                ),
                ClassDescriptor(
                    "OnlyIndexedArgsEvent",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("done", Boolean::class),
                        ArgDescriptor("log", Log::class),
                    ),
                ),
                ClassDescriptor(
                    "StructIndexedArgsEvent",
                    listOf(
                        ArgDescriptor("status", BigInteger::class),
                        ArgDescriptor("details", Bytes::class),
                        ArgDescriptor("log", Log::class),
                    ),
                ),
            )
        }
    }
})
