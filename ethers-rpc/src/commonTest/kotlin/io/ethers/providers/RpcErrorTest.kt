package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.ThrowableError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

class RpcErrorTest : FunSpec({
    test("exception includes the error code") {
        val error = RpcError(RpcError.CODE_EXECUTION_ERROR, "execution reverted", null)
        val exception = error.toException()

        exception.message shouldBe "execution reverted (code: ${RpcError.CODE_EXECUTION_ERROR})"
        exception.toString() shouldBe "RpcError: execution reverted (code: ${RpcError.CODE_EXECUTION_ERROR})"

        // the error itself is still retained, so the code stays reachable as an Int
        (exception as ThrowableError.Exception).error shouldBe error
    }

    test("deserialization") {
        data class TestCase(
            val json: String,
            val expected: RpcError,
        )

        Exhaustive.of(
            TestCase(
                """{"code":-32700,"message":"Parse error"}""",
                RpcError(RpcError.CODE_PARSE_ERROR, "Parse error", null),
            ),
            TestCase(
                """{"code":-32601,"message":"Method not found"}""",
                RpcError(RpcError.CODE_METHOD_NOT_FOUND, "Method not found", null),
            ),
            TestCase(
                """{"code":-32601,"message":"Method not found","data":{"method":"‘eth_getHeaderByNumber’ does not exist/is not available"}}""",
                RpcError(
                    RpcError.CODE_METHOD_NOT_FOUND,
                    "Method not found",
                    Kotlinx.DEFAULT.parseToJsonElement("""{"method":"‘eth_getHeaderByNumber’ does not exist/is not available"}"""),
                ),
            ),
            TestCase(
                """{"code":3,"message":"Execution reverted","data":"0x12124214345676524127654123476541263765"}""",
                RpcError(
                    RpcError.CODE_EXECUTION_ERROR,
                    "Execution reverted",
                    Kotlinx.DEFAULT.parseToJsonElement("\"0x12124214345676524127654123476541263765\""),
                ),
            ),
        ).checkAll { (json, expected) ->
            val result = RpcError.fromJsonObject(Kotlinx.DEFAULT.parseToJsonElement(json).jsonObject)
            result shouldBe expected
        }
    }

    test("isHttpRateLimited is true for HTTP 429 only") {
        RpcError(RpcError.CODE_CALL_FAILED, "HTTP 429: Too Many Requests", httpStatus = 429).isHttpRateLimited shouldBe true
        RpcError(RpcError.CODE_CALL_FAILED, "HTTP 503: Service Unavailable", httpStatus = 503).isHttpRateLimited shouldBe false
        // a JSON-RPC code that mirrors 429 is not an HTTP status
        RpcError(429, "Too many requests").isHttpRateLimited shouldBe false
    }

    test("isRateLimited is true when a policy set rateLimit") {
        RpcError(-32000, "Too many requests").isRateLimited shouldBe false
        RpcError(-32000, "Too many requests", rateLimit = RateLimit(null)).isRateLimited shouldBe true
    }

    test("the HTTP policy finds HTTP 429 with its Retry-After, and nothing else") {
        val policy = RateLimitPolicy.HTTP

        policy.rateLimit(RpcError(RpcError.CODE_CALL_FAILED, "HTTP 429", httpStatus = 429, retryAfterMs = 3000L)) shouldBe
            RateLimit(3000L)
        policy.rateLimit(RpcError(RpcError.CODE_CALL_FAILED, "HTTP 429", httpStatus = 429)) shouldBe RateLimit(null)
        policy.rateLimit(RpcError(RpcError.CODE_CALL_FAILED, "HTTP 503", httpStatus = 503, retryAfterMs = 3000L)) shouldBe null
        policy.rateLimit(RpcError(429, "Your app has exceeded its compute units per second capacity.")) shouldBe null
        policy.rateLimit(RpcError(-32005, "project ID request rate exceeded")) shouldBe null
    }

    test("new fields default to null, so errors without them stay equal") {
        val error = RpcError(RpcError.CODE_EXECUTION_ERROR, "execution reverted")

        error.httpStatus shouldBe null
        error.retryAfterMs shouldBe null
        error.rateLimit shouldBe null
        error shouldBe RpcError(RpcError.CODE_EXECUTION_ERROR, "execution reverted", null, null, null, null, null)
    }

    test("parse Retry-After") {
        // Wed, 21 Oct 2015 07:28:00 GMT
        val dateMs = 1_445_412_480_000L

        data class TestCase(val value: String?, val expected: Long?)

        Exhaustive.of(
            TestCase(null, null),
            TestCase("", null),
            TestCase("   ", null),
            TestCase("0", 0L),
            TestCase("120", 120_000L),
            TestCase(" 5 ", 5_000L),
            TestCase("1.5", 1_500L),
            TestCase("-1", null),
            TestCase("-1.5", null),
            TestCase("NaN", null),
            TestCase("Infinity", null),
            TestCase("${Long.MAX_VALUE}", Long.MAX_VALUE),
            TestCase("soon", null),
            // HTTP date, measured from now
            TestCase("Wed, 21 Oct 2015 07:28:05 GMT", 5_000L),
            // a date in the past means no wait
            TestCase("Wed, 21 Oct 2015 07:27:00 GMT", 0L),
        ).checkAll { (value, expected) ->
            HttpClient.parseRetryAfterMs(value, nowMs = dateMs) shouldBe expected
        }
    }
})
