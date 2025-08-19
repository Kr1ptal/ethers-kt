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
import java.math.BigInteger

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
                { TestStruct() },
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
                { TestStruct() },
                fieldsA,
            )

            val typeB = AbiType.Struct(
                TestStruct::class.java,
                { TestStruct() },
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
                { TestStruct() },
                fieldsA,
            )

            val typeB = AbiType.Struct(
                TestStruct::class.java,
                { TestStruct() },
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

    context("typeHash") {
        test("generates correct hash for simple struct") {
            val person = Person(Address.ZERO, "John")
            val typeHash = EIP712Codec.typeHash(person)

            typeHash.size shouldBe 32
            typeHash.toHexString() shouldBe "7da6bbfd4f19da81c7a7a41044aa8a9f78c40e60e88f0abbd96dc4643a2ef30d"
        }

        test("generates correct hash for nested struct") {
            val mail = Mail(
                Person(Address.ZERO, "Bob"),
                Person(Address.ZERO, "Alice"),
                "Hello",
                Header("Subject"),
            )
            val typeHash = EIP712Codec.typeHash(mail)

            typeHash.size shouldBe 32
            typeHash.toHexString() shouldBe "a0307470303ec6304c7526d820a93e435565e1b976e62b86ceabc82dee28a013"
        }

        test("generates correct hash for struct with arrays") {
            val inbox = Inbox("Test", emptyList())
            val typeHash = EIP712Codec.typeHash(inbox)

            typeHash.size shouldBe 32
            typeHash.toHexString() shouldBe "a883449b0ca720e19817be235ac3283b1a60d8f91fdf01dd26dde9fe84885a84"
        }
    }

    context("toMessage") {
        test("converts simple struct to message map") {
            val person = Person(Address.ZERO, "Alice")

            val message = EIP712Codec.toMessage(person)
            message shouldBe mapOf(
                "wallet" to Address.ZERO,
                "name" to "Alice",
            )
        }

        test("converts nested struct to message map") {
            val from = Person(Address.ZERO, "Bob")
            val to = Person(Address("0x1111111111111111111111111111111111111111"), "Alice")
            val header = Header("Important Message")
            val mail = Mail(from, to, "Hello World", header)

            val message = EIP712Codec.toMessage(mail)
            message shouldBe mapOf(
                "from" to mapOf(
                    "wallet" to Address.ZERO,
                    "name" to "Bob",
                ),
                "to" to mapOf(
                    "wallet" to Address("0x1111111111111111111111111111111111111111"),
                    "name" to "Alice",
                ),
                "contents" to "Hello World",
                "header" to mapOf(
                    "header" to "Important Message",
                ),
            )
        }

        test("converts struct with array to message map") {
            val person1 = Person(Address.ZERO, "Alice")
            val person2 = Person(Address("0x1111111111111111111111111111111111111111"), "Bob")
            val header1 = Header("Message 1")
            val header2 = Header("Message 2")
            val mail1 = Mail(person1, person2, "Hello", header1)
            val mail2 = Mail(person2, person1, "Hi back", header2)
            val inbox = Inbox("My Inbox", listOf(mail1, mail2))

            val message = EIP712Codec.toMessage(inbox)
            message shouldBe mapOf(
                "name" to "My Inbox",
                "mails" to listOf(
                    mapOf(
                        "from" to mapOf(
                            "wallet" to Address.ZERO,
                            "name" to "Alice",
                        ),
                        "to" to mapOf(
                            "wallet" to Address("0x1111111111111111111111111111111111111111"),
                            "name" to "Bob",
                        ),
                        "contents" to "Hello",
                        "header" to mapOf(
                            "header" to "Message 1",
                        ),
                    ),
                    mapOf(
                        "from" to mapOf(
                            "wallet" to Address("0x1111111111111111111111111111111111111111"),
                            "name" to "Bob",
                        ),
                        "to" to mapOf(
                            "wallet" to Address.ZERO,
                            "name" to "Alice",
                        ),
                        "contents" to "Hi back",
                        "header" to mapOf(
                            "header" to "Message 2",
                        ),
                    ),
                ),
            )
        }

        test("converts empty array to empty list") {
            val inbox = Inbox("Empty Inbox", emptyList())

            val message = EIP712Codec.toMessage(inbox)
            message shouldBe mapOf(
                "name" to "Empty Inbox",
                "mails" to emptyList<Map<String, Any>>(),
            )
        }

        test("converts EIP712Domain to message map") {
            val domain = EIP712Domain(
                name = "MyDApp",
                version = "1.0",
                chainId = BigInteger.valueOf(1),
            )

            val message = domain.toEIP712Message()

            message shouldBe mapOf(
                "name" to "MyDApp",
                "version" to "1.0",
                "chainId" to BigInteger.valueOf(1),
            )
        }
    }

    context("toTypeMap") {
        test("returns correct components for simple struct") {
            val person = Person(Address.ZERO, "John")
            val components = EIP712Codec.toTypeMap(person)

            components shouldBe mapOf(
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
            )
        }

        test("returns all nested types for complex struct") {
            val mail = Mail(
                Person(Address.ZERO, "Bob"),
                Person(Address.ZERO, "Alice"),
                "Hello",
                Header("Subject"),
            )

            val components = EIP712Codec.toTypeMap(mail)
            components shouldBe mapOf(
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("to", "Person"),
                    EIP712Field("contents", "string"),
                    EIP712Field("header", "Header"),
                ),
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
                "Header" to listOf(
                    EIP712Field("header", "string"),
                ),
            )
        }

        test("handles structs with arrays correctly") {
            val inbox = Inbox("Test", emptyList())
            val components = EIP712Codec.toTypeMap(inbox)

            components shouldBe mapOf(
                "Inbox" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("mails", "Mail[]"),
                ),
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("to", "Person"),
                    EIP712Field("contents", "string"),
                    EIP712Field("header", "Header"),
                ),
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
                "Header" to listOf(
                    EIP712Field("header", "string"),
                ),
            )
        }

        test("deduplicates repeated struct types") {
            val mail = Mail(
                Person(Address.ZERO, "Bob"),
                Person(Address.ZERO, "Alice"),
                "Hello",
                Header("Subject"),
            )
            val inbox = Inbox("Test", listOf(mail, mail))
            val components = EIP712Codec.toTypeMap(inbox)

            components shouldBe mapOf(
                "Inbox" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("mails", "Mail[]"),
                ),
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("to", "Person"),
                    EIP712Field("contents", "string"),
                    EIP712Field("header", "Header"),
                ),
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
                "Header" to listOf(
                    EIP712Field("header", "string"),
                ),
            )
        }
    }

    context("hashStruct") {
        test("hashes simple struct correctly") {
            val person = Person(Address.ZERO, "Alice")
            val typedData = EIP712TypedData.from(person, EIP712Domain(name = "Test"))

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(person)

            hashFromTypedData shouldBe hashFromStruct
        }

        test("hashes nested struct correctly") {
            val mail = Mail(
                Person(Address.ZERO, "Bob"),
                Person(Address("0x1111111111111111111111111111111111111111"), "Alice"),
                "Hello World",
                Header("Important Message"),
            )
            val typedData = EIP712TypedData.from(mail, EIP712Domain(name = "Test"))

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(mail)

            hashFromTypedData shouldBe hashFromStruct
        }

        test("hashes struct with arrays correctly") {
            val inbox = Inbox(
                "My Inbox",
                listOf(
                    Mail(
                        Person(Address.ZERO, "Bob"),
                        Person(Address("0x1111111111111111111111111111111111111111"), "Alice"),
                        "Hello",
                        Header("Subject 1"),
                    ),
                    Mail(
                        Person(Address("0x2222222222222222222222222222222222222222"), "Charlie"),
                        Person(Address.ZERO, "Bob"),
                        "Hi back",
                        Header("Subject 2"),
                    ),
                ),
            )
            val typedData = EIP712TypedData.from(inbox, EIP712Domain(name = "Test"))

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(inbox)

            hashFromTypedData shouldBe hashFromStruct
        }

        test("hashes EIP712Domain correctly") {
            val domain = EIP712Domain(
                name = "TestDApp",
                version = "1.0",
                chainId = BigInteger.valueOf(1),
            )
            val typedData = EIP712TypedData.from(domain, domain)

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(domain)

            hashFromTypedData shouldBe hashFromStruct
        }

        test("hashes struct with explicit types and message map") {
            val types = mapOf(
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
            )
            val message = mapOf(
                "wallet" to Address.ZERO,
                "name" to "Alice",
            )

            val hash = EIP712Codec.hashStruct("Person", types, message)

            // Compare with hash from struct
            val person = Person(Address.ZERO, "Alice")
            val expectedHash = EIP712Codec.hashStruct(person)

            hash shouldBe expectedHash
        }

        test("hashes nested struct with explicit types and message map") {
            val types = mapOf(
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("to", "Person"),
                    EIP712Field("contents", "string"),
                    EIP712Field("header", "Header"),
                ),
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
                "Header" to listOf(
                    EIP712Field("header", "string"),
                ),
            )
            val message = mapOf(
                "from" to mapOf(
                    "wallet" to Address.ZERO,
                    "name" to "Bob",
                ),
                "to" to mapOf(
                    "wallet" to Address("0x1111111111111111111111111111111111111111"),
                    "name" to "Alice",
                ),
                "contents" to "Hello World",
                "header" to mapOf(
                    "header" to "Important Message",
                ),
            )

            val hash = EIP712Codec.hashStruct("Mail", types, message)

            // Compare with hash from struct
            val mail = Mail(
                Person(Address.ZERO, "Bob"),
                Person(Address("0x1111111111111111111111111111111111111111"), "Alice"),
                "Hello World",
                Header("Important Message"),
            )
            val expectedHash = EIP712Codec.hashStruct(mail)

            hash shouldBe expectedHash
        }

        test("handles different value types for addresses") {
            val types = mapOf(
                "Test" to listOf(
                    EIP712Field("addr", "address"),
                ),
            )

            // Test with Address object
            val hash1 = EIP712Codec.hashStruct(
                "Test",
                types,
                mapOf("addr" to Address.ZERO),
            )

            // Test with string
            val hash2 = EIP712Codec.hashStruct(
                "Test",
                types,
                mapOf("addr" to "0x0000000000000000000000000000000000000000"),
            )

            hash1 shouldBe hash2
        }

        test("handles different value types for numbers") {
            val types = mapOf(
                "Test" to listOf(
                    EIP712Field("amount", "uint256"),
                ),
            )

            // Test with BigInteger
            val hash1 = EIP712Codec.hashStruct(
                "Test",
                types,
                mapOf("amount" to BigInteger.valueOf(12345)),
            )

            // Test with Long
            val hash2 = EIP712Codec.hashStruct(
                "Test",
                types,
                mapOf("amount" to 12345L),
            )

            // Test with String
            val hash3 = EIP712Codec.hashStruct(
                "Test",
                types,
                mapOf("amount" to "12345"),
            )

            hash1 shouldBe hash2
            hash1 shouldBe hash3
        }

        test("throws exception for missing field in message") {
            val types = mapOf(
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
            )
            val message = mapOf(
                "wallet" to Address.ZERO,
                // missing "name" field
            )

            val exception = shouldThrow<IllegalArgumentException> {
                EIP712Codec.hashStruct("Person", types, message)
            }
            exception.message shouldContain "Field 'name' not found in message"
        }

        test("throws exception for invalid value type") {
            val types = mapOf(
                "Test" to listOf(
                    EIP712Field("flag", "bool"),
                ),
            )
            val message = mapOf(
                "flag" to "not a boolean",
            )

            shouldThrow<IllegalArgumentException> {
                EIP712Codec.hashStruct("Test", types, message)
            }
        }

        test("throws exception for unknown type in message") {
            val types = mapOf(
                "Test" to listOf(
                    EIP712Field("data", "CustomType"),
                ),
                // CustomType is not defined
            )
            val message = mapOf(
                "data" to mapOf("value" to 123),
            )

            val exception = shouldThrow<IllegalArgumentException> {
                EIP712Codec.hashStruct("Test", types, message)
            }
            exception.message shouldContain "CustomType"
        }

        test("simple struct hash comparison") {
            val person = Person(Address.ZERO, "Alice")
            val typedData = EIP712TypedData.from(person, EIP712Domain(name = "Test"))

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(person)

            // The hashes should be identical
            hashFromTypedData shouldBe hashFromStruct
        }

        test("complete EIP712TypedData signatureHash") {
            val mail = Mail(
                Person(Address.ZERO, "Bob"),
                Person(Address("0x1111111111111111111111111111111111111111"), "Alice"),
                "Hello World",
                Header("Important"),
            )
            val domain = EIP712Domain(
                name = "MailDApp",
                version = "1.0",
                chainId = BigInteger.valueOf(1),
            )
            val typedData = EIP712TypedData.from(mail, domain)

            // Should not throw and should return 32-byte hash
            val signatureHash = typedData.signatureHash()
            signatureHash.size shouldBe 32
        }
    }
})
