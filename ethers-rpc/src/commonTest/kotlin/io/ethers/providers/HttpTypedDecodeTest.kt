package io.ethers.providers

import io.ethers.core.Result
import io.ethers.core.isFailure
import io.ethers.providers.types.RpcCall
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
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
private const val CALL_FAILED = "failed: ${RpcError.CODE_CALL_FAILED}"

/** The parts of an outcome both routes must agree on; a call failure's message carries the decoder's own wording. */
private fun <T> Result<T, RpcError>.outcome(): Any? = if (isFailure()) {
    val error = error
    if (error.code == RpcError.CODE_CALL_FAILED) CALL_FAILED else Triple(error.code, error.message, error.data)
} else {
    value
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

    suspend fun <T> outcomes(status: Int, body: String, serializer: KSerializer<T>): Pair<Any?, Any?> {
        server.enqueue(status, body)
        val typed = client.request("m", emptyArray<Any>(), serializer)
        server.enqueue(status, body)
        val tree = client.request("m", emptyArray<Any>(), decoderFor(serializer))
        return typed.outcome() to tree.outcome()
    }

    test("decoding a response straight into the result matches decoding it through a JSON tree") {
        for (case in CASES) {
            for (template in BODIES) {
                val body = template.replace("RESULT", case.result)
                for (status in listOf(200, 500)) {
                    withClue("${case.name}, HTTP $status: $body") {
                        val (typed, tree) = outcomes(status, body, case.serializer)
                        // The typed route decodes each value as the stream reaches it, so it may fail where the tree
                        // route does not, in two cases: without an id, the tree route reports the missing id before
                        // decoding the result; with duplicate results, which JSON-RPC servers do not send, the tree
                        // keeps only the last one. Either way the typed route fails rather than returning a value.
                        val noId = "\"id\"" !in body && tree == NO_ID
                        val duplicateResult = body.split("\"result\"").size > 2
                        if ((noId || duplicateResult) && typed == CALL_FAILED) return@withClue
                        typed shouldBe tree
                    }
                }
            }
        }
    }

    test("a call built with a serializer decodes through the typed route, and one built with a decoder through the tree") {
        val recording = RecordingClient(client)
        server.enqueueJson("""{"result":["x","y"],"id":1,"jsonrpc":"2.0"}""")
        RpcCall(recording, "m", emptyArray<Any>(), ListSerializer(String.serializer())).send().outcome() shouldBe listOf("x", "y")
        recording.routes shouldBe listOf("typed")

        server.enqueueJson("""{"result":["x","y"],"id":1,"jsonrpc":"2.0"}""")
        RpcCall(recording, "m", emptyArray<Any>(), decoderFor(ListSerializer(String.serializer()))).send().outcome() shouldBe listOf("x", "y")
        recording.routes shouldBe listOf("typed", "tree")
    }
})

/** Delegates to [delegate], recording which request route each call took. */
private class RecordingClient(private val delegate: HttpClient) : JsonRpcClient by delegate {
    val routes = mutableListOf<String>()

    override suspend fun <T> request(method: String, params: Array<*>, resultSerializer: KSerializer<T>): Result<T, RpcError> {
        routes += "typed"
        return delegate.request(method, params, resultSerializer)
    }

    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (KJsonElement) -> T): Result<T, RpcError> {
        routes += "tree"
        return delegate.request(method, params, resultDecoder)
    }
}
