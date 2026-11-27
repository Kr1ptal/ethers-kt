package io.ethers.providers

import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.rpcParams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JsonRpcRequestWriterTest : FunSpec({
    // the request as it was built before it was written as text, with kotlinx's JSON tree
    fun treeRequest(method: String, id: Long, params: JsonArray): String = buildJsonObject {
        put("id", id)
        put("jsonrpc", "2.0")
        put("method", method)
        put("params", params)
    }.toString()

    // a method as the request writes it: as it is, quoted, if it needs no escaping
    fun jsonString(value: String): String = escapedJsonStringOrNull(value)?.decodeToString() ?: "\"$value\""

    context("method names are escaped as kotlinx does") {
        test("every ASCII character") {
            for (code in 0..0x7F) {
                val value = "a${code.toChar()}b"
                jsonString(value) shouldBe JsonPrimitive(value).toString()
            }
        }

        test("strings that need no escaping are written as they are") {
            escapedJsonStringOrNull("eth_getBalance") shouldBe null
            escapedJsonStringOrNull("") shouldBe null
            jsonString("eth_getBalance") shouldBe "\"eth_getBalance\""
            jsonString("") shouldBe "\"\""
        }

        test("escapes at the start, the end, next to each other, and non-ASCII characters") {
            val values = listOf(
                "\"start",
                "end\\",
                "\n\r\t\b\u000C\u0000\u001F",
                "mixed \"quote\" and \\ in eth_\u0001call",
                "unicode é ü 中文 😀    \u007F",
            )
            for (value in values) {
                jsonString(value) shouldBe JsonPrimitive(value).toString()
            }
        }
    }

    context("buildJsonRpcRequest") {
        test("is the request kotlinx's JSON tree writes, byte for byte") {
            val params = rpcParams("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", "latest")
            val tree = JsonArray(listOf(JsonPrimitive("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"), JsonPrimitive("latest")))
            for (id in listOf(0L, 1L, 42L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)) {
                buildJsonRpcRequest("eth_getBalance", id, params).decodeToString() shouldBe
                    treeRequest("eth_getBalance", id, tree)
            }
            buildJsonRpcRequest("eth_getBalance", 7L, params).decodeToString() shouldBe
                """{"id":7,"jsonrpc":"2.0","method":"eth_getBalance","params":["0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2","latest"]}"""
        }

        test("no params") {
            buildJsonRpcRequest("eth_blockNumber", 1L, rpcParams()).decodeToString() shouldBe
                """{"id":1,"jsonrpc":"2.0","method":"eth_blockNumber","params":[]}"""
        }

        test("a method that needs escaping is written as a valid JSON string") {
            val method = "odd\"method\\\n\u0001é"
            buildJsonRpcRequest(method, 3L, rpcParams(1)).decodeToString() shouldBe
                treeRequest(method, 3L, JsonArray(listOf(JsonPrimitive(1))))
        }

        test("is the UTF-8 of the request text, and exactly sized, with non-ASCII params") {
            val params = rpcParams("é ü 中文 😀", "\u0000\"")
            val text = treeRequest("eth_call", 12L, JsonArray(listOf(JsonPrimitive("é ü 中文 😀"), JsonPrimitive("\u0000\""))))
            buildJsonRpcRequest("eth_call", 12L, params) shouldBe text.encodeToByteArray()
        }

        test("ids of every number of digits") {
            var id = 1L
            while (id > 0) {
                for (value in longArrayOf(id - 1, id, -id, -(id - 1))) {
                    buildJsonRpcRequest("m", value, rpcParams()).decodeToString() shouldBe
                        treeRequest("m", value, JsonArray(emptyList()))
                }
                id = if (id > Long.MAX_VALUE / 10) -1 else id * 10
            }
        }
    }

    context("buildJsonRpcBatch") {
        test("writes every call into one array, asking for the ids in order") {
            // never sends anything: only the calls' methods and params are read
            val client = HttpClient("http://localhost")
            val calls = listOf(
                RpcCall(client, "eth_getBalance", rpcParams("0xa", "latest"), String.serializer()),
                RpcCall(client, "eth_blockNumber", rpcParams(), String.serializer()),
                RpcCall(client, "eth_get\"Code", rpcParams("0xb"), String.serializer()),
            )
            val params = calls.map { it.params.encodeBytes() }
            val indices = ArrayList<Int>()

            val batch = buildJsonRpcBatch(calls, params) { i ->
                indices.add(i)
                10L + i
            }

            indices shouldBe listOf(0, 1, 2)
            batch.decodeToString() shouldBe "[" +
                """{"id":10,"jsonrpc":"2.0","method":"eth_getBalance","params":["0xa","latest"]},""" +
                """{"id":11,"jsonrpc":"2.0","method":"eth_blockNumber","params":[]},""" +
                """{"id":12,"jsonrpc":"2.0","method":"eth_get\"Code","params":["0xb"]}""" +
                "]"
            client.close()
        }

        test("an empty batch is an empty array") {
            buildJsonRpcBatch(emptyList(), emptyList()) { error("no call") }.decodeToString() shouldBe "[]"
        }

        test("a single call is written with no comma") {
            val client = HttpClient("http://localhost")
            val calls = listOf(RpcCall(client, "eth_chainId", rpcParams(), String.serializer()))
            buildJsonRpcBatch(calls, calls.map { it.params.encodeBytes() }) { 1L }.decodeToString() shouldBe
                """[{"id":1,"jsonrpc":"2.0","method":"eth_chainId","params":[]}]"""
            client.close()
        }
    }
})
