package io.ethers.abi

import io.ethers.core.types.Address
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AbiTypeTest : FunSpec({
    context("eip712RootType") {
        test("creates correct string for non-nested struct") {
            val person = Person(Address.ZERO, "John")

            person.abiType.eip712RootType shouldBe "Person(address wallet,string name)"
        }

        test("creates correct string for nested struct") {
            val from = Person(Address.ZERO, "Bob")
            val to = Person(Address.ZERO, "Alice")
            val contents = "Some random string"
            val mail = Mail(from, to, contents, Header("Header"))

            mail.abiType.eip712RootType shouldBe "Mail(Person from,Person to,string contents,Header header)"
        }

        test("creates correct string for struct with arrays") {
            val inbox = Inbox("Test Inbox", emptyList())

            inbox.abiType.eip712RootType shouldBe "Inbox(string name,Mail[] mails)"
        }

        test("creates correct string for deeply nested struct") {
            val person1 = Person(Address.ZERO, "Bob")
            val person2 = Person(Address.ZERO, "Alice")
            val mail = Mail(person1, person2, "Hello", Header("Subject"))
            val inbox = Inbox("My Inbox", listOf(mail))

            inbox.abiType.eip712RootType shouldBe "Inbox(string name,Mail[] mails)"
        }
    }
})
