package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.isFailure
import io.ethers.providers.types.RpcParams
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient

@Serializable
private data class WsItem(val a: Long, val b: String? = null)

/**
 * Responses to a call with id `ID`, covering field order, nulls, missing, duplicate and escaped fields, `result`
 * against `error`, unknown keys, other message kinds and malformed input.
 */
private val WS_BODIES = listOf(
    """{"jsonrpc":"2.0","id":ID,"result":RESULT}""",
    """{"result":RESULT,"id":ID,"jsonrpc":"2.0"}""",
    """ { "jsonrpc" : "2.0" ,
         "id" : ID , "result" : RESULT } """,
    """{"jsonrpc":"2.0","id":ID,"result":null}""",
    """{"jsonrpc":"2.0","id":"ID","result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID.0,"result":RESULT}""",
    """{"jsonrpc":"2.0","id":null,"result":RESULT}""",
    """{"jsonrpc":"2.0","id":"abc","result":RESULT}""",
    """{"jsonrpc":"2.0","result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID,"error":{"code":-32000,"message":"boom","data":"0x12"}}""",
    """{"jsonrpc":"2.0","id":ID,"error":{"code":-32000,"message":"boom"},"result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID,"result":RESULT,"error":{"code":-32000,"message":"boom"}}""",
    """{"jsonrpc":"2.0","id":ID,"result":{"bad":true},"error":{"code":-32000,"message":"boom"}}""",
    """{"jsonrpc":"2.0","id":ID,"result":null,"error":{"code":-32000,"message":"boom"}}""",
    """{"jsonrpc":"2.0","id":ID,"error":null}""",
    """{"jsonrpc":"2.0","id":ID,"error":null,"result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID,"error":"text"}""",
    """{"jsonrpc":"2.0","id":ID,"error":"text","result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID}""",
    """{"jsonrpc":"2.0","id":ID,"extra":{"nested":[1,{"x":null}],"s":"}"},"result":RESULT,"more":[]}""",
    """{"jsonrpc":"2.0","id":ID,"result":{"bad":true},"result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID,"result":RESULT,"result":{"bad":true}}""",
    """{"jsonrpc":"2.0","id":999,"id":ID,"result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID,"id":999,"result":RESULT}""",
    "{\"jsonrpc\":\"2.0\",\"\\u0069d\":ID,\"result\":RESULT}",
    "{\"jsonrpc\":\"2.0\",\"id\":ID,\"\\u0072esult\":RESULT}",
    """{"jsonrpc":"2.0","id":ID,"result":{"bad":[1,2]}}""",
    """{"jsonrpc":"2.0","id":ID,"result":"text"}""",
    """{"jsonrpc":"2.0","id":ID,"result":12}""",
    """{"jsonrpc":"2.0","id":ID,"result":unquoted}""",
    """{"jsonrpc":"2.0","id":ID,"method":"m","result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID,"method":"m","params":{},"result":RESULT}""",
    """{"jsonrpc":"2.0","id":ID,"result":RESULT,"method":"m","params":{}}""",
    """{"jsonrpc":"2.0","id":ID,"result":RESULT,"method":null}""",
    "{\"jsonrpc\":\"2.0\",\"id\":ID,\"\\u006dethod\":\"m\",\"params\":{},\"result\":RESULT}",
    """{"jsonrpc":"2.0","id":ID,"result":RESULT""",
    """{"jsonrpc":"2.0","id":ID,"result":RESULT}}""",
    """[{"jsonrpc":"2.0","id":ID,"result":RESULT}]""",
    "not json",
    "null",
)

private class WsCase<T>(val name: String, val serializer: KSerializer<T>, val result: String)

private val WS_CASES = listOf(
    WsCase("string", String.serializer(), "\"a\\\"b\\u0041\""),
    WsCase("nullable string", String.serializer().nullable, "\"0x1\""),
    WsCase("long", Long.serializer(), "42"),
    WsCase("list", ListSerializer(String.serializer()), """["x","y"]"""),
    WsCase("object", WsItem.serializer(), """{"b":"q","a":5,"unknown":{"z":[1]}}"""),
    WsCase("nullable object", WsItem.serializer().nullable, """{"a":5}"""),
)

private const val TIMEOUT = "timeout"
private const val CALL_FAILED = "failed"

/** The parts of an outcome both routes must agree on; a decoding failure's message carries the decoder's wording. */
private fun <T> Result<T, RpcError>.wsOutcome(): Any? = if (isFailure()) {
    val error = error
    when (error.code) {
        RpcError.CODE_CALL_TIMEOUT -> TIMEOUT
        RpcError.CODE_CALL_FAILED -> CALL_FAILED
        else -> Triple(error.code, error.message, error.data)
    }
} else {
    value
}

/**
 * The outcome of [body] for the call with [id] the way WsClient handled every message before it decoded responses
 * directly: parse the message into a tree, route it on `method`, `params`, `id`, `error` and `result`, and decode the
 * result from the tree. A message that throws while being routed is skipped, so its call times out.
 */
private fun wsTreeOutcome(body: String, id: Long, decode: (JsonElement) -> Any?): Any? {
    val obj = try {
        Kotlinx.DEFAULT.parseToJsonElement(body)
    } catch (_: Exception) {
        return TIMEOUT
    }
    // a batch response matches no batch, and anything but an object cannot be routed
    if (obj !is JsonObject) return TIMEOUT

    val routing = try {
        val responseId = obj["id"]?.jsonPrimitive?.longOrNull ?: -1L
        val method = obj["method"]?.jsonPrimitive?.content
        val errorEl = obj["error"]
        val error = if (errorEl != null && errorEl !is JsonNull) RpcError.fromJsonObject(errorEl.jsonObject) else null
        Triple(responseId, method, error)
    } catch (_: Exception) {
        return TIMEOUT
    }
    val (responseId, method, error) = routing
    val result = obj["result"]

    return when {
        // a notification, for no subscription
        method != null && obj["params"] != null -> TIMEOUT
        responseId != id -> TIMEOUT
        error != null -> Triple(error.code, error.message, error.data)
        result != null -> try {
            decode(result)
        } catch (_: Exception) {
            CALL_FAILED
        }
        else -> Triple(RpcError.CODE_INVALID_RESPONSE, "Invalid response", JsonPrimitive(body))
    }
}

/** Records how each value was decoded: straight from the message, or from a tree. */
private class RecordingStrategy<T>(private val delegate: DeserializationStrategy<T>) : DeserializationStrategy<T> {
    val decoders = ArrayList<String>()
    override val descriptor get() = delegate.descriptor

    override fun deserialize(decoder: Decoder): T {
        decoders += decoder::class.simpleName.orEmpty()
        return delegate.deserialize(decoder)
    }
}

/** The code of the error that ends a call whose response the client skipped; see the first test. */
private const val SKIPPED_CODE = -1234

class WsDirectDecodeTest : FunSpec({
    test("decoding a response straight into the result matches decoding it through a JSON tree") {
        val server = mockServerWebsocket()
        val client = WsClient(server.url, KtorHttpClient { install(WebSockets) })
        try {
            // the client numbers its requests from 1
            var id = 0L
            for (case in WS_CASES) {
                val decodeTree = { element: JsonElement -> Kotlinx.DEFAULT.decodeFromJsonElement(case.serializer, element) }
                for (template in WS_BODIES) {
                    id++
                    val body = template.replace("RESULT", case.result).replace("ID", id.toString())
                    val expected = wsTreeOutcome(body, id, decodeTree)

                    // A message the client skips leaves its call unanswered. Rather than wait out a timeout for each,
                    // the body is followed by an error answering the same call, which ends it only if the body did not.
                    val actual = coroutineScope {
                        val response = async { client.request("m", RpcParams.EMPTY, case.serializer) }
                        server.takeReceivedText() shouldNotBe null
                        server.sendJson(body)
                        server.sendJson("""{"jsonrpc":"2.0","id":$id,"error":{"code":$SKIPPED_CODE,"message":"skipped"}}""")
                        withTimeout(10.seconds) { response.await() }.wsOutcome()
                    }
                    withClue("${case.name}: $body") {
                        (if (actual == Triple(SKIPPED_CODE, "skipped", null)) TIMEOUT else actual) shouldBe expected
                    }
                }
            }
        } finally {
            client.close()
            server.stop()
        }
    }

    test("a response to a call is decoded straight from the message, and again through a tree if that fails") {
        val server = mockServerWebsocket()
        val client = WsClient(server.url, KtorHttpClient { install(WebSockets) })
        try {
            val single = RecordingStrategy(String.serializer())
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1"}""")
            client.request("m", RpcParams.EMPTY, single).unwrap() shouldBe "0x1"
            single.decoders shouldBe listOf("StreamingJsonDecoder")

            // a result that does not decode straight from the message is decoded again through the tree
            val failing = RecordingStrategy(String.serializer())
            server.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":{"bad":true}}""")
            client.request("m", RpcParams.EMPTY, failing).isFailure() shouldBe true
            failing.decoders.size shouldBe 2
            failing.decoders.first() shouldBe "StreamingJsonDecoder"
            (failing.decoders.last() == "StreamingJsonDecoder") shouldBe false
        } finally {
            client.close()
            server.stop()
        }
    }

    test("the error wins over a result, as on the tree route") {
        val server = mockServerWebsocket()
        val client = WsClient(server.url, KtorHttpClient { install(WebSockets) })
        try {
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":null,"error":{"code":-32000,"message":"boom"}}""")
            val result = client.request("m", RpcParams.EMPTY, String.serializer().nullable)
            result.unwrapError().message shouldBe "boom"
        } finally {
            client.close()
            server.stop()
        }
    }
})
