package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.isFailure
import io.ethers.providers.types.RpcCall
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
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

    suspend fun <T> outcome(status: Int, body: String, decoder: (KJsonElement) -> T): Any? {
        server.enqueue(status, body)
        return client.request("m", emptyArray<Any>(), decoder).outcome()
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

                for (status in listOf(200, 500)) {
                    val serializerExpected = treeOutcome(body, decodeTree)
                    check("serializer, HTTP $status", outcome(status, body, case.serializer), serializerExpected)
                    check("tree decoder, HTTP $status", outcome(status, body, decodeTree), serializerExpected)
                    // a decoder function sees exactly the result's subtree
                    check("identity decoder, HTTP $status", outcome(status, body) { it }, treeOutcome(body) { it })
                }
            }
        }
    }

    test("a call passes its deserializer to the client unchanged, and wraps a decoder function in one") {
        val recording = RecordingClient(client)
        val serializer = ListSerializer(String.serializer())
        val response = """{"result":["x","y"],"id":1,"jsonrpc":"2.0"}"""

        server.enqueueJson(response)
        RpcCall(recording, "m", emptyArray<Any>(), serializer).send().outcome() shouldBe listOf("x", "y")
        recording.deserializers.single() shouldBeSameInstanceAs serializer

        val decoder = { element: KJsonElement -> element.jsonArray.map { it.jsonPrimitive.content } }
        val call = RpcCall(recording, "m", emptyArray<Any>(), decoder)
        call.resultDecoder shouldBeSameInstanceAs decoder
        server.enqueueJson(response)
        call.send().outcome() shouldBe listOf("x", "y")
        recording.deserializers.last().shouldBeInstanceOf<JsonElementDeserializer<*>>().resultDecoder shouldBeSameInstanceAs decoder
    }

    test("a call made with a deserializer still offers a decoder function of the result's tree") {
        val call = RpcCall(client, "m", emptyArray<Any>(), Item.serializer().nullable)
        call.resultDecoder(Kotlinx.DEFAULT.parseToJsonElement("""{"a":5,"b":"q"}""")) shouldBe Item(5, "q")
        call.resultDecoder(JsonNull) shouldBe null
    }

    test("a decoder function only decodes JSON") {
        val error = shouldThrow<SerializationException> {
            JsonElementDeserializer { it }.deserialize(NonJsonDecoder)
        }
        error.message shouldContain "cannot read from"
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

/** A [Decoder] of some format other than JSON. */
@OptIn(ExperimentalSerializationApi::class)
private object NonJsonDecoder : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE
}
