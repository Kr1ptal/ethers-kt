package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.core.isFailure
import io.ethers.providers.HttpClient
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SolanaSimulationConfig
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionException
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.HttpClient as KtorHttpClient

class SolanaSimulationTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })

    lateinit var provider: SolanaProvider
    lateinit var ktor: KtorHttpClient
    val responses = mutableMapOf<String, String>()
    val requests = mutableListOf<JsonObject>()

    beforeEach {
        requests.clear()
        responses.clear()
        ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject
                requests.add(body)
                val method = body.getValue("method").jsonPrimitive.content
                val result = responses[method] ?: "null"
                respond(
                    """{"jsonrpc":"2.0","id":${body.getValue("id")},"result":$result}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            },
        )
        provider = SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }
    afterEach {
        provider.close()
        ktor.close()
    }

    fun contextual(value: String) = """{"context":{"slot":1,"apiVersion":"3.0.0"},"value":$value}"""
    fun simulation(units: Long? = 1000, err: String = "null") = contextual("""{"err":$err,"logs":["Program log: hi"],"unitsConsumed":${units ?: 0},"returnData":null}""")

    fun request() = SolanaTransactionRequest {
        feePayer(alice.publicKey)
        instruction(SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L))
    }

    fun paramsOf(method: String) = requests.last { it.getValue("method").jsonPrimitive.content == method }
        .getValue("params").let { Kotlinx.DEFAULT.parseToJsonElement(it.toString()) }

    test("compiled and byte simulations preserve the same complete config") {
        responses["simulateTransaction"] = simulation()
        val tx = request().apply { blockhash(blockhash) }.compileV0().unwrap()
        val config = SolanaSimulationConfig(
            commitment = Commitment.FINALIZED,
            replaceRecentBlockhash = true,
            innerInstructions = true,
            accounts = listOf(alice.publicKey),
            minContextSlot = bigIntegerOf(7),
        )
        provider.simulateTransaction(tx, config).send().unwrap()
        val compiledParams = paramsOf("simulateTransaction")
        provider.simulateTransaction(tx.serializeForSimulation(), config).send().unwrap()
        paramsOf("simulateTransaction") shouldBe compiledParams
        val sent = compiledParams.toString()
        ("\"commitment\":\"finalized\"" in sent) shouldBe true
        ("\"replaceRecentBlockhash\":true" in sent) shouldBe true
        ("\"innerInstructions\":true" in sent) shouldBe true
        ("\"minContextSlot\":7" in sent) shouldBe true
        ("\"addresses\":[\"${alice.publicKey}\"]" in sent) shouldBe true

        provider.simulateTransaction(tx, Commitment.PROCESSED).send().unwrap()
        val shorthandParams = paramsOf("simulateTransaction")
        provider.simulateTransaction(tx, SolanaSimulationConfig(commitment = Commitment.PROCESSED)).send().unwrap()
        paramsOf("simulateTransaction") shouldBe shorthandParams
        provider.simulateTransaction(tx.serializeForSimulation(), Commitment.PROCESSED).send().unwrap()
        paramsOf("simulateTransaction") shouldBe shorthandParams

        provider.simulateTransaction(tx, SolanaSimulationConfig()).send().unwrap()
        ("\"commitment\":\"confirmed\"" in paramsOf("simulateTransaction").toString()) shouldBe true
    }

    test("simulation options reach the node and mutually exclusive ones are rejected") {
        responses["simulateTransaction"] = simulation()
        val tx = request().apply { blockhash(blockhash) }.compileV0().unwrap()
        val options = SolanaSimulationConfig(
            innerInstructions = true,
            accounts = listOf(alice.publicKey, Programs.SYSTEM),
            minContextSlot = bigIntegerOf(7),
        )
        provider.simulateTransaction(tx, options).send().unwrap().value.unitsConsumed shouldBe bigIntegerOf(1000)

        val sent = paramsOf("simulateTransaction").toString()
        (""""innerInstructions":true""" in sent) shouldBe true
        (""""minContextSlot":7""" in sent) shouldBe true
        (""""addresses":["${alice.publicKey}","${Programs.SYSTEM}"]""" in sent) shouldBe true
        // not requested, so not sent
        ("sigVerify" in sent) shouldBe false

        shouldThrow<IllegalArgumentException> { SolanaSimulationConfig(sigVerify = true, replaceRecentBlockhash = true) }
    }

    test("simulating a request needs no signatures or live blockhash") {
        responses["simulateTransaction"] = simulation()
        val simulated = provider.simulateTransaction(request()).send().unwrap().value
        simulated.logs shouldBe listOf("Program log: hi")

        val sent = paramsOf("simulateTransaction").toString()
        (""""replaceRecentBlockhash":true""" in sent) shouldBe true
        ("sigVerify" in sent) shouldBe false
    }

    test("a request that cannot compile fails without reaching the node") {
        val incomplete = SolanaTransactionRequest { instruction(SystemProgram.transfer(alice.publicKey, bob.publicKey, 1L)) }
        val error = provider.simulateTransaction(incomplete).send().unwrapError()
        requests.isEmpty() shouldBe true
        val cause = error.cause
        (cause is SolanaTransactionException) shouldBe true
        (cause as SolanaTransactionException).error shouldBe SolanaTransactionError.MissingFeePayer
    }

    test("fillTransaction fails when the simulation it needs for the limit fails") {
        responses["getLatestBlockhash"] = contextual("""{"blockhash":"$blockhash","lastValidBlockHeight":99}""")
        responses["getRecentPrioritizationFees"] = """[{"slot":1,"prioritizationFee":5}]"""
        responses["simulateTransaction"] = simulation(units = 10, err = """"AccountNotFound"""")
        provider.fillTransaction(request(), SolanaTxType.V0).send().isFailure() shouldBe true
    }

    test("fillTransaction supplies blockhash, price and limit, and compiles the requested version") {
        responses["getLatestBlockhash"] = contextual("""{"blockhash":"$blockhash","lastValidBlockHeight":99}""")
        responses["getRecentPrioritizationFees"] = """[{"slot":1,"prioritizationFee":1},{"slot":2,"prioritizationFee":5},{"slot":3,"prioritizationFee":100}]"""
        responses["simulateTransaction"] = simulation(units = 1000)

        val v1 = provider.fillTransaction(request(), SolanaTxType.V1).send().unwrap() as SolanaTxV1
        v1.recentBlockhash shouldBe blockhash
        // 1000 units plus the default ten percent margin
        v1.config.computeUnitLimit shouldBe 1100
        // median of the three samples, not the mean, so the outlier does not dominate
        // 1100 units at 5 micro-lamports rounds up to a single lamport
        v1.config.priorityFee shouldBe bigIntegerOf(1)

        // the fee locality is the accounts the transaction writes
        paramsOf("getRecentPrioritizationFees").toString() shouldBe """[["${alice.publicKey}","${bob.publicKey}"]]"""

        val v0 = provider.fillTransaction(request(), SolanaTxType.V0).send().unwrap() as SolanaTxV0
        v0.instructions.count { v0.accounts[it.programIdIndex] == Programs.COMPUTE_BUDGET } shouldBe 2
    }

    test("fillTransaction picks the version when none is given") {
        responses["getLatestBlockhash"] = contextual("""{"blockhash":"$blockhash","lastValidBlockHeight":99}""")
        responses["getRecentPrioritizationFees"] = """[{"slot":1,"prioritizationFee":5}]"""
        responses["simulateTransaction"] = simulation(units = 1000)

        // no lookup tables can help, so legacy is the smaller encoding
        val tx = provider.fillTransaction(request()).send().unwrap()
        tx.type shouldBe SolanaTxType.Legacy
        tx.recentBlockhash shouldBe blockhash
    }

    test("a priority fee already set means no fee samples are fetched") {
        responses["getLatestBlockhash"] = contextual("""{"blockhash":"$blockhash","lastValidBlockHeight":99}""")
        responses["simulateTransaction"] = simulation(units = 1000)

        val tx = provider.fillTransaction(request().apply { priorityFee(500) }, SolanaTxType.V1).send().unwrap() as SolanaTxV1
        tx.config.priorityFee shouldBe bigIntegerOf(500)
        requests.none { it.getValue("method").jsonPrimitive.content == "getRecentPrioritizationFees" } shouldBe true
    }

    test("fillTransaction honours values the caller already set") {
        responses["simulateTransaction"] = simulation(units = 1000)
        val prefilled = request().apply {
            blockhash(blockhash)
            computeUnitLimit(50_000)
            computeUnitPrice(7)
        }
        val tx = provider.fillTransaction(prefilled, SolanaTxType.V1).send().unwrap() as SolanaTxV1
        tx.config.computeUnitLimit shouldBe 50_000
        // nothing needed filling, so nothing was asked of the node
        requests.isEmpty() shouldBe true
        // and the caller's request is untouched
        prefilled.computeUnitLimit shouldBe 50_000
    }
})
