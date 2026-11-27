package io.ethers.providers

import io.ethers.core.types.BlockId
import io.ethers.core.types.CallRequest
import io.ethers.core.types.IntoCallRequest
import io.ethers.core.types.tracers.StructTracer
import io.ethers.core.types.tracers.TracerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * `eth_callMany`/`debug_traceCallMany` take [IntoCallRequest]s, which can change after the request is created, as a
 * contract call that is still being configured does. The calls must be converted when the request is created.
 * */
class ProviderCallManyTest : FunSpec({
    lateinit var server: MockServer
    lateinit var client: HttpClient
    lateinit var provider: Provider

    beforeEach {
        server = mockServerHttp()
        client = HttpClient(server.url, KtorHttpClient())
        provider = Provider(client, 1L)
    }

    afterEach {
        client.close()
        server.stop()
    }

    suspend fun takeParams(): JsonArray {
        return Json.parseToJsonElement(server.takeRequest().bodyText).jsonObject["params"]!!.jsonArray
    }

    fun JsonObject.singleNonce(): String {
        return this["transactions"]!!.jsonArray.single().jsonObject["nonce"]!!.jsonPrimitive.content
    }

    test("callMany converts the calls when the request is created, not when it is sent") {
        server.enqueueJson("""{"jsonrpc":"2.0","id":0,"result":[[]]}""")
        val call = ChangingCall(CallRequest { nonce = 1 })

        val request = provider.callMany(BlockId.LATEST, listOf(call), -1, null, null)
        call.current = CallRequest { nonce = 2 }
        request.send().unwrap()

        // eth_callMany takes a list of bundles
        takeParams()[0].jsonArray.single().jsonObject.singleNonce() shouldBe "0x1"
    }

    test("traceCallMany converts the calls when the request is created, not when it is sent") {
        server.enqueueJson("""{"jsonrpc":"2.0","id":0,"result":[[]]}""")
        val call = ChangingCall(CallRequest { nonce = 1 })

        val request = provider.traceCallMany(BlockId.LATEST, listOf(call), TracerConfig(StructTracer()), -1)
        call.current = CallRequest { nonce = 2 }
        request.send().unwrap()

        // debug_traceCallMany takes a list of bundles
        takeParams()[0].jsonArray.single().jsonObject.singleNonce() shouldBe "0x1"
    }
})

/**
 * An [IntoCallRequest] whose request can change, like a contract call that is still being configured.
 * */
private class ChangingCall(var current: CallRequest) : IntoCallRequest {
    override fun toCallRequest(): CallRequest = current
}
