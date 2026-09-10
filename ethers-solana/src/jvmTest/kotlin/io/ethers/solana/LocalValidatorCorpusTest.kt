package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Explicit opt-in capture; never reads wallet keys or sends to a public cluster. */
class LocalValidatorCorpusTest : FunSpec({
    val endpoint = System.getenv("SOLANA_VALIDATOR_HTTP")
    val output = System.getenv("SOLANA_VALIDATOR_CORPUS_OUTPUT")
    test("capture 200 finalized local-validator v1 transactions").config(enabled = endpoint != null && output != null, timeout = 600.seconds) {
        val uri = URI(endpoint!!)
        require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.userInfo == null)
        val directory = Path.of(output!!)
        require(!Files.exists(directory)) { "Capture output must be a new directory" }
        Files.createDirectories(directory)
        fun rpc(method: String, params: JsonArray = JsonArray(emptyList())): JsonElement {
            require(method in setOf("getVersion", "getGenesisHash", "getAccountInfo", "getTransaction", "getBlock"))
            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 30000
                connection.setRequestProperty("Content-Type", "application/json")
                val body = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", method)
                    put("params", params)
                }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val response = connection.inputStream.bufferedReader().use { Kotlinx.DEFAULT.parseToJsonElement(it.readText()).jsonObject }
                require("error" !in response) { response.toString() }
                return response.getValue("result")
            } finally {
                connection.disconnect()
            }
        }
        val feature = rpc("getAccountInfo", JsonArray(listOf(JsonPrimitive("txv1aq4pp281K9um3tnPgkfX8UqtFT6wcVW3hNezGLL"), buildJsonObject { put("encoding", "base64") })))
        require(feature.jsonObject.getValue("value") != JsonNull) { "V1 feature is missing" }
        val manifest = buildJsonObject {
            put("cluster", "localnet")
            put("origin", "generated-local-validator")
            put("endpoint", endpoint)
            put("capturedAt", Instant.now().toString())
            put("genesisHash", rpc("getGenesisHash"))
            put("nodeVersion", rpc("getVersion"))
            put("v1Feature", feature)
            put("commitment", "finalized")
            put("count", 200)
        }
        val provider = SolanaProvider.builder(endpoint).defaultCommitment(Commitment.CONFIRMED).build().unwrap()
        try {
            val signers = List(12) { KeypairSigner.generate() }
            val recipients = List(47) { KeypairSigner.generate().publicKey }
            for (signer in signers) provider.requestAirdrop(signer.publicKey, 10000000000L).send().unwrap()
            eventually(30.seconds) {
                for (signer in signers) provider.getBalance(signer.publicKey).send().unwrap().value shouldBe bigIntegerOf(10000000000L)
            }
            val submitted = mutableListOf<SolanaTransactionSigned>()
            repeat(200) { index ->
                val active = signers.take(1 + index % 12)
                val count = maxOf(active.size, listOf(1, 2, 8, 16, 32, 64)[index % 6])
                val instructions = List(count) { n -> SystemProgram.transfer(active[n % active.size].publicKey, recipients[n % recipients.size], 1000000L + index) }
                val config = SolanaTransactionConfig(
                    priorityFee = if (index % 3 == 0) null else bigIntegerOf(if (index % 3 == 1) 0 else 5000 + index),
                    computeUnitLimit = 20000L + index * 100,
                    loadedAccountsDataSizeLimit = if (index % 2 == 0) 65536 else 131072,
                    heapSize = listOf(null, 32768L, 65536L, 262144L)[(index / 3) % 4],
                )
                val latest = provider.getLatestBlockhash().send().unwrap().value
                val tx = SolanaTxV1.compile(active.first().publicKey, latest.blockhash, instructions, config).unwrap().sign(*active.toTypedArray())
                provider.simulateTransaction(tx).send().unwrap().value.isSuccess shouldBe true
                provider.sendTransaction(tx).send().unwrap() shouldBe tx.id
                submitted += tx
                if ((index + 1) % 20 == 0) println("Submitted ${index + 1}/200 local v1 transactions")
            }
            Files.newBufferedWriter(directory.resolve("localnet-1.jsonl")).use { writer ->
                for (tx in submitted) {
                    fun fetch(encoding: String) = rpc(
                        "getTransaction",
                        JsonArray(
                            listOf(
                                JsonPrimitive(tx.id.toString()),
                                buildJsonObject {
                                    put("encoding", encoding)
                                    put("commitment", "finalized")
                                    put("maxSupportedTransactionVersion", 1)
                                },
                            ),
                        ),
                    )
                    var raw: JsonElement = JsonNull
                    eventually(60.seconds) {
                        raw = fetch("json")
                        (raw != JsonNull) shouldBe true
                    }
                    val binary = fetch("base64").jsonObject
                    val json = raw.jsonObject
                    json.getValue("version") shouldBe JsonPrimitive(1)
                    binary.getValue("slot") shouldBe json.getValue("slot")
                    binary.getValue("meta") shouldBe json.getValue("meta")
                    val wire = binary.getValue("transaction").jsonArray[0].jsonPrimitive.content
                    wire shouldBe tx.toBase64()
                    json.getValue("meta").jsonObject.getValue("err") shouldBe JsonNull
                    val block = rpc(
                        "getBlock",
                        JsonArray(
                            listOf(
                                json.getValue("slot"),
                                buildJsonObject {
                                    put("transactionDetails", "none")
                                    put("rewards", false)
                                    put("commitment", "finalized")
                                    put("maxSupportedTransactionVersion", 1)
                                },
                            ),
                        ),
                    ).jsonObject
                    val record = buildJsonObject {
                        put("cluster", "localnet")
                        put("origin", "generated-local-validator")
                        put("slot", json.getValue("slot"))
                        put("blockhash", block.getValue("blockhash"))
                        put("signature", tx.id.toString())
                        put("wire", wire)
                        put("rpc", raw)
                    }
                    writer.appendLine(record.toString())
                }
            }
            Files.writeString(directory.resolve("localnet-manifest.json"), manifest.toString() + "\n")
        } finally {
            provider.close()
        }
    }
})
