package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.solana.providers.PendingSolanaTransaction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient

/** JVM and Android add blocking and CompletableFuture variants, as the EVM PendingInclusion does. */
class PendingTransactionBlockingApiTest : FunSpec({
    val signature = SolanaSignature(ByteArray(64) { 1 })

    fun provider(): SolanaProvider {
        val ktor = KtorHttpClient(
            MockEngine { request ->
                val id = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("id")
                respond(
                    """{"jsonrpc":"2.0","id":$id,"result":{"context":{"slot":9},"value":[{"slot":9,"confirmations":null,"err":null,"confirmationStatus":"finalized"}]}}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            },
        )
        return SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }

    test("awaitConfirmation blocks the calling thread and returns the status") {
        val provider = provider()
        try {
            val pending = PendingSolanaTransaction(signature, provider)
            pending.awaitConfirmation().unwrap().confirmationStatus shouldBe Commitment.FINALIZED
            pending.awaitConfirmation(Commitment.FINALIZED).unwrap().isSuccess shouldBe true
            pending.awaitConfirmation(Commitment.CONFIRMED, 1.milliseconds, 5.seconds).unwrap().slot.toString() shouldBe "9"
        } finally {
            provider.close()
        }
    }

    test("confirmationAsync completes a CompletableFuture off the calling thread") {
        val provider = provider()
        try {
            val pending = PendingSolanaTransaction(signature, provider)
            pending.confirmationAsync().get().unwrap().confirmationStatus shouldBe Commitment.FINALIZED
            pending.confirmationAsync(Commitment.FINALIZED).get().unwrap().isSuccess shouldBe true
            pending.confirmationAsync(Commitment.CONFIRMED, 1.milliseconds, 5.seconds).get().unwrap().slot.toString() shouldBe "9"
        } finally {
            provider.close()
        }
    }
})
