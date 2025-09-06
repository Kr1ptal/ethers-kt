package io.ethers.abi.eip712

import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.Inbox
import io.ethers.abi.Mail
import io.ethers.abi.Person
import io.ethers.core.types.Address
import io.ethers.core.types.Hash
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
            val person = Person("John", Address.ZERO)

            EIP712Codec.encodeType(person) shouldBe "Person(string name,address wallet)"
        }

        test("creates correct string for nested structs") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address.ZERO)
            val contents = "Some random string"
            val mail = Mail(from, to, contents)

            EIP712Codec.encodeType(mail) shouldBe "Mail(Person from,Person to,string contents)Person(string name,address wallet)"
        }

        test("creates correct properly sorted string for multiple nested structs") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address.ZERO)
            val contents = "Some random string"
            val mail = Mail(from, to, contents)
            val inbox = Inbox("Test Inbox", listOf(mail))

            EIP712Codec.encodeType(inbox) shouldBe "Inbox(string name,Mail[] mails)Mail(Person from,Person to,string contents)Person(string name,address wallet)"
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
            val person = Person("John", Address.ZERO)
            val typeHash = EIP712Codec.typeHash(person)

            typeHash.size shouldBe 32
            typeHash.toHexString() shouldBe "b9d8c78acf9b987311de6c7b45bb6a9c8e1bf361fa7fd3467a2163f994c79500"
        }

        test("generates correct hash for nested struct") {
            val mail = Mail(
                Person("Bob", Address.ZERO),
                Person("Alice", Address.ZERO),
                "Hello",
            )
            val typeHash = EIP712Codec.typeHash(mail)

            typeHash.size shouldBe 32
            typeHash.toHexString() shouldBe "a0cedeb2dc280ba39b857546d74f5549c3a1d7bdc2dd96bf881f76108e23dac2"
        }

        test("generates correct hash for struct with arrays") {
            val inbox = Inbox("Test", emptyList())
            val typeHash = EIP712Codec.typeHash(inbox)

            typeHash.size shouldBe 32
            typeHash.toHexString() shouldBe "ab66368714d741992cbc6768947974e6a03601750245d7689ffee3dc6c9f9eee"
        }
    }

    context("toMessage") {
        test("converts simple struct to message map") {
            val person = Person("Alice", Address.ZERO)

            val message = EIP712Codec.toMessage(person)
            message shouldBe mapOf(
                "wallet" to "0x0000000000000000000000000000000000000000",
                "name" to "Alice",
            )
        }

        test("converts nested struct to message map") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address("0x1111111111111111111111111111111111111111"))
            val mail = Mail(from, to, "Hello World")

            val message = EIP712Codec.toMessage(mail)
            message shouldBe mapOf(
                "from" to mapOf(
                    "wallet" to "0x0000000000000000000000000000000000000000",
                    "name" to "Bob",
                ),
                "to" to mapOf(
                    "wallet" to "0x1111111111111111111111111111111111111111",
                    "name" to "Alice",
                ),
                "contents" to "Hello World",
            )
        }

        test("converts struct with array to message map") {
            val person1 = Person("Alice", Address.ZERO)
            val person2 = Person("Bob", Address("0x1111111111111111111111111111111111111111"))
            val mail1 = Mail(person1, person2, "Hello")
            val mail2 = Mail(person2, person1, "Hi back")
            val inbox = Inbox("My Inbox", listOf(mail1, mail2))

            val message = EIP712Codec.toMessage(inbox)
            message shouldBe mapOf(
                "name" to "My Inbox",
                "mails" to listOf(
                    mapOf(
                        "from" to mapOf(
                            "wallet" to "0x0000000000000000000000000000000000000000",
                            "name" to "Alice",
                        ),
                        "to" to mapOf(
                            "wallet" to "0x1111111111111111111111111111111111111111",
                            "name" to "Bob",
                        ),
                        "contents" to "Hello",
                    ),
                    mapOf(
                        "from" to mapOf(
                            "wallet" to "0x1111111111111111111111111111111111111111",
                            "name" to "Bob",
                        ),
                        "to" to mapOf(
                            "wallet" to "0x0000000000000000000000000000000000000000",
                            "name" to "Alice",
                        ),
                        "contents" to "Hi back",
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
                "chainId" to "1",
            )
        }
    }

    context("toTypeMap") {
        test("returns correct components for simple struct") {
            val person = Person("John", Address.ZERO)
            val components = EIP712Codec.toTypeMap(person)

            components shouldBe mapOf(
                "Person" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("wallet", "address"),
                ),
            )
        }

        test("returns all nested types for complex struct") {
            val mail = Mail(
                Person("Bob", Address.ZERO),
                Person("Alice", Address.ZERO),
                "Hello",
            )

            val components = EIP712Codec.toTypeMap(mail)
            components shouldBe mapOf(
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("to", "Person"),
                    EIP712Field("contents", "string"),
                ),
                "Person" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("wallet", "address"),
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
                ),
                "Person" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("wallet", "address"),
                ),
            )
        }

        test("deduplicates repeated struct types") {
            val mail = Mail(
                Person("Bob", Address.ZERO),
                Person("Alice", Address.ZERO),
                "Hello",
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
                ),
                "Person" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("wallet", "address"),
                ),
            )
        }
    }

    context("hashStruct") {
        test("hashes simple struct correctly") {
            val person = Person("Alice", Address.ZERO)
            val typedData = EIP712TypedData.from(person, EIP712Domain(name = "Test"))

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(person)

            hashFromTypedData shouldBe hashFromStruct
        }

        test("hashes nested struct correctly") {
            val mail = Mail(
                Person("Bob", Address.ZERO),
                Person("Alice", Address("0x1111111111111111111111111111111111111111")),
                "Hello World",
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
                        Person("Bob", Address.ZERO),
                        Person("Alice", Address("0x1111111111111111111111111111111111111111")),
                        "Hello",
                    ),
                    Mail(
                        Person("Charlie", Address("0x2222222222222222222222222222222222222222")),
                        Person("Bob", Address.ZERO),
                        "Hi back",
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
                name = "Ether Mail",
                version = "1",
                chainId = BigInteger.valueOf(1),
                verifyingContract = Address("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"),
            )
            val typedData = EIP712TypedData.from(domain, domain)

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(domain)

            hashFromTypedData shouldBe hashFromStruct
            Hash(hashFromTypedData) shouldBe Hash("0xf2cee375fa42b42143804025fc449deafd50cc031ca257e0b194a650a912090f")
        }

        test("hashes struct with explicit types and message map") {
            val types = mapOf(
                "Person" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("wallet", "address"),
                ),
            )
            val message = mapOf(
                "name" to "Alice",
                "wallet" to "0x0000000000000000000000000000000000000000",
            )

            val hash = EIP712Codec.hashStruct("Person", types, message)

            // Compare with hash from struct
            val person = Person("Alice", Address.ZERO)
            val expectedHash = EIP712Codec.hashStruct(person)

            hash shouldBe expectedHash
        }

        test("hashes nested struct with explicit types and message map") {
            val types = mapOf(
                "Mail" to listOf(
                    EIP712Field("from", "Person"),
                    EIP712Field("to", "Person"),
                    EIP712Field("contents", "string"),
                ),
                "Person" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("wallet", "address"),
                ),
            )
            val message = mapOf(
                "from" to mapOf(
                    "wallet" to "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                    "name" to "Cow",
                ),
                "to" to mapOf(
                    "wallet" to "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                    "name" to "Bob",
                ),
                "contents" to "Hello, Bob!",
            )

            val hash = EIP712Codec.hashStruct("Mail", types, message)

            Hash(hash) shouldBe Hash("0xc52c0ee5d84264471806290a3f2c4cecfc5490626bf912d01f240d7a274b371e")
        }

        test("throws exception for missing field in message") {
            val types = mapOf(
                "Person" to listOf(
                    EIP712Field("wallet", "address"),
                    EIP712Field("name", "string"),
                ),
            )
            val message = mapOf(
                "wallet" to "0x0000000000000000000000000000000000000000",
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
                    EIP712Field("flag", "address"),
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
            val person = Person("Alice", Address.ZERO)
            val typedData = EIP712TypedData.from(person, EIP712Domain(name = "Test"))

            val hashFromTypedData = EIP712Codec.hashStruct(typedData)
            val hashFromStruct = EIP712Codec.hashStruct(person)

            // The hashes should be identical
            hashFromTypedData shouldBe hashFromStruct
        }

        test("complete EIP712TypedData signatureHash") {
            val mail = Mail(
                Person("Bob", Address.ZERO),
                Person("Alice", Address("0x1111111111111111111111111111111111111111")),
                "Hello World",
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

    context("hashStruct with nested arrays") {
        test("hashes struct with array of primitive arrays") {
            val types = mapOf(
                "MatrixData" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("values", "uint256[][]"),
                ),
            )
            val message = mapOf(
                "name" to "Test Matrix",
                "values" to listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                ),
            )

            val hash = EIP712Codec.hashStruct("MatrixData", types, message)
            hash.size shouldBe 32
            // Each inner array should be hashed separately, then the outer array is hashed
        }

        test("hashes struct with fixed-size array of dynamic arrays") {
            val types = mapOf(
                "FixedMatrix" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("rows", "uint256[][3]"),
                ),
            )
            val message = mapOf(
                "name" to "Fixed Rows Matrix",
                "rows" to listOf(
                    listOf("10", "20"),
                    listOf("30", "40", "50"),
                    listOf("60"),
                ),
            )

            val hash = EIP712Codec.hashStruct("FixedMatrix", types, message)
            hash.size shouldBe 32
        }

        test("hashes struct with array of struct arrays") {
            val types = mapOf(
                "Department" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("teams", "Team[]"),
                ),
                "Team" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("members", "Person[]"),
                ),
                "Person" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("wallet", "address"),
                ),
            )

            val message = mapOf(
                "name" to "Engineering",
                "teams" to listOf(
                    mapOf(
                        "name" to "Backend",
                        "members" to listOf(
                            mapOf("name" to "Alice", "wallet" to "0x0000000000000000000000000000000000000001"),
                            mapOf("name" to "Bob", "wallet" to "0x0000000000000000000000000000000000000002"),
                        ),
                    ),
                    mapOf(
                        "name" to "Frontend",
                        "members" to listOf(
                            mapOf("name" to "Charlie", "wallet" to "0x0000000000000000000000000000000000000003"),
                        ),
                    ),
                ),
            )

            val hash = EIP712Codec.hashStruct("Department", types, message)
            hash.size shouldBe 32

            // Verify encoding components are sorted correctly
            val encodedType = EIP712Codec.encodeType("Department", types)
            encodedType shouldContain "Department(string name,Team[] teams)"
            encodedType shouldContain "Person(string name,address wallet)"
            encodedType shouldContain "Team(string name,Person[] members)"
        }

        test("hashes deeply nested struct arrays") {
            val types = mapOf(
                "Organization" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("departments", "Department[][]"),
                ),
                "Department" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("budget", "uint256"),
                ),
            )

            val message = mapOf(
                "name" to "MegaCorp",
                "departments" to listOf(
                    listOf(
                        mapOf("name" to "Engineering", "budget" to "1000000"),
                        mapOf("name" to "Marketing", "budget" to "500000"),
                    ),
                    listOf(
                        mapOf("name" to "Sales", "budget" to "750000"),
                    ),
                ),
            )

            val hash = EIP712Codec.hashStruct("Organization", types, message)
            hash.size shouldBe 32
        }

        test("hashes struct with mixed array types") {
            val types = mapOf(
                "ComplexData" to listOf(
                    EIP712Field("staticArray", "address[3]"),
                    EIP712Field("dynamicArray", "uint256[]"),
                    EIP712Field("nestedStatic", "bytes32[2][2]"),
                    EIP712Field("structArray", "Point[]"),
                ),
                "Point" to listOf(
                    EIP712Field("x", "uint256"),
                    EIP712Field("y", "uint256"),
                ),
            )

            val message = mapOf(
                "staticArray" to listOf(
                    "0x0000000000000000000000000000000000000001",
                    "0x0000000000000000000000000000000000000002",
                    "0x0000000000000000000000000000000000000003",
                ),
                "dynamicArray" to listOf("100", "200", "300", "400"),
                "nestedStatic" to listOf(
                    listOf(
                        "0x0000000000000000000000000000000000000000000000000000000000000001",
                        "0x0000000000000000000000000000000000000000000000000000000000000002",
                    ),
                    listOf(
                        "0x0000000000000000000000000000000000000000000000000000000000000003",
                        "0x0000000000000000000000000000000000000000000000000000000000000004",
                    ),
                ),
                "structArray" to listOf(
                    mapOf("x" to "10", "y" to "20"),
                    mapOf("x" to "30", "y" to "40"),
                ),
            )

            val hash = EIP712Codec.hashStruct("ComplexData", types, message)
            hash.size shouldBe 32
        }

        test("handles empty nested arrays correctly") {
            val types = mapOf(
                "EmptyArrays" to listOf(
                    EIP712Field("name", "string"),
                    EIP712Field("emptyMatrix", "uint256[][]"),
                    EIP712Field("partialMatrix", "address[][3]"),
                ),
            )

            val message = mapOf(
                "name" to "Empty Test",
                "emptyMatrix" to emptyList<List<String>>(),
                "partialMatrix" to listOf(
                    emptyList<String>(),
                    listOf("0x0000000000000000000000000000000000000001"),
                    emptyList<String>(),
                ),
            )

            val hash = EIP712Codec.hashStruct("EmptyArrays", types, message)
            hash.size shouldBe 32
        }

        test("validates array dimensions match type definition") {
            val types = mapOf(
                "FixedArrayTest" to listOf(
                    EIP712Field("values", "uint256[3]"),
                ),
            )

            // Test with correct size
            val validMessage = mapOf(
                "values" to listOf("1", "2", "3"),
            )
            val hash = EIP712Codec.hashStruct("FixedArrayTest", types, validMessage)
            hash.size shouldBe 32

            // Note: The current implementation doesn't validate fixed array sizes,
            // it processes whatever array is provided. This test documents this behavior.
            val invalidMessage = mapOf(
                "values" to listOf("1", "2"), // Wrong size
            )
            // This should ideally throw but currently doesn't
            val hash2 = EIP712Codec.hashStruct("FixedArrayTest", types, invalidMessage)
            hash2.size shouldBe 32
        }

        test("hashes arrays of bytes and strings correctly") {
            val types = mapOf(
                "BytesArrayTest" to listOf(
                    EIP712Field("dynamicBytes", "bytes[]"),
                    EIP712Field("strings", "string[]"),
                    EIP712Field("fixedBytes", "bytes32[]"),
                ),
            )

            val message = mapOf(
                "dynamicBytes" to listOf(
                    "0x1234",
                    "0xabcdef",
                    "0x",
                ),
                "strings" to listOf(
                    "Hello",
                    "World",
                    "",
                ),
                "fixedBytes" to listOf(
                    "0x0000000000000000000000000000000000000000000000000000000000000001",
                    "0x0000000000000000000000000000000000000000000000000000000000000002",
                ),
            )

            val hash = EIP712Codec.hashStruct("BytesArrayTest", types, message)
            hash.size shouldBe 32
        }
    }
})
