package io.ethers.ens

import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FakeJsonRpcClientTest : FunSpec({
    test("serves queued responses in order and records every request") {
        val client = FakeJsonRpcClient()
            .enqueue("eth_call", "\"0xaaaa\"")
            .enqueue("eth_call", "\"0xbbbb\"")
        val provider = Provider(client, 1L)
        val to = Address("0x0000000000000000000000000000000000000001")

        provider.call(CallRequest().to(to), BlockId.LATEST).send().unwrap() shouldBe Bytes("0xaaaa")
        provider.call(CallRequest().to(to), BlockId.LATEST).send().unwrap() shouldBe Bytes("0xbbbb")

        client.requests.size shouldBe 2
        client.requests[0].method shouldBe "eth_call"
        (client.requests[0].params[0] as CallRequest).to shouldBe to
    }

    test("returns an RpcError when the script is exhausted") {
        val client = FakeJsonRpcClient()
        val provider = Provider(client, 1L)

        val result = provider.call(CallRequest(), BlockId.LATEST).send()

        result.isFailure() shouldBe true
        result.unwrapError().message shouldBe "FakeJsonRpcClient: no queued response for 'eth_call'"
    }

    test("enqueueAddressResolution drives a full EnsResolver.resolveAddress") {
        val resolverAddr = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63")
        val resolved = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
        val client = FakeJsonRpcClient().enqueueAddressResolution(resolverAddr, resolved)
        val ens = EnsResolver(Provider(client, 1L))

        ens.resolveAddress("vitalik.eth").send().unwrap() shouldBe resolved
        client.requests.size shouldBe 4
    }
})
