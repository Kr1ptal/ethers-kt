package io.ethers.providers

import io.ethers.core.Result
import io.ethers.core.success
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.BatchRpcResponse
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.send
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * Answers calls to `cached` itself and forwards the rest to [parent] in a batch of their own, the way a caching
 * client does, using only the public batch API.
 */
private class ForwardingClient(private val parent: JsonRpcClient) : JsonRpcClient by parent {
    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean {
        val forwarded = BatchRpcRequest()
        val pending = ArrayList<Pair<Int, BatchRpcResponse<out Result<*, RpcError>>>>()
        for ((index, call) in batch.requests.withIndex()) {
            if (call.method == "cached") {
                batch.complete(index, success("from cache"))
            } else {
                pending += index to call.batch(forwarded)
            }
        }

        if (!forwarded.sendWith(parent)) return false
        for ((index, response) in pending) batch.complete(index, response.await())
        return true
    }
}

class BatchForwardingTest : FunSpec({
    lateinit var server: MockServer
    lateinit var parent: HttpClient

    beforeEach {
        server = mockServerHttp()
        parent = HttpClient(server.url, KtorHttpClient())
    }

    afterEach {
        parent.close()
        server.stop()
    }

    test("a wrapping client answers some calls itself and sends the rest through the client it wraps") {
        val client = ForwardingClient(parent)
        server.enqueueJson("""[{"jsonrpc":"2.0","id":2,"result":["x","y"]},{"jsonrpc":"2.0","id":1,"result":"0x10"}]""")

        val results = listOf(
            RpcCall(client, "eth_a", emptyArray<Any>(), String.serializer()),
            RpcCall(client, "cached", emptyArray<Any>(), String.serializer()),
            RpcCall(client, "eth_b", emptyArray<Any>(), ListSerializer(String.serializer())),
        ).send()

        results.map { it.unwrap() } shouldBe listOf("0x10", "from cache", listOf("x", "y"))
        // only the forwarded calls reach the server, in their own batch
        val sent = server.takeRequest().bodyText
        sent.contains("\"eth_a\"") shouldBe true
        sent.contains("\"eth_b\"") shouldBe true
        sent.contains("\"cached\"") shouldBe false
    }

    test("the forwarded batch decodes each result with its call's deserializer") {
        val client = ForwardingClient(parent)
        server.enqueueJson("""[{"jsonrpc":"2.0","id":1,"result":"0x10"}]""")

        val result = listOf(RpcCall(client, "eth_a", emptyArray<Any>(), String.serializer().map { it.length })).send()

        result.single().unwrap() shouldBe 4
    }

    test("a batch is sent once, whichever way it is sent") {
        val batch = BatchRpcRequest()
        RpcCall(parent, "eth_a", emptyArray<Any>(), String.serializer()).batch(batch)
        server.enqueueJson("""[{"jsonrpc":"2.0","id":1,"result":"0x10"}]""")
        batch.sendWith(parent) shouldBe true

        shouldThrow<IllegalStateException> { batch.sendWith(parent) }
        shouldThrow<IllegalStateException> { batch.send() }
    }

    test("a response is completed once") {
        val batch = BatchRpcRequest()
        val response = RpcCall(parent, "eth_a", emptyArray<Any>(), String.serializer()).batch(batch)

        batch.complete(0, success("first")) shouldBe true
        batch.complete(0, success("second")) shouldBe false
        shouldThrow<IndexOutOfBoundsException> { batch.complete(1, success("none")) }

        // a client that leaves the responses as they are
        batch.sendWith(object : JsonRpcClient by parent {
            override suspend fun requestBatch(batch: BatchRpcRequest) = true
        }) shouldBe true
        response.await().unwrap() shouldBe "first"
    }
})
