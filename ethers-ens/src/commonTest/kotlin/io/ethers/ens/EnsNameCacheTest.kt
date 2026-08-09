package io.ethers.ens

import io.ethers.core.types.Address
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

private val RESOLVER = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63")
private val VITALIK = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
private val NICK = Address("0xb8c2C29ee19D8307cb7255e1Cd9CbDE883A267d5")

// TestTimeSource has carried @ExperimentalTime across Kotlin versions and the test tasks only opt into
// RequiresOptIn/ExperimentalStdlibApi/ExperimentalKotest. Opting in explicitly compiles either way - if the
// marker is no longer required this is only a warning, and the build does not set allWarningsAsErrors.
@OptIn(ExperimentalTime::class)
class EnsNameCacheTest : FunSpec({
    test("returns a cached entry until its ttl elapses") {
        val time = TestTimeSource()
        val cache = EnsNameCache(ttl = 5.minutes, timeSource = time)
        cache.put("vitalik.eth", VITALIK)

        cache.get("vitalik.eth") shouldBe VITALIK

        time += 4.minutes
        cache.get("vitalik.eth") shouldBe VITALIK

        time += 2.minutes
        cache.get("vitalik.eth").shouldBeNull()
    }

    test("an expired entry is replaced rather than resurrected when it is re-put") {
        val time = TestTimeSource()
        val cache = EnsNameCache(ttl = 5.minutes, timeSource = time)
        cache.put("vitalik.eth", VITALIK)

        time += 6.minutes
        cache.get("vitalik.eth").shouldBeNull()

        // re-resolving after eviction caches the new value against a fresh mark
        cache.put("vitalik.eth", NICK)
        cache.get("vitalik.eth") shouldBe NICK

        time += 4.minutes
        cache.get("vitalik.eth") shouldBe NICK

        time += 2.minutes
        cache.get("vitalik.eth").shouldBeNull()
    }

    test("entries are independent, so evicting one leaves the others intact") {
        val time = TestTimeSource()
        val cache = EnsNameCache(ttl = 5.minutes, timeSource = time)
        cache.put("vitalik.eth", VITALIK)

        time += 3.minutes
        cache.put("nick.eth", NICK)

        // vitalik.eth expires and is evicted, nick.eth was cached 3 minutes later and still stands
        time += 3.minutes
        cache.get("vitalik.eth").shouldBeNull()
        cache.get("nick.eth") shouldBe NICK
    }

    test("clear drops every entry") {
        val cache = EnsNameCache()
        cache.put("vitalik.eth", VITALIK)

        cache.clear()

        cache.get("vitalik.eth").shouldBeNull()
    }

    test("EnsResolver serves a repeated lookup from cache instead of hitting the network again") {
        val client = FakeJsonRpcClient().enqueueAddressResolution(RESOLVER, VITALIK)
        val ens = EnsResolver(Provider(client, 1L))

        ens.resolveAddress("vitalik.eth").send().unwrap() shouldBe VITALIK
        val afterFirst = client.requests.size

        ens.resolveAddress("vitalik.eth").send().unwrap() shouldBe VITALIK

        client.requests.size shouldBe afterFirst
    }
})
