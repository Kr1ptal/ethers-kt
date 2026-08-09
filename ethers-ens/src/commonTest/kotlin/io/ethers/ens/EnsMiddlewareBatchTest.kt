package io.ethers.ens

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.Provider
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement as KJsonElement

private val RESOLVER = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63")
private val VITALIK = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
private val NICK = Address("0xb8c2C29ee19D8307cb7255e1Cd9CbDE883A267d5")

/**
 * Wraps a [FakeJsonRpcClient] and records how many requests were in flight at once.
 *
 * Each request yields before delegating, so any coroutine that has already started gets a chance to enter the
 * request before the first one completes. Sequential resolution therefore peaks at 1, concurrent at N.
 */
private class ConcurrencyTrackingClient(private val delegate: FakeJsonRpcClient) : JsonRpcClient {
    private var inFlight = 0
    var maxInFlight = 0
        private set

    override suspend fun <T> request(
        method: String,
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): Result<T, RpcError> {
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        yield()
        val result = delegate.request(method, params, resultDecoder)
        inFlight--
        return result
    }

    override suspend fun requestBatch(batch: BatchRpcRequest) = delegate.requestBatch(batch)

    override suspend fun <T : Any> subscribe(
        params: Array<*>,
        resultDecoder: (KJsonElement) -> T,
    ): Result<ChannelReceiver<T>, RpcError> = delegate.subscribe(params, resultDecoder)

    override fun close() = delegate.close()
}

class EnsMiddlewareBatchTest : FunSpec({
    test("callMany resolves every ENS name and dispatches the resolved addresses") {
        val plainTo = Address("0x0000000000000000000000000000000000000001")
        val client = FakeJsonRpcClient()
            .serveEnsNames(RESOLVER, "vitalik.eth" to VITALIK, "nick.eth" to NICK)
            .enqueue("eth_callMany", """[{"value":"0xaa"},{"value":"0xbb"},{"value":"0xcc"}]""")
        val middleware = EnsMiddleware(Provider(client, 1L))

        val result = middleware.callMany(
            BlockId.LATEST,
            listOf(
                EnsCallRequest("vitalik.eth").data(Bytes("0x1234")),
                CallRequest().to(plainTo),
                EnsCallRequest("nick.eth"),
            ),
        ).send()

        result.unwrap().size shouldBe 3

        // the bundle keeps input order, with only the ENS entries rewritten
        val bundle = client.requests.last().params[0].toString()
        bundle shouldContain VITALIK.toString()
        bundle shouldContain plainTo.toString()
        bundle shouldContain NICK.toString()
    }

    test("names in a batch resolve concurrently rather than one after another") {
        val fake = FakeJsonRpcClient()
            .serveEnsNames(RESOLVER, "vitalik.eth" to VITALIK, "nick.eth" to NICK)
            .enqueue("eth_callMany", """[{"value":"0xaa"},{"value":"0xbb"}]""")
        val client = ConcurrencyTrackingClient(fake)
        val middleware = EnsMiddleware(Provider(client, 1L))

        middleware.callMany(
            BlockId.LATEST,
            listOf(EnsCallRequest("vitalik.eth"), EnsCallRequest("nick.eth")),
        ).send().unwrap()

        // sequential resolution would never have more than one request in flight
        client.maxInFlight shouldBe 2
    }

    test("a batch with no ENS names passes through without any resolution traffic") {
        val to = Address("0x0000000000000000000000000000000000000001")
        val client = FakeJsonRpcClient().enqueue("eth_callMany", """[{"value":"0xaa"}]""")
        val middleware = EnsMiddleware(Provider(client, 1L))

        middleware.callMany(BlockId.LATEST, listOf(CallRequest().to(to))).send().unwrap()

        client.requests.size shouldBe 1
        client.requests[0].method shouldBe "eth_callMany"
    }

    test("one unresolvable name fails the whole batch") {
        // nonexistent.eth is not registered, so the registry reports no resolver for it or its parent
        val client = FakeJsonRpcClient()
            .serveEnsNames(RESOLVER, "vitalik.eth" to VITALIK)
        val middleware = EnsMiddleware(Provider(client, 1L))

        val result = middleware.callMany(
            BlockId.LATEST,
            listOf(EnsCallRequest("vitalik.eth"), EnsCallRequest("nonexistent.eth")),
        ).send()

        result.isFailure() shouldBe true
        result.unwrapError().code shouldBe EnsMiddleware.CODE_ENS_RESOLUTION_FAILED
        result.unwrapError().message shouldContain "nonexistent.eth"

        // the batch itself was never dispatched
        client.requests.none { it.method == "eth_callMany" } shouldBe true
    }

    // regression test: same delegation trap as the single-call overloads
    test("the block-number callMany overload also resolves names") {
        val client = FakeJsonRpcClient()
            .serveEnsNames(RESOLVER, "vitalik.eth" to VITALIK)
            .enqueue("eth_callMany", """[{"value":"0xaa"}]""")
        val middleware = EnsMiddleware(Provider(client, 1L))

        middleware.callMany(1L, listOf(EnsCallRequest("vitalik.eth"))).send().unwrap()

        client.requests.last().params[0].toString() shouldContain VITALIK.toString()
    }
})
