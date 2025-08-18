package io.ethers.abi.eip712

import io.ethers.abi.Header
import io.ethers.abi.Inbox
import io.ethers.abi.Mail
import io.ethers.abi.Person
import io.ethers.core.types.Address
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigInteger

class EIP712TypedDataTest : FunSpec({
    context("toEIP712Message") {
        test("converts simple struct to message map") {
            val person = Person(Address.ZERO, "Alice")

            val message = person.toEIP712Message()

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

            val message = mail.toEIP712Message()
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

            val message = inbox.toEIP712Message()

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

            val message = inbox.toEIP712Message()

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

    context("EIP712TypedData.from") {
        test("creates complete typed data from simple struct") {
            val person = Person(Address.ZERO, "Alice")
            val domain = EIP712Domain(name = "TestDApp", version = "1.0")

            val typedData = EIP712TypedData.from(person, domain)

            typedData.primaryType shouldBe "Person"
            typedData.domain shouldBe domain
            typedData.message shouldBe mapOf(
                "wallet" to Address.ZERO,
                "name" to "Alice",
            )
            typedData.types shouldBe person.abiType.eip712Components
        }

        test("creates complete typed data from nested struct") {
            val from = Person(Address.ZERO, "Bob")
            val to = Person(Address("0x1111111111111111111111111111111111111111"), "Alice")
            val header = Header("Test")
            val mail = Mail(from, to, "Hello", header)
            val domain = EIP712Domain(name = "MailDApp")

            val typedData = EIP712TypedData.from(mail, domain)

            typedData.primaryType shouldBe "Mail"
            typedData.domain shouldBe domain
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
                "header" to mapOf(
                    "header" to "Test",
                ),
            )
            typedData.types shouldBe mail.abiType.eip712Components
        }

        test("creates complete typed data from struct with arrays") {
            val person = Person(Address.ZERO, "Alice")
            val mail = Mail(person, person, "Test", Header("Test"))
            val inbox = Inbox("Test Inbox", listOf(mail))
            val domain = EIP712Domain(name = "InboxDApp")

            val typedData = EIP712TypedData.from(inbox, domain)

            typedData.primaryType shouldBe "Inbox"
            typedData.domain shouldBe domain
            typedData.message.shouldBeInstanceOf<Map<String, Any>>()

            val message = typedData.message
            message["name"] shouldBe "Test Inbox"
            message["mails"].shouldBeInstanceOf<List<*>>()

            val mails = message["mails"] as List<*>
            mails.size shouldBe 1
            mails[0].shouldBeInstanceOf<Map<*, *>>()

            typedData.types shouldBe inbox.abiType.eip712Components
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
