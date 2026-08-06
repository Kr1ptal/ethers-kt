package io.ethers.providers

import io.ethers.core.Result
import io.ethers.core.unwrap
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcCall
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ExecutionException
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * The blocking and CompletableFuture batch APIs come from the JVM platform seam, so they cannot be exercised from
 * commonTest. These assertions used to live inline in JsonRpcTestFactory; they moved here when that factory became
 * common so the rest of it could run on native too.
 */
class BatchBlockingApiTest : FunSpec({
    val stringDecoder: (KJsonElement) -> String = { element -> element.jsonPrimitive.content }

    lateinit var server: MockServer
    lateinit var client: HttpClient

    beforeEach {
        server = mockServerHttp()
        client = HttpClient(server.url, KtorHttpClient())
    }

    afterEach {
        server.stop()
    }

    test("toFuture() fails with the batch's IllegalStateException before it is sent") {
        val batch = BatchRpcRequest(1)
        val pending = batch.addRpcCall(RpcCall(client, "eth_blockNumber", emptyArray<Any>(), stringDecoder))

        shouldThrow<ExecutionException> {
            pending.toFuture().get()
        }.cause.shouldBeInstanceOf<IllegalStateException>()
    }

    test("get() and toFuture() return the result once the batch has been sent") {
        server.enqueueJson("""[{"jsonrpc":"2.0","id":1,"result":"0x1234567"}]""")

        val batch = BatchRpcRequest(1)
        val pending = batch.addRpcCall(RpcCall(client, "eth_blockNumber", emptyArray<Any>(), stringDecoder))

        batch.send() shouldBe true

        val expected: Result<String, RpcError> = pending.await()
        expected.unwrap() shouldBe "0x1234567"
        pending.get() shouldBe expected
        pending.toFuture().get() shouldBe expected
    }
})
