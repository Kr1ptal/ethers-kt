package io.ethers.abi.eip712

import io.ethers.abi.Inbox
import io.ethers.abi.Mail
import io.ethers.abi.Person
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
                "wallet" to Address.ZERO,
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
                    "wallet" to Address.ZERO,
                    "name" to "Bob",
                ),
                "to" to mapOf(
                    "wallet" to Address("0x1111111111111111111111111111111111111111"),
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
                            "wallet" to Address.ZERO,
                            "name" to "Alice",
                        ),
                        "to" to mapOf(
                            "wallet" to Address.ZERO,
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
                "chainId" to BigInteger.valueOf(1),
            )
        }
    }
})
