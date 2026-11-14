package io.ethers.solana.program

import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.success
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcClientConfig
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.solana.codec.BorshReader
import io.ethers.solana.codec.BorshWriter
import io.ethers.solana.codec.SolanaCodec
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaDiscriminator
import io.ethers.solana.codec.SolanaEvent
import io.ethers.solana.codec.SolanaEventContext
import io.ethers.solana.codec.SolanaEventFactory
import io.ethers.solana.codec.SolanaEventSource
import io.ethers.solana.codec.SolanaExecutionStatus
import io.ethers.solana.codec.SolanaInstructionDescriptor
import io.ethers.solana.codec.SolanaProgramError
import io.ethers.solana.codec.SolanaProgramErrorDecoder
import io.ethers.solana.corpus.transactionCorpus
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.providers.SolanaSubscriptionDescriptor
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.ReturnData
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.SolanaRPCMessage
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.SolanaRPCTransactionData
import io.ethers.solana.types.rpc.SolanaRPCTransactionMeta
import io.ethers.solana.types.rpc.TransactionSimulation
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

private val PAYER = SolanaAddress(ByteArray(32) { 1 })
private val PROGRAM = SolanaAddress(ByteArray(32) { 3 })
private val OTHER = SolanaAddress(ByteArray(32) { 4 })
private val AUTHORITY = SolanaAddress.findProgramAddress(listOf("__event_authority".encodeToByteArray()), PROGRAM).address
private val SIGNATURE = SolanaSignature(ByteArray(64) { 7 })
private val EVENT_TAG = SolanaBytes.fromHex("e445a52e51cb9a1d")

private data class Pinged(val n: Int, override val context: SolanaEventContext) : SolanaEvent {
    companion object : SolanaEventFactory<Pinged> {
        override val name = "Pinged"
        override val discriminator = SolanaDiscriminator.of(1, 2, 3, 4, 5, 6, 7, 8)

        override fun read(reader: BorshReader, context: SolanaEventContext) = Pinged(reader.readU8(), context)

        override fun write(writer: BorshWriter, event: Pinged) = writer.writeU8(event.n)
    }
}

private fun pinged(n: Int) = Pinged.encode(Pinged(n, SolanaEventContext(PROGRAM)))

private fun logData(bytes: ByteArray) = "Program data: ${Base64.encode(bytes)}"

/**
 * One top-level instruction to [PROGRAM] that logs event 1, then emits event 2 through a self-CPI with its
 * event authority, and a forged self-CPI whose first account is not the authority.
 */
private fun transaction(err: String = "null", inner: Boolean = true, logs: List<String> = standardLogs()): SolanaRPCTransaction {
    val cpi = (EVENT_TAG.toByteArray() + pinged(2))
    val innerJson = if (!inner) {
        ""
    } else {
        """
        "innerInstructions": [{"index": 0, "instructions": [
          {"programIdIndex": 2, "accounts": [1], "data": "${SolanaBytes.fromBytes(cpi).toBase58()}", "stackHeight": 2},
          {"programIdIndex": 2, "accounts": [0], "data": "${SolanaBytes.fromBytes(EVENT_TAG.toByteArray() + pinged(3)).toBase58()}", "stackHeight": 2}
        ]}],
        """
    }
    return Kotlinx.DEFAULT.decodeFromString(
        """
        {
          "slot": 77,
          "blockTime": null,
          "transaction": {
            "signatures": ["$SIGNATURE"],
            "message": {
              "header": {"numRequiredSignatures": 1, "numReadonlySignedAccounts": 0, "numReadonlyUnsignedAccounts": 2},
              "accountKeys": ["$PAYER", "$AUTHORITY", "$PROGRAM"],
              "recentBlockhash": "${SolanaBlockhash(ByteArray(32) { 5 })}",
              "instructions": [{"programIdIndex": 2, "accounts": [0, 1], "data": ""}]
            }
          },
          "meta": {
            "err": $err, "fee": 5000, "preBalances": [], "postBalances": [],
            $innerJson
            "logMessages": [${logs.joinToString { "\"$it\"" }}]
          },
          "version": "legacy"
        }
        """,
    )
}

private fun standardLogs() = listOf(
    "Program $PROGRAM invoke [1]",
    logData(pinged(1)),
    "Program $PROGRAM invoke [2]",
    "Program $PROGRAM success",
    "Program $PROGRAM success",
)

private enum class ProgramErrors(override val code: Long, override val message: String?) : SolanaProgramError {
    Overflow(6000, "Overflow"),
}

class SolanaEventsTest : FunSpec({
    context("event records") {
        test("log and self-CPI events are extracted with their source and execution") {
            val records = transaction().eventRecords().unwrap()
            records.map { it.context.source } shouldBe listOf(SolanaEventSource.Log(0, 1, 1), SolanaEventSource.Cpi(0, 0, 2))
            records.map { it.programId } shouldBe listOf(PROGRAM, PROGRAM)
            records.map { it.data } shouldBe listOf(SolanaBytes.fromBytes(pinged(1)), SolanaBytes.fromBytes(pinged(2)))

            val execution = records.first().context.execution
            execution.signature shouldBe SIGNATURE
            execution.slot shouldBe 77L
            execution.status shouldBe SolanaExecutionStatus.SUCCEEDED
            execution.incomplete shouldBe false
        }

        test("records decode directly into typed events carrying their context") {
            val events = transaction().eventRecords().unwrap().decodeEvents(PROGRAM, Pinged)
            events.map { it.n } shouldBe listOf(1, 2)
            events[1].context.source shouldBe SolanaEventSource.Cpi(0, 0, 2)
            transaction().eventRecords().unwrap().decodeEvents(OTHER, Pinged) shouldBe emptyList()
        }

        test("events of a failed transaction are marked failed; missing inner instructions mark them incomplete") {
            transaction(err = """{"InstructionError":[0,{"Custom":6000}]}""").eventRecords().unwrap()
                .first().context.execution.status shouldBe SolanaExecutionStatus.FAILED

            val logOnly = transaction(inner = false).eventRecords().unwrap()
            logOnly.map { it.context.source } shouldBe listOf(SolanaEventSource.Log(0, 1, 1))
            logOnly.single().context.execution.incomplete shouldBe true
        }

        test("a simulation's events have its slot but no signature") {
            val simulation = Kotlinx.DEFAULT.decodeFromString<ContextValue<TransactionSimulation>>(
                """{"context":{"slot":5},"value":{"err":null,"logs":[${standardLogs().joinToString { "\"$it\"" }}]}}""",
            )
            val record = simulation.eventRecords(transaction()).unwrap().single()
            record.context.execution.signature.shouldBeNull()
            record.context.execution.slot shouldBe 5L
            // no inner instructions were requested
            record.context.execution.incomplete shouldBe true
        }

        test("a logs notification carries log events only") {
            val notification = ContextValue(RpcContext(bigIntegerOf(9)), LogsNotification(SIGNATURE, null, standardLogs()))
            notification.eventRecords().map { it.context.source } shouldBe listOf(SolanaEventSource.Log(0, 1, 1))
        }
    }

    test("a transaction built from a stream's fields extracts exactly what the fetched response does") {
        for (fetched in listOf(transaction(), transaction(err = """{"InstructionError":[0,{"Custom":6000}]}"""))) {
            val message = fetched.transaction.message
            val meta = fetched.meta!!
            // what a Geyser/Yellowstone adapter does: map the update's fields onto the same model
            val streamed = SolanaRPCTransaction(
                slot = bigIntegerOf(77),
                blockTime = null,
                transaction = SolanaRPCTransactionData(
                    signatures = listOf(SIGNATURE),
                    message = SolanaRPCMessage(message.header, message.accountKeys, message.recentBlockhash, message.instructions),
                ),
                meta = SolanaRPCTransactionMeta(
                    err = meta.err,
                    fee = meta.fee,
                    preBalances = emptyList(),
                    postBalances = emptyList(),
                    innerInstructions = meta.innerInstructions,
                    logMessages = meta.logMessages,
                ),
            )
            streamed.executedInstructions().unwrap() shouldBe fetched.executedInstructions().unwrap()
            streamed.eventRecords().unwrap() shouldBe fetched.eventRecords().unwrap()
            streamed.programFailure() shouldBe fetched.programFailure()
        }
    }

    context("program failures") {
        test("a custom error is attributed to the program the logs name") {
            val tx = transaction(
                err = """{"InstructionError":[0,{"Custom":6000}]}""",
                logs = listOf("Program $PROGRAM invoke [1]", "Program $OTHER invoke [2]", "Program $OTHER failed: custom program error: 0x1770", "Program $PROGRAM failed: custom program error: 0x1770"),
            )
            tx.programFailure() shouldBe SolanaProgramFailure(0, 6000, OTHER, 2)
        }

        test("without logs, a top-level instruction that made no CPIs raised the error itself; otherwise it stays unresolved") {
            val noCalls = transaction(err = """{"InstructionError":[0,{"Custom":6000}]}""", logs = emptyList())
            // the fixture's inner instructions belong to instruction 0, so drop them to model an instruction without CPIs
            val withoutInner = noCalls.copy(meta = noCalls.meta!!.copy(innerInstructions = emptyList(), logMessages = null))
            withoutInner.programFailure() shouldBe SolanaProgramFailure(0, 6000, PROGRAM, 1)

            val withInner = noCalls.copy(meta = noCalls.meta.copy(logMessages = null))
            withInner.programFailure() shouldBe SolanaProgramFailure(0, 6000, null, null)
        }

        test("only custom program errors are reported") {
            transaction().programFailure().shouldBeNull()
            transaction(err = """{"InstructionError":[0,"InvalidAccountData"]}""").programFailure().shouldBeNull()
        }

        test("an error resolves only against the program it was attributed to") {
            val decoder = SolanaProgramErrorDecoder.of(ProgramErrors.entries)
            decoder.decode(SolanaProgramFailure(0, 6000, PROGRAM, 1), PROGRAM) shouldBe ProgramErrors.Overflow
            decoder.decode(SolanaProgramFailure(0, 6000, OTHER, 1), PROGRAM).shouldBeNull()
            decoder.decode(SolanaProgramFailure(0, 6000, null, null), PROGRAM).shouldBeNull()
        }
    }

    test("every corpus transaction yields event records from programs it invoked, and attributes its custom errors") {
        var records = 0
        var failures = 0
        var attributed = 0
        for (record in transactionCorpus()) {
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransaction>(Kotlinx.DEFAULT.parseToJsonElement(record).jsonObject.getValue("rpc"))
            val events = tx.eventRecords().unwrap()
            val invoked = tx.executedInstructions().unwrap().mapTo(HashSet()) { it.instruction.programId }
            events.all { it.programId in invoked } shouldBe true
            records += events.size

            val failure = tx.programFailure() ?: continue
            failures++
            if (failure.programId != null) {
                attributed++
                (failure.programId in invoked) shouldBe true
            }
        }
        (records > 0) shouldBe true
        // every custom error in the corpus has logs naming the program that raised it
        attributed shouldBe failures
    }

    test("return data decodes only when the expected program set it") {
        val get = SolanaInstructionDescriptor.Returning("get", SolanaDiscriminator.of(1), SolanaCodec.UNIT, SolanaCodec.of({ readU64() }, { writeU64(it) }))
        val returned = ReturnData(PROGRAM, SolanaBytes.fromHex("2a00000000000000"))
        get.decodeReturn(returned, PROGRAM).unwrap().toString() shouldBe "42"
        get.decodeReturn(returned, OTHER).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
    }

    test("an event subscription filters logs by program and delivers each transaction's events together") {
        val client = LogsClient(
            """{"context":{"slot":3},"value":{"signature":"$SIGNATURE","err":null,"logs":[
                "Program $PROGRAM invoke [1]", "${logData(pinged(1))}", "${logData(pinged(2))}", "Program $PROGRAM success",
                "Program $OTHER invoke [1]", "${logData(pinged(3))}", "Program $OTHER success"
            ]}}""",
        )
        val channel = SolanaEventFilter(SolanaProvider(client), Pinged, PROGRAM).subscribe().send().unwrap()
        // the other program's event is not delivered
        channel.receive()!!.map { it.n } shouldBe listOf(1, 2)
        channel.poll().shouldBeNull()
        client.params.toString() shouldContain PROGRAM.toString()
    }
})

/** Answers a logs subscription with one [notification], recording the params it was sent. */
private class LogsClient(private val notification: String) : JsonRpcClient {
    private val config = RpcClientConfig().subscriptionDescriptor(SolanaSubscriptionDescriptor)
    var params: List<Any?> = emptyList()

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> {
        this.params = config.subscriptionDescriptor.resolve(params).params.toList()
        return success(QueueChannel.spscUnbounded<T>().apply { offer(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(notification))) })
    }

    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> = error("Unexpected request")
    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}
