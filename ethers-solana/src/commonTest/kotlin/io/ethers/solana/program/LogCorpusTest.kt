package io.ethers.solana.program

import io.ethers.core.Kotlinx
import io.ethers.solana.corpus.logCorpus
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.TransactionError
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64

/** One corpus transaction: its logs, and what the logs are checked against. */
private class LogSample(val signature: String, val sample: String, val err: JsonObject?, val topLevelPrograms: List<String>, val logs: List<String>)

private val SAMPLES: List<LogSample> by lazy {
    logCorpus().map { line ->
        val json = Kotlinx.DEFAULT.parseToJsonElement(line).jsonObject
        LogSample(
            signature = json.getValue("signature").jsonPrimitive.content,
            sample = json.getValue("sample").jsonPrimitive.content,
            err = json["err"]?.takeUnless { it is JsonNull } as? JsonObject,
            topLevelPrograms = json.getValue("topLevelPrograms").jsonArray.map { it.jsonPrimitive.content },
            logs = json.getValue("logMessages").jsonArray.map { it.jsonPrimitive.content },
        )
    }
}

/** A custom program error's top-level instruction index and code, or null for any other error. */
private fun LogSample.customError(): Pair<Int, Long>? {
    val failure = err?.get("InstructionError") as? JsonArray ?: return null
    val code = (failure[1] as? JsonObject)?.get("Custom") as? JsonPrimitive ?: return null
    return failure[0].jsonPrimitive.int to code.long
}

/** Precompiles are verified before execution and log no invocation. */
private val PRECOMPILES = setOf(
    "Ed25519SigVerify111111111111111111111111111",
    "KeccakSecp256k11111111111111111111111111111",
    "Secp256r1SigVerify1111111111111111111111111",
)

/** The straightforward way to follow the logs: split every line into words. */
private class ReferenceLogs(logs: List<String>) {
    val data = ArrayList<List<Any>>()
    val topLevel = ArrayList<String>()
    var failure: List<Any?>? = null
    var truncated = false

    init {
        val stack = ArrayList<String>()
        logs.forEachIndexed { logIndex, line ->
            if (line == "Log truncated") truncated = true
            val words = line.split(" ")
            if (words[0] != "Program" || words.size < 3) return@forEachIndexed
            when {
                line.startsWith("Program data: ") -> if (stack.isNotEmpty()) {
                    val bytes = words.drop(2).filter(String::isNotEmpty).fold(ByteArray(0)) { acc, field -> acc + Base64.decode(field) }
                    data.add(listOf(stack.last(), bytes.toList(), topLevel.size - 1, logIndex, stack.size))
                }
                // "Program log:", "Program return:" and similar name no program
                words[1].endsWith(":") -> Unit
                words[2] == "invoke" -> {
                    val depth = words[3].removePrefix("[").removeSuffix("]").toInt()
                    if (depth == 1) topLevel.add(words[1])
                    while (stack.size >= depth) stack.removeAt(stack.lastIndex)
                    stack.add(words[1])
                }
                words.size == 3 && words[2] == "success" -> stack.removeLastOrNull()
                words[2] == "failed:" -> {
                    if (failure == null) {
                        val code = line.substringAfter("custom program error: 0x", "").takeIf(String::isNotEmpty)?.toLong(16)
                        failure = listOf(words[1], code, topLevel.size - 1, stack.size)
                    }
                    stack.removeLastOrNull()
                }
            }
        }
    }
}

class LogCorpusTest : FunSpec({
    test("the corpus covers a wide variety of real logs") {
        SAMPLES.size shouldBe SAMPLES.distinctBy { it.signature }.size
        (SAMPLES.size >= 900) shouldBe true
        (SAMPLES.map { it.sample }.distinct().size >= 30) shouldBe true
        (SAMPLES.count { it.err != null } >= 100) shouldBe true
        (SAMPLES.count { it.customError() != null } >= 50) shouldBe true
        // no truncated logs turned up on mainnet; ProgramLogsTest covers truncation
        (SAMPLES.sumOf { s -> s.logs.count { it.startsWith("Program data: ") } } >= 100) shouldBe true
        (SAMPLES.count { s -> s.logs.any { it.startsWith("Program return: ") } } >= 10) shouldBe true
        // CPI chains several programs deep
        (SAMPLES.maxOf { it.logs.maxInvocationDepth() } >= 4) shouldBe true
        (SAMPLES.sumOf { it.logs.size } >= 10_000) shouldBe true
    }

    test("the parser agrees with a line-splitting reference on every transaction") {
        for (sample in SAMPLES) {
            val parsed = ProgramLogs.parse(sample.logs)
            val reference = ReferenceLogs(sample.logs)
            parsed.data.map { listOf(it.programId.toBase58(), it.data.toByteArray().toList(), it.instructionIndex, it.logIndex, it.stackHeight) } shouldBe
                reference.data
            parsed.failure?.let { listOf(it.programId.toBase58(), it.code, it.instructionIndex, it.stackHeight) } shouldBe reference.failure
            parsed.truncated shouldBe reference.truncated
        }
    }

    test("the invocations the logs report match each transaction's top-level instructions") {
        for (sample in SAMPLES) {
            if ("Log truncated" in sample.logs) continue
            val invoked = ReferenceLogs(sample.logs).topLevel
            val expected = sample.topLevelPrograms.filter { it !in PRECOMPILES }
            // execution stops at the instruction that failed
            invoked shouldBe expected.take(invoked.size)
            if (sample.err == null) invoked.size shouldBe expected.size
        }
    }

    test("every custom error is attributed to its instruction and to a program that instruction invoked") {
        for (sample in SAMPLES) {
            val (index, code) = sample.customError() ?: continue
            // only the logs attribute the error, as they do for a logs subscription
            val err = Kotlinx.DEFAULT.decodeFromJsonElement(TransactionError.serializer(), sample.err!!)
            val failure = ContextValue(RpcContext(bigIntegerOf(1)), LogsNotification(SolanaSignature(ByteArray(64)), err, sample.logs)).programFailure()!!
            failure.instructionIndex shouldBe index
            failure.code shouldBe code
            if ("Log truncated" !in sample.logs) {
                failure.programId!!.toBase58() shouldBeIn sample.logs.invokedPrograms()
                failure.stackHeight!! shouldBe ReferenceLogs(sample.logs).failure!![3]
            }
        }
    }

    test("log notifications yield one event record per data line, each from the program that logged it") {
        for (sample in SAMPLES) {
            val notification = ContextValue(RpcContext(bigIntegerOf(1)), LogsNotification(SolanaSignature(ByteArray(64)), null, sample.logs))
            val records = notification.eventRecords()
            records.map { it.programId.toBase58() } shouldBe ReferenceLogs(sample.logs).data.map { it[0] }
        }
    }
})

/** Every program the logs report invoking, at any depth. */
private fun List<String>.invokedPrograms(): Set<String> = mapNotNullTo(HashSet()) { line ->
    val words = line.split(" ")
    if (words.size >= 4 && words[0] == "Program" && words[2] == "invoke") words[1] else null
}

private fun List<String>.maxInvocationDepth(): Int = maxOfOrNull { line ->
    val words = line.split(" ")
    if (words.size >= 4 && words[0] == "Program" && words[2] == "invoke") words[3].removePrefix("[").removeSuffix("]").toInt() else 0
} ?: 0
