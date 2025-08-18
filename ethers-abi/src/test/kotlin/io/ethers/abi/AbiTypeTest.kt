package io.ethers.abi

import io.ethers.abi.eip712.EIP712Field
import io.ethers.core.types.Address
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AbiTypeTest : FunSpec({
    test("eip712Type creates correct string for non-nested struct") {
        val person = Person(Address.ZERO, "John")

        person.abiType.eip712Type shouldBe "Person(address wallet,string name)"
    }

    test("eip712Type creates correct string for nested struct") {
        val from = Person(Address.ZERO, "Bob")
        val to = Person(Address.ZERO, "Alice")
        val contents = "Some random string"
        val mail = Mail(from, to, contents, Header("Header"))

        mail.abiType.eip712Type shouldBe "Mail(Person from,Person to,string contents,Header header)"
    }

    test("eip712Type creates correct string for struct with arrays") {
        val inbox = Inbox("Test Inbox", emptyList())

        inbox.abiType.eip712Type shouldBe "Inbox(string name,Mail[] mails)"
    }

    test("eip712Type creates correct string for deeply nested struct") {
        val person1 = Person(Address.ZERO, "Bob")
        val person2 = Person(Address.ZERO, "Alice")
        val mail = Mail(person1, person2, "Hello", Header("Subject"))
        val inbox = Inbox("My Inbox", listOf(mail))

        inbox.abiType.eip712Type shouldBe "Inbox(string name,Mail[] mails)"
    }

    test("eip712TypeHash generates correct hash for simple struct") {
        val person = Person(Address.ZERO, "John")
        val typeHash = person.abiType.eip712TypeHash

        typeHash.size shouldBe 32
        typeHash.toHexString() shouldBe "7da6bbfd4f19da81c7a7a41044aa8a9f78c40e60e88f0abbd96dc4643a2ef30d"
    }

    test("eip712TypeHash generates correct hash for nested struct") {
        val mail = Mail(
            Person(Address.ZERO, "Bob"),
            Person(Address.ZERO, "Alice"),
            "Hello",
            Header("Subject"),
        )
        val typeHash = mail.abiType.eip712TypeHash

        typeHash.size shouldBe 32
        typeHash.toHexString() shouldBe "802b4dd7ae9e58a19cd559b87c14c8c55309209881626fd23ee19c1f6c52c33c"
    }

    test("eip712TypeHash generates correct hash for struct with arrays") {
        val inbox = Inbox("Test", emptyList())
        val typeHash = inbox.abiType.eip712TypeHash

        typeHash.size shouldBe 32
        typeHash.toHexString() shouldBe "9ae37375b4f9441ea00575b596f3903c7706701469fae1e878dbdf3a7308a435"
    }

    test("eip712Components returns correct components for simple struct") {
        val person = Person(Address.ZERO, "John")
        val components = person.abiType.eip712Components

        components shouldBe mapOf(
            "Person" to listOf(
                EIP712Field("wallet", "address"),
                EIP712Field("name", "string"),
            ),
        )
    }

    test("eip712Components returns all nested types for complex struct") {
        val mail = Mail(
            Person(Address.ZERO, "Bob"),
            Person(Address.ZERO, "Alice"),
            "Hello",
            Header("Subject"),
        )

        val components = mail.abiType.eip712Components
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

    test("eip712Components handles structs with arrays correctly") {
        val inbox = Inbox("Test", emptyList())
        val components = inbox.abiType.eip712Components

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

    test("eip712Components deduplicates repeated struct types") {
        val mail = Mail(
            Person(Address.ZERO, "Bob"),
            Person(Address.ZERO, "Alice"),
            "Hello",
            Header("Subject"),
        )
        val inbox = Inbox("Test", listOf(mail, mail))
        val components = inbox.abiType.eip712Components

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
})
