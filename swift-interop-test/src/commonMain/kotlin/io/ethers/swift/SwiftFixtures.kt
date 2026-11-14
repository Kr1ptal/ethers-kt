package io.ethers.swift

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.solana.providers.PendingSolanaTransaction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.jsonObject
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * A pending transaction whose RPC node always reports [confirmationStatus], since Swift cannot build a mock engine.
 * The provider is never closed; the Swift test process exits right after.
 */
fun pendingSolanaTransaction(confirmationStatus: String): PendingSolanaTransaction {
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
    val provider = SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    return PendingSolanaTransaction(SolanaSignature(ByteArray(64) { 1 }), provider)
}
