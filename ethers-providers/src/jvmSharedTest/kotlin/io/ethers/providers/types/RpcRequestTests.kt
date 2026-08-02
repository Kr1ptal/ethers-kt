package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isSuccess
import io.ethers.core.success
import io.ethers.providers.RpcError
import io.ethers.providers.types.sendAsync
import io.ethers.providers.types.sendAwait
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay

class RpcRequestTests : FunSpec({
    test("RpcRequest adapters execute the suspend send function") {
        var calls = 0
        val request = SuppliedRpcRequest<Int, RpcError> {
            calls++
            success(calls)
        }

        request.send().unwrap() shouldBe 1
        request.sendAwait().unwrap() shouldBe 2
        request.sendAsync().get().unwrap() shouldBe 3
    }

    test("mapped RpcRequest uses the suspend send function") {
        val request = SuppliedRpcRequest<Int, RpcError> { success(1) }.map { it + 1 }

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
            SuppliedRpcRequest<Int, RpcError> { success(1) },
            SuppliedRpcRequest<Int, RpcError> { success(2) },
        )

        requests.send().unwrap() shouldBe listOf(1, 2)
        requests.sendAwait().unwrap() shouldBe listOf(1, 2)
    }

    test("BatchRequest supports blocking and suspending await") {
        val blocking = batchRequest(
            SuppliedRpcRequest<Int, RpcError> { success(1) },
            SuppliedRpcRequest<Int, RpcError> { success(2) },
        ).await()
        blocking.unwrap() shouldBe BatchResponse2(1, 2)

        val suspending = batchRequest(
            SuppliedRpcRequest<Int, RpcError> { success(3) },
            SuppliedRpcRequest<Int, RpcError> { success(4) },
        ).awaitSuspend()
        suspending.unwrap() shouldBe BatchResponse2(3, 4)
    }
})
