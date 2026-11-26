package io.ethers.ens

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class EnsCallRequestTest : FunSpec({
    test("toCallRequest throws instead of silently producing a request with no recipient") {
        val request = EnsCallRequest("vitalik.eth", CallRequest.Builder().data(Bytes("0x1234")).build())

        val thrown = shouldThrow<IllegalStateException> { request.toCallRequest() }

        thrown.message shouldContain "vitalik.eth"
        thrown.message shouldContain "EnsMiddleware"
    }

    test("resolveTo carries every other field over onto the resolved address") {
        val resolved = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
        val from = Address("0x0000000000000000000000000000000000000001")
        val request = EnsCallRequest(
            "vitalik.eth",
            CallRequest.Builder().from(from).data(Bytes("0x1234")).value(bigIntegerOf(7)).gas(21000L).build(),
        )

        val call = request.resolveTo(resolved)

        call.to shouldBe resolved
        call.from shouldBe from
        call.data shouldBe Bytes("0x1234")
        call.value shouldBe bigIntegerOf(7)
        call.gas shouldBe 21000L
    }

    test("resolveTo does not mutate the request, so it can be resolved more than once") {
        val base = CallRequest.Builder().data(Bytes("0x1234")).build()
        val request = EnsCallRequest("vitalik.eth", base)
        val first = Address("0x0000000000000000000000000000000000000001")
        val second = Address("0x0000000000000000000000000000000000000002")

        request.resolveTo(first).to shouldBe first
        request.resolveTo(second).to shouldBe second
        base.to shouldBe null
    }

    test("copy changes the underlying request and leaves the original untouched") {
        val resolved = Address("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
        val from = Address("0x0000000000000000000000000000000000000001")
        val original = EnsCallRequest("vitalik.eth") { data = Bytes("0x1234") }

        val call = original.copy {
            from(from)
            value(bigIntegerOf(7))
            gas(21000L)
        }

        call.toEns shouldBe "vitalik.eth"
        val resolvedCall = call.resolveTo(resolved)
        resolvedCall.to shouldBe resolved
        resolvedCall.from shouldBe from
        resolvedCall.data shouldBe Bytes("0x1234")
        resolvedCall.value shouldBe bigIntegerOf(7)
        resolvedCall.gas shouldBe 21000L

        original.request.from shouldBe null
        original.request.gas shouldBe -1L
    }

    test("the single-argument constructor carries an empty request") {
        EnsCallRequest("vitalik.eth").request shouldBe CallRequest {}
    }

    test("equal names and requests are equal") {
        EnsCallRequest("vitalik.eth") { data = Bytes("0x1234") } shouldBe EnsCallRequest("vitalik.eth") { data = Bytes("0x1234") }
        EnsCallRequest("vitalik.eth") shouldNotBe EnsCallRequest("nick.eth")
        EnsCallRequest("vitalik.eth") shouldNotBe EnsCallRequest("vitalik.eth") { nonce = 1 }
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

        val call = CallRequest {
            from(from)
            data(Bytes("0x1234"))
            gas(21000L)
        }.toEns("vitalik.eth")

        call.toEns shouldBe "vitalik.eth"

        val resolvedCall = call.resolveTo(resolved)
        resolvedCall.to shouldBe resolved
        resolvedCall.from shouldBe from
        resolvedCall.data shouldBe Bytes("0x1234")
        resolvedCall.gas shouldBe 21000L
    }

    test("resolveTo leaves the wrapped request untouched, so it can be resolved again") {
        val call = EnsCallRequest("vitalik.eth") { data = Bytes("0x1234") }
        val first = Address("0x0000000000000000000000000000000000000001")

        call.resolveTo(first)

        call.request.to shouldBe null
        call.resolveTo(first).data shouldBe Bytes("0x1234")
    }
})
