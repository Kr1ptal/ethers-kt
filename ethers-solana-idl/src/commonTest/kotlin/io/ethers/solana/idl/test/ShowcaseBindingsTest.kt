package io.ethers.solana.idl.test

import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.success
import io.ethers.crypto.Base58
import io.ethers.providers.HttpClient
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaEventContext
import io.ethers.solana.codec.SolanaEventSource
import io.ethers.solana.codec.SolanaOption
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.program.ExecutedInstruction
import io.ethers.solana.program.InstructionPosition
import io.ethers.solana.program.SolanaAccountError
import io.ethers.solana.program.SolanaProgramFailure
import io.ethers.solana.program.decodeEvents
import io.ethers.solana.program.decodeInstructions
import io.ethers.solana.program.decodeReturn
import io.ethers.solana.program.eventRecords
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.ReturnData
import io.ethers.solana.types.rpc.RpcContext
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonElement
import kotlin.io.encoding.Base64

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

private fun address(byte: Int) = SolanaAddress(ByteArray(32) { byte.toByte() })

private val CONFIG = address(1)
private val PAYER = address(2)
private val USER = address(3)
private val INPUT = address(4)
private val OUTPUT = address(5)
private val REFERRER = address(6)
private val EXTRA = address(7)
private val SYSTEM_PROGRAM = SolanaAddress("11111111111111111111111111111111")

private val COUNTER = Showcase.Counter(bigIntegerOf(7), 254, listOf(true, false), -1, SolanaBytes.fromHex("aabb"))
private val SIGNATURE = SolanaSignature(ByteArray(64) { 9 })

/** Logs of one top-level invocation of the showcase program that emits each of [events]. */
private fun showcaseLogs(vararg events: ByteArray): List<String> = listOf("Program ${Showcase.PROGRAM_ID} invoke [1]") + events.map { "Program data: ${Base64.encode(it)}" } + "Program ${Showcase.PROGRAM_ID} success"

private const val COUNTER_PAYLOAD = "0700000000000000 fe 02000000 01 00 ff 02000000 aabb"

class ShowcaseBindingsTest : FunSpec({
    context("types") {
        test("structs and enums encode in field order, enums with a u8 tag") {
            Showcase.ShowcasePoint.encode(Showcase.ShowcasePoint(1, -1)) shouldBe hex("01000000 ffffffff")
            Showcase.Mode.encode(Showcase.Mode.Unit) shouldBe hex("00")
            Showcase.Mode.encode(Showcase.Mode.Fixed(bigIntegerOf(5), -2)) shouldBe hex("01 0500000000000000 feffffffffffffff")
            Showcase.Mode.encode(Showcase.Mode.Range(1, 2, bigIntegerOf(-1))) shouldBe hex("02 01 02" + "ff".repeat(16))
            Showcase.Mode.encode(Showcase.Mode.Other(Showcase.OtherPoint(1.5f, -2.0))) shouldBe hex("03 0000c03f 00000000000000c0")
            Showcase.Counter.encode(COUNTER) shouldBe hex(COUNTER_PAYLOAD)
        }

        test("an undeclared enum variant is malformed") {
            Showcase.Mode.decode(hex("04")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe
                SolanaDecodeError.Reason.INVALID_VARIANT
        }

        test("every field kind round-trips, including nested options, fixed arrays and aliases") {
            val config = Showcase.Config(
                authority = CONFIG,
                feeBps = 30,
                label = "main",
                limit = null,
                tags = listOf("a", "b"),
                seed = SolanaBytes.fromHex("01020304"),
                weights = listOf(1, 2, 4294967295L),
                nested = SolanaOption.Some(null),
                mode = Showcase.Mode.Fixed(bigIntegerOf(9), 10),
                total = BigInteger("18446744073709551615"),
                last = Showcase.Snapshot(bigIntegerOf(3), 4),
                origin = Showcase.ShowcasePoint(5, 6),
            )
            val encoded = Showcase.Config.encode(config)
            Showcase.Config.decode(encoded).unwrap() shouldBe config
            // nested = Some(None) is two bytes, None one
            val none = config.copy(nested = SolanaOption.None)
            Showcase.Config.encode(none).size shouldBe encoded.size - 1
            Showcase.Config.decode(Showcase.Config.encode(none)).unwrap() shouldBe none
        }

        test("a field named context on an event is renamed, keeping the event context") {
            val context = SolanaEventContext(Showcase.PROGRAM_ID)
            val event = Showcase.Event.Swapped(bigIntegerOf(1), bigIntegerOf(2), "memo", context)
            event.contextField shouldBe "memo"
            event.context shouldBe context
        }
    }

    context("accounts") {
        test("each account decodes through its factory, ignoring allocation padding") {
            val data = hex("ffb004f5bcfd7c19 $COUNTER_PAYLOAD") + ByteArray(16)
            Showcase.Counter.ACCOUNT.decode(data).unwrap() shouldBe COUNTER
            Showcase.Counter.ACCOUNT.encode(COUNTER) shouldBe hex("ffb004f5bcfd7c19 $COUNTER_PAYLOAD")
        }

        test("an account filter selects by discriminator, then by fields at a known offset") {
            val api = SolanaProvider(HttpClient("https://example.invalid"))
            Showcase.Config.filter(api).authority(CONFIG).feeBps(30).label("main").accountFilters shouldBe listOf(
                AccountFilter.Memcmp(0, Base58.encode(hex("9b0caae01efacc82"))),
                AccountFilter.Memcmp(8, CONFIG.toBase58()),
                AccountFilter.Memcmp(40, Base58.encode(hex("1e00"))),
                // a string starts at a known offset, though nothing after it does
                AccountFilter.Memcmp(42, Base58.encode(hex("04000000 6d61696e"))),
            )
            Showcase.Counter.filter(api).flags(listOf(true)).accountFilters[1] shouldBe AccountFilter.Memcmp(17, Base58.encode(hex("01000000 01")))
            api.close()
        }

        test("fetch, watch and query decode accounts the program owns") {
            val counter = """{"data":["${Base64.encode(Showcase.Counter.ACCOUNT.encode(COUNTER))}","base64"],"executable":false,"lamports":1,"owner":"${Showcase.PROGRAM_ID}","rentEpoch":0}"""
            val client = CannedClient("""{"context":{"slot":7},"value":$counter}""")
            val api = SolanaProvider(client)
            Showcase.Counter.fetch(api, CONFIG).send().unwrap().data shouldBe COUNTER
            Showcase.Counter.watch(api, CONFIG).send().unwrap().receive()!!.data shouldBe COUNTER
            // another deployment owns nothing here, so the same account fails its owner check
            Showcase.Counter.fetch(api, CONFIG, programId = EXTRA).send().unwrapError()
                .shouldBeInstanceOf<SolanaAccountError.Decode>().error.shouldBeInstanceOf<SolanaDecodeError.UnexpectedOwner>()

            client.response = """{"context":{"slot":7},"value":[{"pubkey":"$CONFIG","account":$counter}]}"""
            Showcase.Counter.filter(api).bump(254).query().send().unwrap().single().data shouldBe COUNTER
        }

        test("program dispatch picks the account type by discriminator") {
            Showcase.decodeAccount(Showcase.Counter.ACCOUNT.encode(COUNTER)).unwrap() shouldBe COUNTER
            Showcase.decodeAccount(hex("fa01")).unwrap() shouldBe Showcase.Marker
            Showcase.decodeAccount(hex("0000")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
        }
    }

    context("instructions") {
        val swapAccounts = Showcase.SwapAccounts(CONFIG, USER, Showcase.SwapAccounts.Vaults(INPUT, OUTPUT))
        val swapArgs = Showcase.SwapArgs(bigIntegerOf(100), bigIntegerOf(90), Showcase.Mode.Unit)

        test("a builder lays out accounts by role and data as discriminator plus arguments") {
            val instruction = Showcase.initialize(Showcase.InitializeAccounts(CONFIG, PAYER), Showcase.InitializeArgs(30, "hi"))
            instruction.programId shouldBe Showcase.PROGRAM_ID
            instruction.keys shouldBe listOf(AccountMeta.writable(CONFIG), AccountMeta.signerAndWritable(PAYER), AccountMeta(SYSTEM_PROGRAM))
            instruction.data shouldBe SolanaBytes.fromHex("afaf6d1f0d989bed 1e00 02000000 6869".replace(" ", ""))
        }

        test("an absent optional account is passed as the program id, and account groups are flattened in order") {
            val instruction = Showcase.swap(swapAccounts, swapArgs, listOf(AccountMeta(EXTRA)))
            instruction.keys shouldBe listOf(
                AccountMeta(CONFIG),
                AccountMeta.signer(USER),
                AccountMeta.writable(INPUT),
                AccountMeta.writable(OUTPUT),
                AccountMeta(Showcase.PROGRAM_ID),
                AccountMeta(EXTRA),
            )
            instruction.data shouldBe SolanaBytes.fromHex("f8c69e91e17587c8 6400000000000000 01 5a00000000000000 00".replace(" ", ""))
        }

        test("decoding recovers arguments, account roles and remaining accounts") {
            val instruction = Showcase.swap(swapAccounts.copy(referrer = REFERRER), swapArgs, listOf(AccountMeta(EXTRA)))
            val decoded = Showcase.decodeInstruction(instruction).unwrap().shouldBeInstanceOf<Showcase.Instruction.Swap>()
            decoded.args shouldBe swapArgs
            decoded.accounts shouldBe swapAccounts.copy(referrer = REFERRER)
            decoded.remainingAccounts shouldBe listOf(AccountMeta(EXTRA))

            Showcase.decodeInstruction(Showcase.swap(swapAccounts, swapArgs)).unwrap()
                .shouldBeInstanceOf<Showcase.Instruction.Swap>().accounts.referrer.shouldBeNull()
            Showcase.decodeInstruction(Showcase.ping()).unwrap() shouldBe Showcase.Instruction.Ping(emptyList())
        }

        test("elevated privileges are accepted; missing required privileges are not") {
            val elevated = Showcase.swap(swapAccounts, swapArgs).let { ix ->
                ix.copy(keys = ix.keys.map { AccountMeta.signerAndWritable(it.publicKey) })
            }
            Showcase.decodeInstruction(elevated).unwrap().shouldBeInstanceOf<Showcase.Instruction.Swap>()

            val unsigned = Showcase.swap(swapAccounts, swapArgs).let { ix ->
                ix.copy(keys = ix.keys.map { AccountMeta(it.publicKey, signer = false, writable = it.writable) })
            }
            Showcase.decodeInstruction(unsigned).unwrapError().shouldBeInstanceOf<SolanaDecodeError.InvalidAccounts>().message shouldBe
                "Account 'user' at position 1 must be a signer"
        }

        test("unknown data is unrecognized") {
            Showcase.decodeInstruction(Instruction(Showcase.PROGRAM_ID, emptyList(), hex("0102"))).unwrapError()
                .shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
        }

        test("instructions are identified by discriminator, not program id, so an alternate deployment decodes too") {
            val instruction = Showcase.swap(swapAccounts, swapArgs, programId = EXTRA)
            instruction.programId shouldBe EXTRA
            // the absent optional account's placeholder is the deployment's own id
            instruction.keys[4] shouldBe AccountMeta(EXTRA)
            Showcase.decodeInstruction(instruction).unwrap()
                .shouldBeInstanceOf<Showcase.Instruction.Swap>().accounts.referrer.shouldBeNull()
        }

        test("an inner instruction is decoded without requiring signers, which a program can supply through invoke_signed") {
            val unsigned = Showcase.swap(swapAccounts, swapArgs).let { ix ->
                ix.copy(keys = ix.keys.map { AccountMeta(it.publicKey, signer = false, writable = it.writable) })
            }
            Showcase.decodeInstruction(unsigned, checkSigners = false).unwrap().shouldBeInstanceOf<Showcase.Instruction.Swap>()

            val executed = listOf(
                ExecutedInstruction(InstructionPosition(0), Showcase.ping()),
                ExecutedInstruction(InstructionPosition(0, 0, 2), unsigned),
                ExecutedInstruction(InstructionPosition(1), Instruction(EXTRA, emptyList(), hex("09"))),
            )
            val decoded = executed.decodeInstructions(Showcase.PROGRAM_ID, Showcase::decodeInstruction)
            decoded.map { it.position } shouldBe listOf(InstructionPosition(0), InstructionPosition(0, 0, 2))
            decoded[1].value.unwrap().shouldBeInstanceOf<Showcase.Instruction.Swap>()
        }

        test("return values decode through the instruction's descriptor") {
            Showcase.SWAP.decodeReturn(hex("2a00000000000000")).unwrap() shouldBe bigIntegerOf(42)
            Showcase.RESET.decodeReturn(Showcase.Counter.encode(COUNTER)).unwrap() shouldBe COUNTER
        }

        test("descriptors are listed for generic dispatch") {
            Showcase.INSTRUCTIONS.map { it.name } shouldBe listOf("initialize", "swap", "reset", "ping")
            Showcase.PING.encodeData(Unit) shouldBe hex("09")
        }
    }

    context("events") {
        val context = SolanaEventContext(Showcase.PROGRAM_ID, SolanaEventSource.Log(0, 3, 1))

        test("events decode with their context, dispatched by discriminator") {
            val swapped = Showcase.Event.Swapped(bigIntegerOf(1), bigIntegerOf(2), "memo", context)
            val data = Showcase.Event.Swapped.encode(swapped)
            data shouldBe hex("d93434539387606d 0100000000000000 0200000000000000 04000000 6d656d6f")
            Showcase.decodeEvent(data, context).unwrap() shouldBe swapped
            Showcase.decodeEvent(hex("acf805fd31ffffe8"), context).unwrap() shouldBe Showcase.Event.Paused(context)
            Showcase.decodeEvent(hex("0000000000000000"), context).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
        }

        test("events are decoded from the logs a transaction emitted, with their source") {
            val swapped = Showcase.Event.Swapped(bigIntegerOf(1), bigIntegerOf(2), "memo", context)
            val notification = ContextValue(RpcContext(bigIntegerOf(3)), LogsNotification(SIGNATURE, null, showcaseLogs(Showcase.Event.Swapped.encode(swapped))))
            val event = notification.eventRecords().decodeEvents(Showcase.PROGRAM_ID, Showcase::decodeEvent).single()
                .shouldBeInstanceOf<Showcase.Event.Swapped>()
            event.amountIn shouldBe bigIntegerOf(1)
            event.context.source shouldBe SolanaEventSource.Log(0, 1, 1)
            event.context.execution.signature shouldBe SIGNATURE
        }

        test("an event filter subscribes to one event type of the program") {
            val swapped = Showcase.Event.Swapped.encode(Showcase.Event.Swapped(bigIntegerOf(1), bigIntegerOf(2), "memo", context))
            val paused = Showcase.Event.Paused.encode(Showcase.Event.Paused(context))
            val logs = showcaseLogs(paused, swapped).joinToString { "\"$it\"" }
            val client = CannedClient("""{"context":{"slot":3},"value":{"signature":"$SIGNATURE","err":null,"logs":[$logs]}}""")
            val channel = Showcase.Event.Swapped.filter(SolanaProvider(client)).subscribe().send().unwrap()
            // the Paused event in the same transaction is not delivered
            channel.receive()!!.single().contextField shouldBe "memo"
            channel.poll().shouldBeNull()
        }

        test("an event type also used as a value is generated as both") {
            val event = Showcase.Event.Snapshot(bigIntegerOf(3), 4, context)
            val value = Showcase.Snapshot(bigIntegerOf(3), 4)
            Showcase.Event.Snapshot.encode(event).copyOfRange(8, 32) shouldBe Showcase.Snapshot.encode(value)
        }
    }

    test("a failure resolves to an error only when it was attributed to this program") {
        Showcase.decodeError(SolanaProgramFailure(0, 6000, Showcase.PROGRAM_ID, 2)) shouldBe Showcase.Error.Unauthorized
        Showcase.decodeError(SolanaProgramFailure(0, 6000, EXTRA, 1)).shouldBeNull()
        Showcase.decodeError(SolanaProgramFailure(0, 6000, null, null)).shouldBeNull()
        Showcase.decodeError(SolanaProgramFailure(0, 6000, EXTRA, 1), programId = EXTRA) shouldBe Showcase.Error.Unauthorized
    }

    test("return data decodes through the instruction that produced it, when this program set it") {
        Showcase.SWAP.decodeReturn(ReturnData(Showcase.PROGRAM_ID, SolanaBytes.fromHex("2a00000000000000")), Showcase.PROGRAM_ID).unwrap() shouldBe bigIntegerOf(42)
        Showcase.SWAP.decodeReturn(ReturnData(EXTRA, SolanaBytes.fromHex("2a00000000000000")), Showcase.PROGRAM_ID).unwrapError()
            .shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
    }

    test("errors are looked up by code within this program") {
        Showcase.decodeError(6000) shouldBe Showcase.Error.Unauthorized
        Showcase.Error.Unauthorized.message shouldBe "Not allowed"
        Showcase.decodeError(6001)?.message.shouldBeNull()
        Showcase.decodeError(1).shouldBeNull()
    }

    test("constants") {
        Showcase.MAX_FEE shouldBe 10000
        Showcase.SEED shouldBe SolanaBytes.fromHex("73656564")
        Showcase.BIG shouldBe BigInteger("340282366920938463463374607431768211455")
        Showcase.ENABLED shouldBe true
        Showcase.NAME shouldBe "showcase"
        Showcase.OFFSET shouldBe -5L
    }
})

/** Answers every request and subscription with [response]. */
private class CannedClient(var response: String) : JsonRpcClient {
    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> = success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(response)))

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> = success(QueueChannel.spscUnbounded<T>().apply { offer(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(response))) })

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}
