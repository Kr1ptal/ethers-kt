package io.ethers.providers

import io.ethers.core.isFailure
import io.ethers.core.toJsonElement
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.types.StateOverride
import io.ethers.core.types.tracers.CallTracer
import io.ethers.core.types.tracers.FourByteTracer
import io.ethers.core.types.tracers.StructTracer
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import io.ktor.client.HttpClient as KtorHttpClient

private val FROM = Address("0x00000000000000000000000000000000000000aa")
private val TO = Address("0x00000000000000000000000000000000000000bb")
private val TX_HASH = Hash("0x1111111111111111111111111111111111111111111111111111111111111111")
private val BLOCK_HASH = Hash("0x2222222222222222222222222222222222222222222222222222222222222222")

private const val CALL_JSON = """{"from":"0x00000000000000000000000000000000000000aa","to":"0x00000000000000000000000000000000000000bb"}"""
private const val CALL_FRAME_JSON =
    """{"type":"CALL","from":"0x00000000000000000000000000000000000000aa","to":"0x00000000000000000000000000000000000000bb","gas":"0x1","gasUsed":"0x1","input":"0x","value":"0x0"}"""

private fun response(result: String) = """{"jsonrpc":"2.0","id":0,"result":$result}"""

private fun call() = CallRequest().apply {
    from = FROM
    to = TO
}

/**
 * Regression tests for RPC methods whose params are library types that `toJsonElement()` could not encode: the generic
 * [TracerConfig] (no class lookup possible, fixed by implementing `JsonSerializable`) and the internal
 * `CallManyBundle`/`CallManyContext` (ethers-providers did not apply the serialization plugin). Sending these failed
 * with "Cannot serialize JSON value of type ...", and caches that encode `RpcCall.params` failed the same way.
 */
class ProviderTraceRequestTest : FunSpec({
    lateinit var server: MockServer
    lateinit var client: HttpClient
    lateinit var provider: Provider

    beforeEach {
        server = mockServerHttp()
        client = HttpClient(server.url, KtorHttpClient())
        provider = Provider(client, 1L)
    }

    afterEach {
        client.close()
        server.stop()
    }

    suspend fun takeRequest(): Pair<String, JsonArray> {
        val body = Json.parseToJsonElement(server.takeRequest().bodyText).jsonObject
        return body["method"]!!.toString().trim('"') to body["params"]!!.jsonArray
    }

    fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    // anything that inspects RpcCall.params (e.g. request caches) must be able to encode every param
    fun RpcRequest<*, *>.shouldHaveJsonParams(expected: String) {
        val rpcCall = shouldBeInstanceOf<RpcCall<*>>()
        JsonArray(rpcCall.params.map { it.toJsonElement() }) shouldBe json(expected)
    }

    context("debug_traceCall") {
        test("named tracer config is sent as tracer + tracerConfig") {
            server.enqueueJson(response(CALL_FRAME_JSON))

            val config = TracerConfig(CallTracer(onlyTopCall = true, withLog = false))
            val frame = provider.traceCall(call(), BlockId.LATEST, config).send().unwrap()

            val (method, params) = takeRequest()
            method shouldBe "debug_traceCall"
            params shouldBe json("""[$CALL_JSON,"latest",{"tracer":"callTracer","tracerConfig":{"onlyTopCall":true,"withLog":false}}]""")
            frame.to shouldBe TO
        }

        test("all TracerConfig options are sent") {
            server.enqueueJson(response("{}"))

            val config = TracerConfig(
                tracer = FourByteTracer,
                timeoutMs = 1500,
                reexec = 64,
                stateOverrides = StateOverride().apply { put(TO, AccountOverride().nonce(5)) },
                blockOverrides = BlockOverride().apply { number = 10 },
                txIndex = 3,
            )
            provider.traceCall(call(), BlockId.Number(16), config).send().unwrap()

            takeRequest().second shouldBe json(
                """
                [$CALL_JSON,"0x10",{
                  "tracer":"4byteTracer","tracerConfig":{},"timeout":"1500ms","reexec":64,
                  "stateOverrides":{"0x00000000000000000000000000000000000000bb":{"nonce":"0x5"}},
                  "blockOverrides":{"number":"0xa"},"txIndex":"0x3"
                }]
                """,
            )
        }

        test("unnamed struct tracer config is inlined into the config object") {
            server.enqueueJson(response("""{"gas":1,"failed":false,"returnValue":"","structLogs":[]}"""))

            val config = TracerConfig(StructTracer(enableMemory = true, limit = 5))
            provider.traceCall(call(), BlockId.Hash(BLOCK_HASH), config).send().unwrap()

            takeRequest().second shouldBe json(
                """[$CALL_JSON,"$BLOCK_HASH",{"enableMemory":true,"limit":5}]""",
            )
        }

        test("request params can be encoded as JSON by toJsonElement") {
            provider.traceCall(call(), BlockId.LATEST, TracerConfig(CallTracer(onlyTopCall = false, withLog = true)))
                .shouldHaveJsonParams("""[$CALL_JSON,"latest",{"tracer":"callTracer","tracerConfig":{"onlyTopCall":false,"withLog":true}}]""")
        }
    }

    context("debug_traceTransaction") {
        test("tracer config is sent") {
            server.enqueueJson(response(CALL_FRAME_JSON))

            val config = TracerConfig(CallTracer(onlyTopCall = false, withLog = true), timeoutMs = 10)
            provider.traceTransaction(TX_HASH, config).send().unwrap()

            val (method, params) = takeRequest()
            method shouldBe "debug_traceTransaction"
            params shouldBe json("""["$TX_HASH",{"tracer":"callTracer","tracerConfig":{"onlyTopCall":false,"withLog":true},"timeout":"10ms"}]""")
        }

        test("request params can be encoded as JSON by toJsonElement") {
            provider.traceTransaction(TX_HASH, TracerConfig(FourByteTracer))
                .shouldHaveJsonParams("""["$TX_HASH",{"tracer":"4byteTracer","tracerConfig":{}}]""")
        }
    }

    context("debug_traceBlockBy*") {
        test("traceBlockByNumber sends tracer config") {
            server.enqueueJson(response("""[{"txHash":"$TX_HASH","result":{}}]"""))

            provider.traceBlock(BlockId.Number(7), TracerConfig(FourByteTracer)).send().unwrap()

            val (method, params) = takeRequest()
            method shouldBe "debug_traceBlockByNumber"
            params shouldBe json("""["0x7",{"tracer":"4byteTracer","tracerConfig":{}}]""")
        }

        test("traceBlockByHash sends tracer config") {
            server.enqueueJson(response("""[{"txHash":"$TX_HASH","result":{}}]"""))

            provider.traceBlock(BLOCK_HASH, TracerConfig(FourByteTracer)).send().unwrap()

            val (method, params) = takeRequest()
            method shouldBe "debug_traceBlockByHash"
            params shouldBe json("""["$BLOCK_HASH",{"tracer":"4byteTracer","tracerConfig":{}}]""")
        }

        test("request params can be encoded as JSON by toJsonElement") {
            provider.traceBlock(BlockId.LATEST, TracerConfig(FourByteTracer))
                .shouldHaveJsonParams("""["latest",{"tracer":"4byteTracer","tracerConfig":{}}]""")
        }
    }

    context("debug_traceCallMany") {
        test("bundle, context and tracer config are sent") {
            server.enqueueJson(response("[[$CALL_FRAME_JSON,$CALL_FRAME_JSON]]"))

            val config = TracerConfig(CallTracer(onlyTopCall = true, withLog = true), blockOverrides = BlockOverride().apply { time = 100 })
            val traces = provider.traceCallMany(BlockId.Number(5), listOf(call(), call()), config, transactionIndex = 2)
                .send().unwrap()

            val (method, params) = takeRequest()
            method shouldBe "debug_traceCallMany"
            params shouldBe json(
                """
                [
                  [{"transactions":[$CALL_JSON,$CALL_JSON],"blockOverride":{"time":"0x64"}}],
                  {"blockNumber":"0x5","transactionIndex":2},
                  {"tracer":"callTracer","tracerConfig":{"onlyTopCall":true,"withLog":true},"blockOverrides":{"time":"0x64"}}
                ]
                """,
            )
            traces.size shouldBe 2
        }

        test("request params can be encoded as JSON by toJsonElement") {
            provider.traceCallMany(BlockId.LATEST, listOf(call()), TracerConfig(FourByteTracer)).shouldHaveJsonParams(
                """[[{"transactions":[$CALL_JSON]}],{"blockNumber":"latest","transactionIndex":-1},{"tracer":"4byteTracer","tracerConfig":{}}]""",
            )
        }
    }

    context("eth_callMany") {
        test("bundle and context are sent without overrides") {
            server.enqueueJson(response("""[{"value":"0x01"}]"""))

            val results = provider.callMany(BlockId.Number(9), listOf(call())).send().unwrap()

            val (method, params) = takeRequest()
            method shouldBe "eth_callMany"
            params shouldBe json("""[{"transactions":[$CALL_JSON]},{"blockNumber":"0x9","transactionIndex":-1},null]""")
            results.size shouldBe 1
        }

        test("bundle, context and overrides are sent") {
            server.enqueueJson(response("""[{"value":"0x01"},{"error":"reverted"}]"""))

            val results = provider.callMany(
                BLOCK_HASH,
                listOf(call(), call()),
                transactionIndex = 4,
                stateOverride = StateOverride().apply { put(TO, AccountOverride().nonce(1)) },
                blockOverride = BlockOverride().apply { number = 20 },
            ).send().unwrap()

            takeRequest().second shouldBe json(
                """
                [
                  {"transactions":[$CALL_JSON,$CALL_JSON],"blockOverride":{"number":"0x14"}},
                  {"blockNumber":"$BLOCK_HASH","transactionIndex":4},
                  {"0x00000000000000000000000000000000000000bb":{"nonce":"0x1"}}
                ]
                """,
            )
            withClue("second call reverted") { results[1].isFailure() shouldBe true }
        }

        test("request params can be encoded as JSON by toJsonElement") {
            provider.callMany(BlockId.LATEST, listOf(call()))
                .shouldHaveJsonParams("""[{"transactions":[$CALL_JSON]},{"blockNumber":"latest","transactionIndex":-1},null]""")
        }
    }
})
