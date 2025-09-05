package io.ethers.abi.eip712

import io.ethers.abi.Inbox
import io.ethers.abi.Mail
import io.ethers.abi.Person
import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class EIP712TypedDataTest : FunSpec({
    context("EIP712TypedData.from") {
        test("creates complete typed data from simple struct") {
            val person = Person("Alice", Address.ZERO)
            val domain = EIP712Domain(name = "TestDApp", version = "1.0")

            val typedData = EIP712TypedData.from(person, domain)

            typedData.primaryType shouldBe "Person"
            typedData.domain shouldBe domain
            typedData.message shouldBe mapOf(
                "wallet" to "0x0000000000000000000000000000000000000000",
                "name" to "Alice",
            )
            typedData.types shouldBe EIP712Codec.toTypeMap(person) + EIP712Codec.toTypeMap(domain)
        }

        test("creates complete typed data from nested struct") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address("0x1111111111111111111111111111111111111111"))
            val mail = Mail(from, to, "Hello")
            val domain = EIP712Domain(name = "MailDApp")

            val typedData = EIP712TypedData.from(mail, domain)

            typedData.primaryType shouldBe "Mail"
            typedData.domain shouldBe domain
            typedData.types shouldBe EIP712Codec.toTypeMap(mail) + EIP712Codec.toTypeMap(domain)
            typedData.message shouldBe mapOf(
                "from" to mapOf(
                    "wallet" to "0x0000000000000000000000000000000000000000",
                    "name" to "Bob",
                ),
                "to" to mapOf(
                    "wallet" to "0x1111111111111111111111111111111111111111",
                    "name" to "Alice",
                ),
                "contents" to "Hello",
            )
        }

        test("creates complete typed data from struct with arrays") {
            val person = Person("Alice", Address.ZERO)
            val mail = Mail(person, person, "Test")
            val inbox = Inbox("Test Inbox", listOf(mail))
            val domain = EIP712Domain(name = "InboxDApp")

            val typedData = EIP712TypedData.from(inbox, domain)

            typedData.primaryType shouldBe "Inbox"
            typedData.domain shouldBe domain
            typedData.types shouldBe EIP712Codec.toTypeMap(inbox) + EIP712Codec.toTypeMap(domain)
            typedData.message shouldBe mapOf(
                "name" to "Test Inbox",
                "mails" to listOf(
                    mapOf(
                        "from" to mapOf(
                            "wallet" to "0x0000000000000000000000000000000000000000",
                            "name" to "Alice",
                        ),
                        "to" to mapOf(
                            "wallet" to "0x0000000000000000000000000000000000000000",
                            "name" to "Alice",
                        ),
                        "contents" to "Test",
                    ),
                ),
            )
        }

        test("creates typed data from EIP712Domain itself") {
            val domain = EIP712Domain(
                name = "TestDApp",
                version = "1.0",
                chainId = BigInteger.valueOf(1),
            )

            val typedData = EIP712TypedData.from(domain, domain)

            typedData.primaryType shouldBe "EIP712Domain"
            typedData.domain shouldBe domain
            typedData.message shouldBe mapOf(
                "name" to "TestDApp",
                "version" to "1.0",
                "chainId" to "1",
            )
        }
    }

    context("JSON serialization") {
        val mapper = Jackson.MAPPER

        test("roundtrip serialization of simple typed data") {
            val person = Person("Alice", Address.ZERO)
            val domain = EIP712Domain(name = "TestDApp", version = "1.0")
            val typedData = EIP712TypedData.from(person, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized shouldBe typedData
        }

        test("roundtrip serialization of nested typed data") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address("0x1111111111111111111111111111111111111111"))
            val mail = Mail(from, to, "Hello")
            val domain = EIP712Domain(
                name = "MailDApp",
                version = "2.0",
                chainId = BigInteger.valueOf(1),
                verifyingContract = Address("0x2222222222222222222222222222222222222222"),
            )
            val typedData = EIP712TypedData.from(mail, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized shouldBe typedData
        }

        test("roundtrip serialization of typed data with arrays") {
            val person = Person("Alice", Address.ZERO)
            val mail1 = Mail(person, person, "Test 1")
            val mail2 = Mail(person, person, "Test 2")
            val inbox = Inbox("Test Inbox", listOf(mail1, mail2))
            val domain = EIP712Domain(name = "InboxDApp", chainId = BigInteger.TEN)
            val typedData = EIP712TypedData.from(inbox, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized shouldBe typedData
        }

        test("roundtrip serialization of EIP712Field") {
            val field = EIP712Field("testName", "uint256")

            val json = mapper.writeValueAsString(field)
            val deserialized = mapper.readValue(json, EIP712Field::class.java)

            deserialized shouldBe field
        }

        test("JSON structure of serialized EIP712TypedData") {
            val person = Person("Test", Address.ZERO)
            val domain = EIP712Domain(name = "Test", version = "1")
            val typedData = EIP712TypedData.from(person, domain)

            val json = mapper.writeValueAsString(typedData)
            val jsonNode = mapper.readTree(json)

            jsonNode.has("primaryType") shouldBe true
            jsonNode.has("types") shouldBe true
            jsonNode.has("message") shouldBe true
            jsonNode.has("domain") shouldBe true

            jsonNode["primaryType"].asText() shouldBe "Person"
            jsonNode["types"]["Person"].isArray shouldBe true
            jsonNode["types"]["Person"].size() shouldBe 2
            jsonNode["types"]["EIP712Domain"].isArray shouldBe true
        }

        test("handles null values in domain correctly") {
            val domain = EIP712Domain(name = "Test") // Only name is set
            val person = Person("Alice", Address.ZERO)
            val typedData = EIP712TypedData.from(person, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized.domain shouldBe domain
        }

        test("numeric values are formatted as decimal strings") {
            val domain = EIP712Domain(
                name = "NumericTest",
                version = "1.0",
                chainId = BigInteger("12345678901234567890"), // Large number to test decimal formatting
                verifyingContract = Address("0x1234567890abcdef1234567890abcdef12345678"),
            )
            val typedData = EIP712TypedData.from(domain, domain)

            val json = mapper.writeValueAsString(typedData)
            val jsonNode = mapper.readTree(json)

            // Verify chainId is decimal string, not hex
            val chainIdInMessage = jsonNode["message"]["chainId"].asText()
            chainIdInMessage shouldBe "12345678901234567890"

            // Verify chainId in domain is hex string as per EIP712Domain serialization
            val chainIdInDomain = jsonNode["domain"]["chainId"].asText()
            chainIdInDomain shouldBe "0xab54a98ceb1f0ad2"
        }
    }
})
