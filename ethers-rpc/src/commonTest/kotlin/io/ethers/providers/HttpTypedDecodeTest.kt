package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.isFailure
import io.ethers.providers.types.RpcCall
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

@Serializable
private data class Item(val a: Long, val b: String? = null)

/** Response bodies covering field order, nulls, missing and duplicate fields, unknown keys and malformed input. */
private val BODIES = listOf(
    """{"jsonrpc":"2.0","id":1,"result":RESULT}""",
    """{"result":RESULT,"id":1,"jsonrpc":"2.0"}""",
    """ { "jsonrpc" : "2.0" ,
         "id" : 1 , "result" : RESULT } """,
    """{"jsonrpc":"2.0","id":1,"result":null}""",
    """{"jsonrpc":"2.0","id":"7","result":RESULT}""",
    """{"jsonrpc":"2.0","id":null,"result":RESULT}""",
    """{"jsonrpc":"2.0","id":"abc","result":RESULT}""",
    """{"jsonrpc":"2.0","result":RESULT}""",
    """{"jsonrpc":"2.0","result":{"bad":true}}""",
    """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"boom","data":"0x12"}}""",
    """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"boom"},"result":RESULT}""",
    """{"jsonrpc":"2.0","id":1,"result":RESULT,"error":{"code":-32000,"message":"boom"}}""",
    """{"jsonrpc":"2.0","id":1,"error":null}""",
    """{"jsonrpc":"2.0","id":1,"error":"text"}""",
    """{"jsonrpc":"2.0","id":1}""",
    """{"jsonrpc":"2.0","id":1,"extra":{"nested":[1,{"x":null}],"s":"}"},"result":RESULT,"more":[]}""",
    """{"jsonrpc":"2.0","id":1,"result":{"bad":true},"result":RESULT}""",
    """{"jsonrpc":"2.0","id":1,"result":RESULT,"result":{"bad":true}}""",
    """{"jsonrpc":"2.0","id":1,"result":{"bad":[1,2]}}""",
    """{"jsonrpc":"2.0","id":1,"result":"text"}""",
    """{"jsonrpc":"2.0","id":1,"result":12}""",
    """{"jsonrpc":"2.0","id":1,"result":RESULT""",
    """{"jsonrpc":"2.0","id":1,"result":RESULT}}""",
    """[{"jsonrpc":"2.0","id":1,"result":RESULT}]""",
    "",
    "not json",
    "null",
)

private class Case<T>(val name: String, val serializer: KSerializer<T>, val result: String)

private val CASES = listOf(
    Case("string", String.serializer(), "\"a\\\"b\\u0041\""),
    Case("nullable string", String.serializer().nullable, "\"0x1\""),
    Case("long", Long.serializer(), "42"),
    Case("list", ListSerializer(String.serializer()), """["x","y"]"""),
    Case("object", Item.serializer(), """{"b":"q","a":5,"unknown":{"z":[1]}}"""),
    Case("nullable object", Item.serializer().nullable, """{"a":5}"""),
    Case("custom serializer", SerializableValue.serializer(), "\"0x11\""),
)

private val NO_ID = HttpClient.ERROR_NO_ID_RESPONSE.unwrapError().let { Triple(it.code, it.message, it.data) }
private val INVALID = HttpClient.ERROR_INVALID_RESPONSE.unwrapError().let { Triple(it.code, it.message, it.data) }
private const val CALL_FAILED = "failed: ${RpcError.CODE_CALL_FAILED}"

/** The parts of an outcome every route must agree on; a call failure's message carries the decoder's own wording. */
private fun <T> Result<T, RpcError>.outcome(): Any? = if (isFailure()) {
    val error = error
    if (error.code == RpcError.CODE_CALL_FAILED) CALL_FAILED else Triple(error.code, error.message, error.data)
} else {
    value
}

/**
 * The outcome of decoding [body] the way the client did before it decoded responses directly: parse the whole
 * response into a tree, check the `id`, then pass the `result` subtree to [decode].
 */
private fun treeOutcome(body: String, decode: (KJsonElement) -> Any?): Any? = try {
    val response = Kotlinx.DEFAULT.parseToJsonElement(body).jsonObject
    val result = response["result"]
    val error = response["error"]
    when {
        response["id"]?.jsonPrimitive?.long == null -> NO_ID
        result != null -> decode(result)
        error != null -> RpcError.fromJsonObject(error.jsonObject).let { Triple(it.code, it.message, it.data) }
        else -> INVALID
    }
} catch (_: Exception) {
    CALL_FAILED
}

/**
 * The outcome of decoding [body] the way [HttpClient.request] does, a thrown exception included, without the HTTP
 * round trip: hundreds of those take most of a minute on Kotlin/Native. The client test below checks that a body
 * received over HTTP ends the same way, at either status.
 */
private fun <T> directOutcome(body: String, serializer: DeserializationStrategy<T>): Any? = try {
    Kotlinx.DEFAULT.decodeFromString(RpcResponseDeserializer(serializer), body).outcome()
} catch (_: Exception) {
    CALL_FAILED
}

class HttpTypedDecodeTest : FunSpec({
    lateinit var server: MockServer
    lateinit var client: HttpClient

    beforeSpec {
        server = mockServerHttp()
        client = HttpClient(server.url, KtorHttpClient())
    }

    afterSpec {
        client.close()
        server.stop()
    }

    suspend fun <T> outcome(status: Int, body: String, serializer: DeserializationStrategy<T>): Any? {
        server.enqueue(status, body)
        return client.request("m", emptyArray<Any>(), serializer).outcome()
    }

    test("decoding a response straight into the result matches decoding it through a JSON tree") {
        for (case in CASES) {
            val decodeTree = { element: KJsonElement -> Kotlinx.DEFAULT.decodeFromJsonElement(case.serializer, element) }
            for (template in BODIES) {
                val body = template.replace("RESULT", case.result)
                // The direct route decodes each value as the stream reaches it, so it may fail where the tree does
                // not, in two cases: without an id, the tree reports the missing id before decoding the result;
                // with duplicate results, which JSON-RPC servers do not send, the tree keeps only the last one.
                // Either way the direct route fails rather than returning a value.
                val noId = "\"id\"" !in body
                val duplicateResult = body.split("\"result\"").size > 2
                fun check(route: String, actual: Any?, expected: Any?) = withClue("${case.name}, $route: $body") {
                    if (((noId && expected == NO_ID) || duplicateResult) && actual == CALL_FAILED) return@withClue
                    actual shouldBe expected
                }

                val expected = treeOutcome(body, decodeTree)
                check("serializer", directOutcome(body, case.serializer), expected)
                // a mapped strategy decodes exactly as the one it maps
                check("mapped serializer", directOutcome(body, case.serializer.map { it }), expected)
            }
        }
    }

    test("the client ends every response body as decoding it directly does, at HTTP 200 and 500") {
        // a non-2xx body still carries a JSON-RPC response, so it is decoded too, but a failure to decode it is a call failure
        val case = CASES.first { it.name == "object" }
        for (template in BODIES) {
            val body = template.replace("RESULT", case.result)
            for (status in listOf(200, 500)) {
                withClue("HTTP $status: $body") {
                    outcome(status, body, case.serializer) shouldBe directOutcome(body, case.serializer)
                }
            }
        }
    }

    test("a call passes its deserializer to the client unchanged") {
        val recording = RecordingClient(client)
        val serializer = ListSerializer(String.serializer())

        server.enqueueJson("""{"result":["x","y"],"id":1,"jsonrpc":"2.0"}""")
        RpcCall(recording, "m", emptyArray<Any>(), serializer).send().outcome() shouldBe listOf("x", "y")
        recording.deserializers.single() shouldBeSameInstanceAs serializer
    }

    test("a mapped strategy transforms the decoded value, and a failing transform fails the call") {
        val mapped = Item.serializer().map { "${it.a}:${it.b}" }
        mapped.descriptor shouldBe Item.serializer().descriptor

        outcome(200, """{"jsonrpc":"2.0","id":1,"result":{"a":5,"b":"q"}}""", mapped) shouldBe "5:q"
        outcome(200, """{"jsonrpc":"2.0","id":1,"result":{"a":5}}""", Item.serializer().map { require(it.b != null) }) shouldBe CALL_FAILED
        // an error response never reaches the transform
        outcome(200, """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"boom"}}""", mapped) shouldBe Triple(-32000, "boom", null)
    }
})

/** Delegates to [delegate], recording the deserializer of each request. */
private class RecordingClient(private val delegate: HttpClient) : JsonRpcClient by delegate {
    val deserializers = mutableListOf<DeserializationStrategy<*>>()

    override suspend fun <T> request(method: String, params: Array<*>, resultDeserializer: DeserializationStrategy<T>): Result<T, RpcError> {
        deserializers += resultDeserializer
        return delegate.request(method, params, resultDeserializer)
    }
}
