package io.ethers.abi

import io.ethers.core.types.Address
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AbiTypeTest : FunSpec({
    context("eip712RootType") {
        test("creates correct string for non-nested struct") {
            val person = Person("John", Address.ZERO)

            person.abiType.eip712RootType shouldBe "Person(string name,address wallet)"
        }

        test("creates correct string for nested struct") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address.ZERO)
            val contents = "Some random string"
            val mail = Mail(from, to, contents)

            mail.abiType.eip712RootType shouldBe "Mail(Person from,Person to,string contents)"
        }

        test("creates correct string for struct with arrays") {
            val inbox = Inbox("Test Inbox", emptyList())

            inbox.abiType.eip712RootType shouldBe "Inbox(string name,Mail[] mails)"
        }

        test("creates correct string for deeply nested struct") {
            val person1 = Person("Bob", Address.ZERO)
            val person2 = Person("Alice", Address.ZERO)
            val mail = Mail(person1, person2, "Hello")
            val inbox = Inbox("My Inbox", listOf(mail))

            inbox.abiType.eip712RootType shouldBe "Inbox(string name,Mail[] mails)"
        }
    }
})
