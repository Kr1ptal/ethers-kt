package io.ethers.ens

import io.ethers.EnsResolver
import io.ethers.EnsResolver.Companion.resolveName
import io.ethers.core.types.Address
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val MAINNET_HTTP_RPC = "https://ethereum.publicnode.com"

class EnsResolverTest : FunSpec({
    test("Resolve resolver.eth") {
        val provider = Provider(HttpClient(MAINNET_HTTP_RPC))
        val resolver = EnsResolver(provider)
        resolver.resolveName("resolver.eth") shouldBe Address("0x231b0ee14048e9dccd1d247744d114a4eb5e8e63")

    }

    test("Resolve via provider") {
        val provider = Provider(HttpClient(MAINNET_HTTP_RPC))
        provider.resolveName("resolver.eth") shouldBe Address("0x231b0ee14048e9dccd1d247744d114a4eb5e8e63")
    }
})

