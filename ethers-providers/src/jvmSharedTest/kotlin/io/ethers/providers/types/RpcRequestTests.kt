package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import io.ethers.core.failure
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

    context("suspend combinator overloads") {
        val err = RpcError(RpcError.CODE_CALL_FAILED, "boom")
        val otherErr = RpcError(RpcError.CODE_CALL_TIMEOUT, "timeout")
        fun ok(v: Int) = SuppliedRpcRequest { success(v) }
        fun bad() = SuppliedRpcRequest<Int> { failure(err) }

        test("map applies a suspending mapper on success and skips on failure") {
            ok(1).map {
                delay(1)
                it + 1
            }.send().unwrap() shouldBe 2
            bad().map {
                delay(1)
                it + 1
            }.send().unwrapError() shouldBe err
        }

        test("mapError applies a suspending mapper on failure and skips on success") {
            bad().mapError {
                delay(1)
                otherErr
            }.send().unwrapError() shouldBe otherErr
            ok(1).mapError {
                delay(1)
                otherErr
            }.send().unwrap() shouldBe 1
        }

        test("andThen chains a suspending fallible op on success and skips on failure") {
            ok(1).andThen {
                delay(1)
                success(it * 10)
            }.send().unwrap() shouldBe 10
            ok(1).andThen {
                delay(1)
                failure(otherErr)
            }.send().unwrapError() shouldBe otherErr
            bad().andThen {
                delay(1)
                success(it * 10)
            }.send().unwrapError() shouldBe err
        }

        test("orElse recovers via a suspending op on failure and skips on success") {
            bad().orElse {
                delay(1)
                success(42)
            }.send().unwrap() shouldBe 42
            bad().orElse {
                delay(1)
                failure(otherErr)
            }.send().unwrapError() shouldBe otherErr
            ok(1).orElse {
                delay(1)
                success(42)
            }.send().unwrap() shouldBe 1
        }

        test("onSuccess runs the suspending callback only on success, passing the result through") {
            var seen: Int? = null
            ok(7).onSuccess {
                delay(1)
                seen = it
            }.send().unwrap() shouldBe 7
            seen shouldBe 7

            seen = null
            bad().onSuccess {
                delay(1)
                seen = it
            }.send().unwrapError() shouldBe err
            seen shouldBe null
        }

        test("onFailure runs the suspending callback only on failure, passing the result through") {
            var seen: RpcError? = null
            bad().onFailure {
                delay(1)
                seen = it
            }.send().unwrapError() shouldBe err
            seen shouldBe err

            seen = null
            ok(7).onFailure {
                delay(1)
                seen = it
            }.send().unwrap() shouldBe 7
            seen shouldBe null
        }

        test("suspend combinators also apply when the request is batched") {
            val batch = BatchRpcRequest()
            val response = ok(1).map {
                delay(1)
                it + 1
            }.batch(batch)
            batch.send()

            response.await().unwrap() shouldBe 2
        }

        test("Transformer overload stays reachable when passed explicitly") {
            val doubled = ok(21).map(Result.Transformer<Int, Int> { it * 2 })

            doubled.send().unwrap() shouldBe 42
        }
    }

    test("BatchRequest supports blocking and suspending await") {
        val blocking = batchRequest(
            SuppliedRpcRequest { success(1) },
            SuppliedRpcRequest { success(2) },
        ).await()
        blocking.unwrap() shouldBe BatchResponse2(1, 2)

        val suspending = batchRequest(
            SuppliedRpcRequest { success(3) },
            SuppliedRpcRequest { success(4) },
        ).awaitSuspend()
        suspending.unwrap() shouldBe BatchResponse2(3, 4)
    }
})
