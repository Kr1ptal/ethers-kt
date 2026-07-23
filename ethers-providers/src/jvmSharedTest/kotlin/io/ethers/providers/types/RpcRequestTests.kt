package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.providers.RpcError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RpcRequestTests : FunSpec({
    test("RpcRequest adapters execute the suspend send function") {
        var calls = 0
        val request = SuppliedRpcRequest {
            calls++
            success(calls)
        }

        request.send().unwrap() shouldBe 1
        request.sendAwait().unwrap() shouldBe 2
        request.sendAsync().get().unwrap() shouldBe 3
    }

    test("mapped RpcRequest uses the suspend send function") {
        val request = SuppliedRpcRequest { success(1) }.map { it + 1 }

        request.send().unwrap() shouldBe 2
        request.sendAsync().get().unwrap() shouldBe 2
    }

    test("RpcSubscribe adapters execute the suspend send function") {
        var calls = 0
        val error = RpcError(RpcError.CODE_CALL_FAILED, "failed")
        val request = object : RpcSubscribe<String, RpcError> {
            override suspend fun send(): Result<ChannelReceiver<String>, RpcError> {
                calls++
                return failure(error)
            }
        }

        request.send().unwrapError() shouldBe error
        request.sendAwait().unwrapError() shouldBe error
        request.sendAsync().get().unwrapError() shouldBe error
        calls shouldBe 3
    }

    test("BatchRpcRequest adapters execute the suspend send function") {
        val batch = BatchRpcRequest()

        batch.send() shouldBe false
        batch.sendAwait() shouldBe false
        batch.sendAsync().get() shouldBe false
    }

    test("Iterable send awaits all request results") {
        val requests = listOf(
            SuppliedRpcRequest { success(1) },
            SuppliedRpcRequest { success(2) },
        )

        requests.send().unwrap() shouldBe listOf(1, 2)
        requests.sendAwait().unwrap() shouldBe listOf(1, 2)
    }
})
