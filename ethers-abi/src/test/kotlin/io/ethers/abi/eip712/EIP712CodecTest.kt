package io.ethers.abi.eip712

import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.Header
import io.ethers.abi.Inbox
import io.ethers.abi.Mail
import io.ethers.abi.Person
import io.ethers.core.types.Address
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.function.Function

class EIP712CodecTest : FunSpec({
    context("encodeType") {
        data class TestStruct(val data: String = "") : ContractStruct {
            override val tuple: List<Any> = listOf(data)
            override val abiType: AbiType.Struct<*>
                get() = throw NotImplementedError("Not needed for cycle detection tests")
        }

        test("returns correct string for non-nested struct") {
            val person = Person(Address.ZERO, "John")

            EIP712Codec.encodeType(person) shouldBe "Person(address wallet,string name)"
        }

        test("creates correct string for nested structs") {
            val from = Person(Address.ZERO, "Bob")
            val to = Person(Address.ZERO, "Alice")
            val contents = "Some random string"
            val mail = Mail(from, to, contents, Header("Header"))

            EIP712Codec.encodeType(mail) shouldBe "Mail(Person from,Person to,string contents,Header header)Header(string header)Person(address wallet,string name)"
        }

        test("creates correct properly sorted string for multiple nested structs") {
            val from = Person(Address.ZERO, "Bob")
            val to = Person(Address.ZERO, "Alice")
            val contents = "Some random string"
            val mail = Mail(from, to, contents, Header("Header"))
            val inbox = Inbox("Test Inbox", listOf(mail))

            EIP712Codec.encodeType(inbox) shouldBe "Inbox(string name,Mail[] mails)Header(string header)Mail(Person from,Person to,string contents,Header header)Person(address wallet,string name)"
        }

        test("detects self-referencing cycle") {
            // Create a simple self-referencing struct type
            val fields = mutableListOf<AbiType.Struct.Field>()
            val selfRefType = AbiType.Struct(
                TestStruct::class.java,
                Function { TestStruct() },
                fields,
            )

            // Add self-reference
            fields.add(AbiType.Struct.Field("value", AbiType.String))
            fields.add(AbiType.Struct.Field("self", selfRefType))

            val exception = shouldThrow<IllegalStateException> {
                EIP712Codec.encodeType(selfRefType)
            }
            exception.message shouldContain "TestStruct"
        }

        test("detects two-level cycle") {
            // Create mutually referencing struct types
            val fieldsA = mutableListOf<AbiType.Struct.Field>()
            val fieldsB = mutableListOf<AbiType.Struct.Field>()

            val typeA = AbiType.Struct(
                TestStruct::class.java,
                Function { TestStruct() },
                fieldsA,
            )

            val typeB = AbiType.Struct(
                TestStruct::class.java,
                Function { TestStruct() },
                fieldsB,
            )

            // Set up the cycle: A -> B -> A
            fieldsA.add(AbiType.Struct.Field("name", AbiType.String))
            fieldsA.add(AbiType.Struct.Field("b", typeB))

            fieldsB.add(AbiType.Struct.Field("name", AbiType.String))
            fieldsB.add(AbiType.Struct.Field("a", typeA))

            val exception = shouldThrow<IllegalStateException> {
                EIP712Codec.encodeType(typeA)
            }
            exception.message shouldContain "TestStruct"
        }

        test("detects cycle through array") {
            // Create mutually referencing struct types through array
            val fieldsA = mutableListOf<AbiType.Struct.Field>()
            val fieldsB = mutableListOf<AbiType.Struct.Field>()

            val typeA = AbiType.Struct(
                TestStruct::class.java,
                Function { TestStruct() },
                fieldsA,
            )

            val typeB = AbiType.Struct(
                TestStruct::class.java,
                Function { TestStruct() },
                fieldsB,
            )

            // Set up the cycle: A -> B[] where B -> A
            fieldsA.add(AbiType.Struct.Field("name", AbiType.String))
            fieldsA.add(AbiType.Struct.Field("bs", AbiType.Array(typeB)))

            fieldsB.add(AbiType.Struct.Field("name", AbiType.String))
            fieldsB.add(AbiType.Struct.Field("a", typeA))

            val exception = shouldThrow<IllegalStateException> {
                EIP712Codec.encodeType(typeA)
            }
            exception.message shouldContain "TestStruct"
        }
    }
})
