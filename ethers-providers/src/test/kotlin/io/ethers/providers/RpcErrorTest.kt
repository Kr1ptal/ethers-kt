package io.ethers.providers

import io.ethers.core.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class RpcErrorTest : FunSpec({
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
                    """{"method":"‘eth_getHeaderByNumber’ does not exist/is not available"}""",
                ),
            ),
            TestCase(
                """{"code":3,"message":"Execution reverted","data":"0x12124214345676524127654123476541263765"}""",
                RpcError(RpcError.CODE_EXECUTION_ERROR, "Execution reverted", "0x12124214345676524127654123476541263765"),
            ),
        ).checkAll { (json, expected) ->
            val result = Jackson.MAPPER.readValue(json, RpcError::class.java)
            result shouldBe expected
        }
    }
})
