package io.ethers.ens

import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private val RESOLVER = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63")
private val VITALIK = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")

class EnsMiddlewareTest : FunSpec({
    test("resolves the ENS name and dispatches the call to the resolved address") {
        val client = FakeJsonRpcClient()
            .enqueueAddressResolution(RESOLVER, VITALIK)
            .enqueue("eth_call", "\"0xdeadbeef\"")
        val middleware = EnsMiddleware(Provider(client, 1L))

        val result = middleware
            .call(EnsCallRequest("vitalik.eth", CallRequest().data(Bytes("0x1234"))), BlockId.LATEST, null, null)
            .send()

        result.unwrap() shouldBe Bytes("0xdeadbeef")
        val dispatched = client.requests.last().params[0] as CallRequest
        dispatched.to shouldBe VITALIK
        dispatched.data shouldBe Bytes("0x1234")
    }

    // regression test: Kotlin class delegation generates forwarders for interface members that have default
    // bodies, so the convenience overloads would otherwise bypass the override above and hit `inner` directly
    test("the two-argument call overload also resolves the name") {
        val client = FakeJsonRpcClient()
            .enqueueAddressResolution(RESOLVER, VITALIK)
            .enqueue("eth_call", "\"0xdeadbeef\"")
        val middleware = EnsMiddleware(Provider(client, 1L))

        val result = middleware.call(EnsCallRequest("vitalik.eth"), BlockId.LATEST).send()

        result.unwrap() shouldBe Bytes("0xdeadbeef")
        (client.requests.last().params[0] as CallRequest).to shouldBe VITALIK
    }

    test("a plain CallRequest passes straight through without any resolution traffic") {
        val to = Address("0x0000000000000000000000000000000000000001")
        val client = FakeJsonRpcClient().enqueue("eth_call", "\"0xdeadbeef\"")
        val middleware = EnsMiddleware(Provider(client, 1L))

        middleware.call(CallRequest().to(to), BlockId.LATEST).send().unwrap() shouldBe Bytes("0xdeadbeef")

        client.requests.size shouldBe 1
        (client.requests[0].params[0] as CallRequest).to shouldBe to
    }

    test("a resolution failure surfaces as an RpcError that keeps the ENS error as its cause") {
        // registry returns the zero address, so no resolver is registered for the name
        val client = FakeJsonRpcClient().enqueue("eth_call", "\"${abiWord(Address.ZERO)}\"")
        val middleware = EnsMiddleware(Provider(client, 1L))

        val result = middleware.call(EnsCallRequest("nonexistent.eth"), BlockId.LATEST).send()

        result.isFailure() shouldBe true
        val error = result.unwrapError()
        error.code shouldBe EnsMiddleware.CODE_ENS_RESOLUTION_FAILED
        error.message shouldContain "nonexistent.eth"
        error.cause.shouldNotBeNull()
    }

    test("inner points at the wrapped middleware instead of reporting itself as the bottom layer") {
        val provider = Provider(FakeJsonRpcClient(), 1L)
        val middleware = EnsMiddleware(provider)

        middleware.inner shouldBe provider
        middleware.provider shouldBe provider
    }
})
