package io.ethers.ens

import io.ethers.core.types.CallRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Signature of every chaining builder on [type]: a non-synthetic, single-argument method that returns the
 * declaring type itself.
 */
private fun buildersOf(type: Class<*>): Set<String> {
    return type.declaredMethods
        .filter { !it.isSynthetic && it.returnType == type && it.parameterCount == 1 }
        .map { "${it.name}(${it.parameterTypes.single().simpleName})" }
        .toSet()
}

class EnsCallRequestParityTest : FunSpec({
    test("EnsCallRequest mirrors every CallRequest chaining builder except to(Address)") {
        val callRequest = buildersOf(CallRequest::class.java)
        val ensCallRequest = buildersOf(EnsCallRequest::class.java)

        // to(Address) is intentionally absent: an EnsCallRequest's recipient is always its ENS name
        (callRequest - ensCallRequest) shouldBe setOf("to(Address)")

        // nothing on EnsCallRequest that CallRequest does not have
        (ensCallRequest - callRequest) shouldBe emptySet()
    }
})
