package io.ethers.providers

import io.ethers.core.isSuccess
import io.ethers.core.unwrap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * Each provider takes a child of whichever ktor client it was given, so closing one provider must never tear down
 * a transport it does not own. Before this, `close()` disposed of [RpcClientConfig]'s shared default and every
 * other provider in the process started failing with "Parent job is Completed".
 */
class ClientOwnershipTest : FunSpec({
    test("closing a provider does not break an unrelated one sharing the default client") {
        val serverA = mockServerHttp()
        val serverB = mockServerHttp()

        // neither supplies a ktor client, so both derive from RpcClientConfig's shared default
        val providerA = Provider.builder(serverA.url).build(1L).unwrap()
        val providerB = Provider.builder(serverB.url).build(1L).unwrap()

        serverB.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1"}""")
        providerB.getBlockNumber().send().isSuccess() shouldBe true

        providerA.close()

        serverB.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":"0x2"}""")
        providerB.getBlockNumber().send().isSuccess() shouldBe true
    }

    test("closing a provider does not close a caller-supplied client") {
        val server = mockServerHttp()
        val ownClient = KtorHttpClient { install(WebSockets) }

        val provider = Provider.builder(server.url).httpClient(ownClient).build(1L).unwrap()
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1"}""")
        provider.getBlockNumber().send().isSuccess() shouldBe true

        provider.close()

        // the caller still owns their client, so it has to remain usable
        val second = Provider.builder(server.url).httpClient(ownClient).build(1L).unwrap()
        server.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":"0x2"}""")
        second.getBlockNumber().send().isSuccess() shouldBe true

        second.close()
        ownClient.close()
    }

    test("closing a provider still releases its own client") {
        val server = mockServerHttp()
        val provider = Provider.builder(server.url).build(1L).unwrap()

        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1"}""")
        provider.getBlockNumber().send().isSuccess() shouldBe true

        provider.close()

        // its own child client really is gone. Note this throws rather than returning a failed Result, unlike
        // transport errors - using a provider after closing it is a programming error, not an RPC failure.
        server.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":"0x2"}""")
        shouldThrow<Throwable> { provider.getBlockNumber().send() }
    }
})
