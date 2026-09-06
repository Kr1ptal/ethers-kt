package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.types.ContextValue
import io.ethers.solana.types.InstructionError
import io.ethers.solana.types.RpcContext
import io.ethers.solana.types.SignatureNotification
import io.ethers.solana.types.TransactionError
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException

class SignatureNotificationTest : FunSpec({
    val serializer = ContextValue.serializer(SignatureNotification.serializer())

    test("received and status payloads roundtrip inside the shared context envelope") {
        val payloads = listOf(
            "\"receivedSignature\"" to SignatureNotification.Received,
            """{"err":null}""" to SignatureNotification.Status(null),
            """{"err":{"InstructionError":[0,"InvalidArgument"]}}""" to SignatureNotification.Status(
                TransactionError.InstructionFailure(0, InstructionError.InvalidArgument),
            ),
        )
        for ((payload, expected) in payloads) {
            for (apiVersion in listOf(null, "3.0.0")) {
                val versionField = apiVersion?.let { ""","apiVersion":"$it"""" } ?: ""
                val wire = """{"context":{"slot":18446744073709551615$versionField},"value":$payload}"""
                val notification = Kotlinx.DEFAULT.decodeFromString(serializer, wire)
                notification.context shouldBe RpcContext(BigInteger("18446744073709551615"), apiVersion)
                notification.value shouldBe expected
                Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(serializer, notification)) shouldBe Kotlinx.DEFAULT.parseToJsonElement(wire)
                Kotlinx.DEFAULT.encodeToString(SignatureNotification.serializer(), notification.value) shouldBe payload
            }
        }
    }

    test("invalid payloads and missing required envelope or status fields are rejected") {
        for (payload in listOf("null", "true", "42", "\"unexpected\"", "[]", "{}")) {
            shouldThrow<SerializationException> {
                Kotlinx.DEFAULT.decodeFromString(serializer, """{"context":{"slot":42},"value":$payload}""")
            }
        }
        for (wire in listOf(
            """{"value":"receivedSignature"}""",
            """{"context":{"slot":42}}""",
            """{"context":{},"value":{"err":null}}""",
        )) {
            shouldThrow<SerializationException> { Kotlinx.DEFAULT.decodeFromString(serializer, wire) }
        }
    }
})
