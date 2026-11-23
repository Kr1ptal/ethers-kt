package io.ethers.aa.bundler

import io.ethers.aa.FakeJsonRpcClient
import io.ethers.core.types.Hash
import io.ethers.providers.types.PendingInclusion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class PendingUserOperationBlockingApiTest : FunSpec({
    val userOpHash = Hash("0x" + "ab".repeat(32))

    test("awaitInclusion and inclusionAsync are inherited from PendingInclusion") {
        val pending = PendingUserOperation(userOpHash, BundlerClient(FakeJsonRpcClient().respond("eth_getUserOperationReceipt", "null")))
        pending.awaitInclusion(2, 1.milliseconds).unwrapError() shouldBe PendingInclusion.Error.NoInclusion(userOpHash, 2)
        pending.inclusionAsync(2, 1.milliseconds).get().unwrapError() shouldBe PendingInclusion.Error.NoInclusion(userOpHash, 2)
    }
})
