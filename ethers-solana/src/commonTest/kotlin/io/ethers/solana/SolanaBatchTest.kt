package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.providers.types.awaitSuspend
import io.ethers.providers.types.batchRequest
import io.ethers.providers.types.unwrap
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.Programs
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SolanaNodeHealth
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * Batching lives in ethers-rpc and is generic over [io.ethers.providers.types.RpcRequest], so Solana
 * requests should join a batch with no Solana-specific support. These tests hold that to account: the
 * point of each is that exactly one HTTP request leaves the client.
 */
class SolanaBatchTest : FunSpec({
    // one entry per id seen in the request, so a batch answers every call it carries
    fun results(vararg byMethod: Pair<String, String>) = byMethod.toMap()

    fun providerAnswering(byMethod: Map<String, String>, bodies: MutableList<JsonElement>): SolanaProvider {
        val ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text)
                bodies.add(body)
                val calls = if (body is JsonArray) body.jsonArray.map { it.jsonObject } else listOf(body.jsonObject)
                val answers = calls.joinToString(",") {
                    val method = it.getValue("method").jsonPrimitive.content
                    """{"jsonrpc":"2.0","id":${it.getValue("id")},"result":${byMethod.getValue(method)}}"""
                }
                val payload = if (body is JsonArray) "[$answers]" else answers
                respond(payload, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            },
        )
        return SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }

    val contextual = { value: String -> """{"context":{"slot":7,"apiVersion":"3.0.0"},"value":$value}""" }

    test("unrelated Solana calls travel as a single batched request") {
        val bodies = mutableListOf<JsonElement>()
        val provider = providerAnswering(
            results(
                "getBalance" to contextual("42"),
                "getHealth" to "\"ok\"",
                "getVersion" to """{"solana-core":"3.0.0","feature-set":123}""",
            ),
            bodies,
        )
        try {
            val batch = batchRequest(
                provider.getBalance(Programs.SYSTEM),
                provider.getHealth(),
                provider.getVersion(),
            ).awaitSuspend().unwrap()

            batch.response1.value shouldBe bigIntegerOf(42)
            batch.response2 shouldBe SolanaNodeHealth.OK
            batch.response3.solanaCore shouldBe "3.0.0"

            // the whole point: three calls, one round trip, sent as a JSON-RPC array
            bodies.size shouldBe 1
            (bodies.single() is JsonArray) shouldBe true
            bodies.single().jsonArray.size shouldBe 3
        } finally {
            provider.close()
        }
    }

    test("a batched failure is reported per call, leaving the others intact") {
        val bodies = mutableListOf<JsonElement>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text)
                bodies.add(body)
                val calls = body.jsonArray.map { it.jsonObject }
                val answers = calls.joinToString(",") {
                    val id = it.getValue("id")
                    if (it.getValue("method").jsonPrimitive.content == "getHealth") {
                        """{"jsonrpc":"2.0","id":$id,"error":{"code":-32005,"message":"Node is unhealthy"}}"""
                    } else {
                        """{"jsonrpc":"2.0","id":$id,"result":${contextual("42")}}"""
                    }
                }
                respond("[$answers]", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            },
        )
        val provider = SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
        try {
            val batch = batchRequest(provider.getBalance(Programs.SYSTEM), provider.getHealth()).awaitSuspend()

            batch.response1.unwrap().value shouldBe bigIntegerOf(42)
            batch.response2.unwrapError().code shouldBe -32005
            bodies.size shouldBe 1
        } finally {
            provider.close()
        }
    }
})
