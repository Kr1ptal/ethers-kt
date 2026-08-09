package io.ethers.ens

import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.providers.Provider
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val RESOLVER = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63")
private val VITALIK = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")

class EnsResolverAccountTest : FunSpec({
    test("getBalance resolves the name and queries the resolved address") {
        val client = FakeJsonRpcClient()
            .enqueueAddressResolution(RESOLVER, VITALIK)
            .enqueue("eth_getBalance", "\"0x64\"")
        val ens = EnsResolver(Provider(client, 1L))

        ens.getBalance("vitalik.eth", BlockId.LATEST).send().unwrap() shouldBe bigIntegerOf(100)
        client.requests.last().params[0] shouldBe VITALIK
    }

    test("getCode resolves the name and queries the resolved address") {
        val client = FakeJsonRpcClient()
            .enqueueAddressResolution(RESOLVER, VITALIK)
            .enqueue("eth_getCode", "\"0x1234\"")
        val ens = EnsResolver(Provider(client, 1L))

        ens.getCode("vitalik.eth", BlockId.LATEST).send().isFailure() shouldBe false
        client.requests.last().params[0] shouldBe VITALIK
    }

    test("a resolution failure keeps the typed ENS error") {
        // the registry returns the zero address for the name and for its parent, so the walk up the tree
        // bottoms out at the empty parent and reports UnknownResolver
        val client = FakeJsonRpcClient()
            .enqueue("eth_call", "\"${abiWord(Address.ZERO)}\"")
            .enqueue("eth_call", "\"${abiWord(Address.ZERO)}\"")
        val ens = EnsResolver(Provider(client, 1L))

        val result = ens.getBalance("nonexistent.eth", BlockId.LATEST).send()

        result.isFailure() shouldBe true
        result.unwrapError().shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
    }

    test("an RPC failure after resolution is reported as RpcCallFailed") {
        // resolution succeeds, but no eth_getBalance response is queued
        val client = FakeJsonRpcClient().enqueueAddressResolution(RESOLVER, VITALIK)
        val ens = EnsResolver(Provider(client, 1L))

        val result = ens.getBalance("vitalik.eth", BlockId.LATEST).send()

        result.isFailure() shouldBe true
        result.unwrapError().shouldBeInstanceOf<EnsResolver.Error.RpcCallFailed>()
    }
})
