package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.solana.providers.PendingSolanaTransaction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.jsonObject
import io.ktor.client.HttpClient as KtorHttpClient

/** Apple targets spell the default arguments out as overloads and take seconds instead of `Duration`, for Swift. */
class PendingTransactionNativeApiTest : FunSpec({
    val signature = SolanaSignature(ByteArray(64) { 1 })

    fun provider(confirmationStatus: String): SolanaProvider {
        val ktor = KtorHttpClient(
            MockEngine { request ->
                val id = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("id")
                respond(
                    """{"jsonrpc":"2.0","id":$id,"result":{"context":{"slot":9},"value":[{"slot":9,"confirmations":null,"err":null,"confirmationStatus":"$confirmationStatus"}]}}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            },
        )
        return SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }

    test("the overloads wait for the commitment they are given, defaulting to confirmed") {
        val provider = provider("confirmed")
        try {
            val pending = PendingSolanaTransaction(signature, provider)
            pending.confirmation().unwrap().confirmationStatus shouldBe Commitment.CONFIRMED
            pending.confirmation(Commitment.PROCESSED).unwrap().confirmationStatus shouldBe Commitment.CONFIRMED
            pending.confirmation(Commitment.CONFIRMED, 0.001, 5.0).unwrap().slot.toString() shouldBe "9"
        } finally {
            provider.close()
        }
    }

    test("the seconds overload times out after the given seconds") {
        val provider = provider("processed")
        try {
            val error = PendingSolanaTransaction(signature, provider).confirmation(Commitment.FINALIZED, 0.001, 0.05).unwrapError()
            error.shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
        } finally {
            provider.close()
        }
    }

    test("the seconds overload rejects NaN") {
        val provider = provider("confirmed")
        try {
            val pending = PendingSolanaTransaction(signature, provider)
            shouldThrow<IllegalArgumentException> { pending.confirmation(Commitment.CONFIRMED, Double.NaN, 5.0) }
            shouldThrow<IllegalArgumentException> { pending.confirmation(Commitment.CONFIRMED, 0.001, Double.NaN) }
        } finally {
            provider.close()
        }
    }
})
