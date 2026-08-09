package io.ethers.ens

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class EnsCallRequestTest : FunSpec({
    test("toCallRequest throws instead of silently producing a request with no recipient") {
        val request = EnsCallRequest("vitalik.eth", CallRequest().data(Bytes("0x1234")))

        val thrown = shouldThrow<IllegalStateException> { request.toCallRequest() }

        thrown.message shouldContain "vitalik.eth"
        thrown.message shouldContain "EnsMiddleware"
    }

    test("resolveTo carries every other field over onto the resolved address") {
        val resolved = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
        val from = Address("0x0000000000000000000000000000000000000001")
        val request = EnsCallRequest(
            "vitalik.eth",
            CallRequest().from(from).data(Bytes("0x1234")).value(bigIntegerOf(7)).gas(21000L),
        )

        val call = request.resolveTo(resolved)

        call.to shouldBe resolved
        call.from shouldBe from
        call.data shouldBe Bytes("0x1234")
        call.value shouldBe bigIntegerOf(7)
        call.gas shouldBe 21000L
    }

    test("resolveTo does not mutate the request, so it can be resolved more than once") {
        val base = CallRequest().data(Bytes("0x1234"))
        val request = EnsCallRequest("vitalik.eth", base)
        val first = Address("0x0000000000000000000000000000000000000001")
        val second = Address("0x0000000000000000000000000000000000000002")

        request.resolveTo(first).to shouldBe first
        request.resolveTo(second).to shouldBe second
        base.to shouldBe null
    }

    test("chaining builders configure the underlying request and return EnsCallRequest") {
        val resolved = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
        val from = Address("0x0000000000000000000000000000000000000001")

        val call = EnsCallRequest("vitalik.eth")
            .from(from)
            .data(Bytes("0x1234"))
            .value(bigIntegerOf(7))
            .gas(21000L)
            .nonce(3L)
            .chainId(1L)
            .resolveTo(resolved)

        call.to shouldBe resolved
        call.from shouldBe from
        call.data shouldBe Bytes("0x1234")
        call.value shouldBe bigIntegerOf(7)
        call.gas shouldBe 21000L
        call.nonce shouldBe 3L
        call.chainId shouldBe 1L
    }

    test("the builder lambda overload configures via CallRequest's own property setters") {
        val resolved = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")

        val call = EnsCallRequest("vitalik.eth") {
            data = Bytes("0x1234")
            value = bigIntegerOf(7)
        }.resolveTo(resolved)

        call.to shouldBe resolved
        call.data shouldBe Bytes("0x1234")
        call.value shouldBe bigIntegerOf(7)
    }

    test("toEns wraps an existing CallRequest, keeping every field") {
        val resolved = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
        val from = Address("0x0000000000000000000000000000000000000001")

        val call = CallRequest()
            .from(from)
            .data(Bytes("0x1234"))
            .gas(21000L)
            .toEns("vitalik.eth")

        call.toEnsName shouldBe "vitalik.eth"

        val resolvedCall = call.resolveTo(resolved)
        resolvedCall.to shouldBe resolved
        resolvedCall.from shouldBe from
        resolvedCall.data shouldBe Bytes("0x1234")
        resolvedCall.gas shouldBe 21000L
    }

    test("toEns copies, so mutating the source afterwards does not leak into the wrapper") {
        val source = CallRequest().data(Bytes("0x1234"))
        val call = source.toEns("vitalik.eth")

        source.data = Bytes("0xffff")

        call.resolveTo(Address.ZERO).data shouldBe Bytes("0x1234")
    }
})
