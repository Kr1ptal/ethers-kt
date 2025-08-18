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

    context("encodeType with EIP712TypedData") {
        test("returns correct string for simple struct") {
            val person = Person(Address.ZERO, "John")
            val typedData = EIP712TypedData.from(person, EIP712Domain(name = "Test"))

            val encodedFromTypedData = EIP712Codec.encodeType(typedData)
            val encodedFromStruct = EIP712Codec.encodeType(person)

            encodedFromTypedData shouldBe encodedFromStruct
            encodedFromTypedData shouldBe "Person(address wallet,string name)"
        }

        test("creates correct string for nested structs") {
            val mail = Mail(
                Person(Address.ZERO, "Bob"),
                Person(Address.ZERO, "Alice"),
                "Some random string",
                Header("Header"),
            )
            val typedData = EIP712TypedData.from(mail, EIP712Domain(name = "Test"))

            val encodedFromTypedData = EIP712Codec.encodeType(typedData)
            val encodedFromStruct = EIP712Codec.encodeType(mail)

            encodedFromTypedData shouldBe encodedFromStruct
            encodedFromTypedData shouldBe "Mail(Person from,Person to,string contents,Header header)Header(string header)Person(address wallet,string name)"
        }

        test("creates correct properly sorted string for multiple nested structs") {
            val inbox = Inbox(
                "Test Inbox",
                listOf(
                    Mail(
                        Person(Address.ZERO, "Bob"),
                        Person(Address.ZERO, "Alice"),
                        "Some random string",
                        Header("Header"),
                    ),
                ),
            )
            val typedData = EIP712TypedData.from(inbox, EIP712Domain(name = "Test"))

            val encodedFromTypedData = EIP712Codec.encodeType(typedData)
            val encodedFromStruct = EIP712Codec.encodeType(inbox)

            encodedFromTypedData shouldBe encodedFromStruct
            encodedFromTypedData shouldBe "Inbox(string name,Mail[] mails)Header(string header)Mail(Person from,Person to,string contents,Header header)Person(address wallet,string name)"
        }

        test("encodeType with explicit primaryType and types parameters") {
            val types = mapOf(
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("to", "Person"),
                    EIP712Field("contents", "string"),
                ),
            )

            val encoded = EIP712Codec.encodeType("Mail", types)

            encoded shouldBe "Mail(Person from,Person to,string contents)Person(address wallet,string name)"
        }

        test("handles array types correctly") {
            val types = mapOf(
                "Inbox" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("mails", "Mail[]"),
                ),
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("contents", "string"),
                ),
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
            )

            val encoded = EIP712Codec.encodeType("Inbox", types)

            encoded shouldBe "Inbox(string name,Mail[] mails)Mail(Person from,string contents)Person(address wallet,string name)"
        }

        test("handles fixed array types correctly") {
            val types = mapOf(
                "Collection" to listOf(
                    EIP712Field("items", "Item[5]"),
                ),
                "Item" to listOf(
                    EIP712Field("id", "uint256"),
                    EIP712Field("name", "string"),
                ),
            )

            val encoded = EIP712Codec.encodeType("Collection", types)

            encoded shouldBe "Collection(Item[5] items)Item(uint256 id,string name)"
        }

        test("throws exception for missing primary type") {
            val types = mapOf(
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
            )

            val exception = shouldThrow<IllegalArgumentException> {
                EIP712Codec.encodeType("Missing", types)
            }
            exception.message shouldContain "Type 'Missing' not found in types map"
        }

        test("throws exception for missing dependent type") {
            val types = mapOf(
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("contents", "string"),
                ),
                // Person type is missing
            )

            val exception = shouldThrow<IllegalArgumentException> {
                EIP712Codec.encodeType("Mail", types)
            }
            exception.message shouldContain "Type 'Person' not found"
        }

        test("detects self-referencing cycle") {
            val types = mapOf(
                "Node" to listOf(
                    EIP712Field("value", "string"),
                    EIP712Field("next", "Node"),
                ),
            )

            val exception = shouldThrow<IllegalStateException> {
                EIP712Codec.encodeType("Node", types)
            }
            exception.message shouldContain "Circular references are not allowed"
            exception.message shouldContain "Node"
        }

        test("detects two-level cycle") {
            val types = mapOf(
                "TypeA" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("b", "TypeB"),
                ),
                "TypeB" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("a", "TypeA"),
                ),
            )

            val exception = shouldThrow<IllegalStateException> {
                EIP712Codec.encodeType("TypeA", types)
            }
            exception.message shouldContain "Circular references are not allowed"
        }

        test("detects cycle through array") {
            val types = mapOf(
                "TypeA" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("bs", "TypeB[]"),
                ),
                "TypeB" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("a", "TypeA"),
                ),
            )

            val exception = shouldThrow<IllegalStateException> {
                EIP712Codec.encodeType("TypeA", types)
            }
            exception.message shouldContain "Circular references are not allowed"
        }

        test("handles complex nested structure with correct sorting") {
            val types = mapOf(
                "Root" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("child", "ChildB"),
                    EIP712Field("other", "ChildA"),
                ),
                "ChildA" to listOf(
                    EIP712Field("id", "uint256"),
                ),
                "ChildB" to listOf(
                    EIP712Field("value", "bool"),
                    EIP712Field("nested", "ChildC"),
                ),
                "ChildC" to listOf(
                    EIP712Field("data", "bytes32"),
                ),
            )

            val encoded = EIP712Codec.encodeType("Root", types)

            // Components should be sorted alphabetically: ChildA, ChildB, ChildC
            encoded shouldBe "Root(string name,ChildB child,ChildA other)ChildA(uint256 id)ChildB(bool value,ChildC nested)ChildC(bytes32 data)"
        }
    }
})
