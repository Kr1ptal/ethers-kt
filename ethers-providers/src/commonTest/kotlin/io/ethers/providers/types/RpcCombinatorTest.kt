package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isSuccess
import io.ethers.core.success
import io.ethers.core.unwrap
import io.ethers.providers.RpcError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay

/**
 * Combinator behaviour that only relies on the suspending API, so it runs on every platform. The blocking and
 * CompletableFuture adapters are covered separately in jvmSharedTest.
 */
class RpcCombinatorTest : FunSpec({
    context("suspend combinator overloads") {
        val err = RpcError(RpcError.CODE_CALL_FAILED, "boom")
        val otherErr = RpcError(RpcError.CODE_CALL_TIMEOUT, "timeout")
        fun ok(v: Int) = SuppliedRpcRequest<Int, RpcError> { success(v) }
        fun bad() = SuppliedRpcRequest<Int, RpcError> { failure(err) }

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

    context("RpcSubscribe suspend combinator overloads") {
        val err = RpcError(RpcError.CODE_CALL_FAILED, "boom")
        val otherErr = RpcError(RpcError.CODE_CALL_TIMEOUT, "timeout")

        fun stream(vararg values: Int): ChannelReceiver<Int> {
            val channel = QueueChannel.mpscUnbounded<Int>()
            values.forEach { channel.offer(it) }
            return channel
        }

        fun ok(vararg values: Int): RpcSubscribe<Int, RpcError> = RpcSubscribeConstant(success(stream(*values)))

        fun bad(): RpcSubscribe<Int, RpcError> = RpcSubscribeConstant(failure(err))

        test("map applies a suspending mapper on success and skips on failure") {
            val mapped = ok(1, 2).map {
                delay(1)
                it.map { v -> v * 10 }
            }.send().unwrap()
            mapped.take() shouldBe 10
            mapped.take() shouldBe 20

            bad().map {
                delay(1)
                it.map { v -> v * 10 }
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
            }.send().isSuccess() shouldBe true
        }

        test("andThen chains a suspending fallible op on success and skips on failure") {
            ok(1).andThen {
                delay(1)
                success(it)
            }.send().isSuccess() shouldBe true
            ok(1).andThen<Int> {
                delay(1)
                failure(otherErr)
            }.send().unwrapError() shouldBe otherErr
            bad().andThen {
                delay(1)
                success(it)
            }.send().unwrapError() shouldBe err
        }

        test("orElse recovers via a suspending op on failure and skips on success") {
            bad().orElse {
                delay(1)
                success(stream(9))
            }.send().unwrap().take() shouldBe 9
            bad().orElse {
                delay(1)
                failure(otherErr)
            }.send().unwrapError() shouldBe otherErr
            ok(1).orElse {
                delay(1)
                success(stream(9))
            }.send().unwrap().take() shouldBe 1
        }

        test("onSuccess runs the suspending callback only on success, passing the result through") {
            var hit = 0
            ok(1).onSuccess {
                delay(1)
                hit++
            }.send().isSuccess() shouldBe true
            hit shouldBe 1

            bad().onSuccess {
                delay(1)
                hit++
            }.send().unwrapError() shouldBe err
            hit shouldBe 1
        }

        test("onFailure runs the suspending callback only on failure, passing the result through") {
            var seen: RpcError? = null
            bad().onFailure {
                delay(1)
                seen = it
            }.send().unwrapError() shouldBe err
            seen shouldBe err

            seen = null
            ok(1).onFailure {
                delay(1)
                seen = it
            }.send().isSuccess() shouldBe true
            seen shouldBe null
        }
    }
})
