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
import io.ethers.solana.codec.BorshReader
import io.ethers.solana.codec.SolanaAccountField
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
import io.ethers.solana.types.rpc.DataSlice
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.ReturnData
import io.ethers.solana.types.rpc.RpcContext
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

        // the layout of an unpacked C struct on-chain, computed independently: each field at a multiple of its
        // alignment, 128-bit integers 8-byte aligned, and the size a multiple of the struct's alignment
        val gauge = Showcase.Gauge(
            flag = 1,
            amount = bigIntegerOf(2),
            big = bigIntegerOf(3),
            small = 4,
            reading = Showcase.Reading(5, 6),
            readings = listOf(Showcase.Reading(7, 8), Showcase.Reading(9, 10)),
            tail = 11,
            aligned = Showcase.Aligned(12),
        )
        val gaugeData = "0913f9bd9eabe2cd" + "01 00000000000000 0200000000000000 03000000000000000000000000000000 0400 0000" +
            "05 000000 06000000 07 000000 08000000 09 000000 0a000000 0b 000000 0c 000000000000000000000000000000"

        test("a zero-copy account follows its C layout, with padding between and after fields") {
            Showcase.Gauge.ACCOUNT.encode(gauge) shouldBe hex(gaugeData)
            Showcase.Gauge.ACCOUNT.decode(hex(gaugeData)).unwrap() shouldBe gauge
            Showcase.Gauge.encode(gauge).size shouldBe 80
        }

        test("padding is not checked when decoding") {
            val dirty = hex(gaugeData)
            for (padding in listOf(9..15, 42..43, 45..47, 53..55, 61..63, 69..71, 73..87)) {
                for (i in padding) dirty[i] = 0xff.toByte()
            }
            Showcase.Gauge.ACCOUNT.decode(dirty).unwrap() shouldBe gauge
        }

        test("a zero-copy account filters fields at their padded offsets") {
            val api = SolanaProvider(HttpClient("https://example.invalid"))
            Showcase.Gauge.filter(api).amount(bigIntegerOf(2)).big(bigIntegerOf(3)).tail(11).aligned(Showcase.Aligned(12)).accountFilters.drop(1) shouldBe listOf(
                AccountFilter.Memcmp(16, Base58.encode(hex("0200000000000000"))),
                AccountFilter.Memcmp(24, Base58.encode(hex("03000000000000000000000000000000"))),
                AccountFilter.Memcmp(68, Base58.encode(hex("0b"))),
                AccountFilter.Memcmp(72, Base58.encode(hex("0c000000000000000000000000000000"))),
            )
            api.close()
        }

        context("account slices") {
            val gaugeBytes = hex(gaugeData)
            fun account(data: ByteArray, owner: SolanaAddress = Showcase.PROGRAM_ID) = """{"data":["${Base64.encode(data)}","base64"],"executable":false,"lamports":1,"owner":"$owner","rentEpoch":0}"""
            fun dataSlice(params: List<Any?>) = (params.last() as JsonObject)["dataSlice"]

            test("descriptors exist for fields at a fixed offset with a fixed size, at their padded zero-copy offsets") {
                Showcase.Config.Fields.authority.offset shouldBe 0
                Showcase.Config.Fields.feeBps.offset shouldBe 32
                Showcase.Config.Fields.feeBps.size shouldBe 2
                // a string has no fixed size, and nothing after it a fixed offset; Config.Fields has no label
                Showcase.Gauge.Fields.amount.offset shouldBe 8
                Showcase.Gauge.Fields.big.offset shouldBe 16
                Showcase.Gauge.Fields.tail.offset shouldBe 60
                Showcase.Gauge.Fields.aligned.size shouldBe 16
            }

            test("a selection reads one slice, from its first field to the end of its last") {
                val selection = Showcase.Gauge.select(Showcase.Gauge.Fields.tail, Showcase.Gauge.Fields.big)
                selection.dataSlice shouldBe DataSlice(24, 45)
                selection.checksDiscriminator shouldBe false
                // starting at the first field, the slice includes the discriminator to check the account type by
                val first = Showcase.Gauge.select(Showcase.Gauge.Fields.flag, Showcase.Gauge.Fields.amount)
                first.dataSlice shouldBe DataSlice(0, 24)
                first.checksDiscriminator shouldBe true
            }

            test("fields can be named within Fields, and read as properties of the slice") {
                val selection = Showcase.Gauge.select { fields(big, tail) }
                selection.dataSlice shouldBe Showcase.Gauge.select(Showcase.Gauge.Fields.big, Showcase.Gauge.Fields.tail).dataSlice
                val client = CannedClient("""{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(24, 69))}}""")
                val api = SolanaProvider(client)
                val slice = selection.fetch(api, CONFIG).send().unwrap()
                slice.big shouldBe bigIntegerOf(3)
                slice.tail shouldBe 11
                // a field the selection does not include cannot be read
                shouldThrow<IllegalArgumentException> { slice.flag }
                api.close()
            }

            test("combined projections fetch, watch, batch, query and subscribe to typed results") {
                data class GaugeValue(val big: BigInteger, val tail: Int)
                var calls = 0
                val selection = Showcase.Gauge.select { combine(big, tail, ::GaugeValue) }
                val mapped = Showcase.Gauge.select {
                    combine(big, tail) { b, t ->
                        calls++
                        GaugeValue(b, t)
                    }.map { it.tail }
                }
                calls shouldBe 0
                val expected = GaugeValue(bigIntegerOf(3), 11)
                val client = CannedClient("""{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(24, 69))}}""")
                val api = SolanaProvider(client)
                selection.fetch(api, CONFIG).send().unwrap() shouldBe expected
                selection.watch(api, CONFIG).send().unwrap().receive()!!.unwrap() shouldBe expected
                mapped.fetch(api, CONFIG).send().unwrap() shouldBe 11
                calls shouldBe 1
                selection.dataSlice shouldBe DataSlice(24, 45)

                client.response = """{"context":{"slot":7},"value":[${account(gaugeBytes.copyOfRange(24, 69))},null]}"""
                val batch = selection.fetch(api, listOf(CONFIG, EXTRA)).send().unwrap()
                batch[0].unwrap() shouldBe expected
                batch[1].unwrapError().shouldBeInstanceOf<SolanaAccountError.Missing>()

                client.response = """{"context":{"slot":7},"value":[{"pubkey":"$CONFIG","account":${account(gaugeBytes.copyOfRange(24, 69))}}]}"""
                Showcase.Gauge.filter(api).query(selection).send().unwrap().value.single().unwrap() shouldBe expected
                client.response = """{"context":{"slot":7},"value":{"pubkey":"$CONFIG","account":${account(gaugeBytes.copyOfRange(24, 69))}}}"""
                Showcase.Gauge.filter(api).subscribe(selection).send().unwrap().receive()!!.unwrap() shouldBe expected

                client.response = """{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(24, 69), EXTRA)}}"""
                selection.program(EXTRA).fetch(api, CONFIG).send().unwrap() shouldBe expected
                api.close()
            }

            test("a projection decode failure skips the transformation and preserves account error context") {
                val booleanTail = SolanaAccountField<Showcase.Gauge, Boolean>("booleanTail", 60, 1, { it.readBool() }, { writeBool(it) })
                var calls = 0
                val selection = Showcase.Gauge.select {
                    combine(big, booleanTail) { _, flag ->
                        calls++
                        flag
                    }
                }
                // The tail byte is 11, which is not a valid Borsh bool.
                val client = CannedClient("""{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(24, 69))}}""")
                val api = SolanaProvider(client)
                val error = selection.fetch(api, CONFIG).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
                error.address shouldBe CONFIG
                error.context.slot shouldBe bigIntegerOf(7)
                error.error.shouldBeInstanceOf<SolanaDecodeError.Malformed>()
                calls shouldBe 0
                api.close()
            }

            test("combine supports one through five fields and deduplicates selected descriptors") {
                val client = CannedClient("""{"context":{"slot":7},"value":${account(byteArrayOf(11))}}""")
                val api = SolanaProvider(client)
                Showcase.Gauge.select { combine(tail) { it } }.fetch(api, CONFIG).send().unwrap() shouldBe 11
                Showcase.Gauge.select { combine(tail, tail, tail) { a, b, c -> a + b + c } }.fetch(api, CONFIG).send().unwrap() shouldBe 33
                Showcase.Gauge.select { combine(tail, tail, tail, tail) { a, b, c, d -> a + b + c + d } }.fetch(api, CONFIG).send().unwrap() shouldBe 44
                val five = Showcase.Gauge.select { combine(tail, tail, tail, tail, tail) { a, b, c, d, e -> a + b + c + d + e } }
                five.fetch(api, CONFIG).send().unwrap() shouldBe 55
                five.fields shouldBe listOf(Showcase.Gauge.Fields.tail)
                shouldThrow<IllegalArgumentException> { Showcase.Gauge.select { fields() } }
                api.close()
            }

            test("fetch and watch request the slice and decode each selected field from it") {
                val selection = Showcase.Gauge.select(Showcase.Gauge.Fields.big, Showcase.Gauge.Fields.tail)
                val client = CannedClient("""{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(24, 69))}}""")
                val api = SolanaProvider(client)

                val slice = selection.fetch(api, CONFIG).send().unwrap()
                client.params[0] shouldBe "getAccountInfo"
                dataSlice(client.params) shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"offset":24,"length":45}""")
                slice[Showcase.Gauge.Fields.big] shouldBe bigIntegerOf(3)
                slice[Showcase.Gauge.Fields.tail] shouldBe 11
                slice.context.slot shouldBe bigIntegerOf(7)
                shouldThrow<IllegalArgumentException> { slice[Showcase.Gauge.Fields.flag] }

                val watched = selection.watch(api, CONFIG).send().unwrap().receive()!!.unwrap()
                client.params[0] shouldBe "account"
                dataSlice(client.params) shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"offset":24,"length":45}""")
                watched[Showcase.Gauge.Fields.tail] shouldBe 11

                client.response = """{"context":{"slot":7},"value":[${account(gaugeBytes.copyOfRange(24, 69))},null]}"""
                val batch = selection.fetch(api, listOf(CONFIG, EXTRA)).send().unwrap()
                batch[0].unwrap()[Showcase.Gauge.Fields.big] shouldBe bigIntegerOf(3)
                batch[1].unwrapError().shouldBeInstanceOf<SolanaAccountError.Missing>()
                api.close()
            }

            test("the owner is checked, and the discriminator when the slice includes it") {
                val client = CannedClient("""{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(24, 69), owner = EXTRA)}}""")
                val api = SolanaProvider(client)
                val selection = Showcase.Gauge.select(Showcase.Gauge.Fields.big)
                selection.fetch(api, CONFIG).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
                    .error.shouldBeInstanceOf<SolanaDecodeError.UnexpectedOwner>()
                selection.program(EXTRA).fetch(api, CONFIG).send().unwrap()[Showcase.Gauge.Fields.big] shouldBe bigIntegerOf(3)

                val first = Showcase.Gauge.select(Showcase.Gauge.Fields.flag, Showcase.Gauge.Fields.amount)
                client.response = """{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(0, 24))}}"""
                first.fetch(api, CONFIG).send().unwrap()[Showcase.Gauge.Fields.amount] shouldBe bigIntegerOf(2)
                val other = gaugeBytes.copyOfRange(0, 24).also { it[0] = (it[0] + 1).toByte() }
                client.response = """{"context":{"slot":7},"value":${account(other)}}"""
                first.fetch(api, CONFIG).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
                    .error.shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
                api.close()
            }

            test("an account too short for the selection fails, and a missing one is reported missing") {
                val client = CannedClient("""{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(24, 40))}}""")
                val api = SolanaProvider(client)
                val selection = Showcase.Gauge.select(Showcase.Gauge.Fields.big, Showcase.Gauge.Fields.tail)
                selection.fetch(api, CONFIG).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
                    .error.shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe SolanaDecodeError.Reason.TRUNCATED
                client.response = """{"context":{"slot":7},"value":null}"""
                selection.fetch(api, CONFIG).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Missing>()
                api.close()
            }

            test("a filter queries and subscribes to slices, selecting accounts by discriminator before slicing") {
                val selection = Showcase.Gauge.select(Showcase.Gauge.Fields.tail)
                val client = CannedClient("""{"context":{"slot":7},"value":[{"pubkey":"$CONFIG","account":${account(byteArrayOf(11))}}]}""")
                val api = SolanaProvider(client)
                val query = Showcase.Gauge.filter(api).flag(1).query(selection).send().unwrap()
                client.params[0] shouldBe "getProgramAccounts"
                dataSlice(client.params) shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"offset":68,"length":1}""")
                ((client.params.last() as JsonObject)["filters"] as JsonArray).size shouldBe 2
                query.context.slot shouldBe bigIntegerOf(7)
                query.value.single().unwrap()[Showcase.Gauge.Fields.tail] shouldBe 11

                client.response = """{"context":{"slot":7},"value":{"pubkey":"$CONFIG","account":${account(byteArrayOf(11))}}}"""
                Showcase.Gauge.filter(api).subscribe(selection).send().unwrap().receive()!!.unwrap()[Showcase.Gauge.Fields.tail] shouldBe 11
                client.params[0] shouldBe "program"
                dataSlice(client.params) shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"offset":68,"length":1}""")
                api.close()
            }
        }

        context("nested field paths") {
            val gaugeBytes = hex(gaugeData)
            fun account(data: ByteArray) = """{"data":["${Base64.encode(data)}","base64"],"executable":false,"lamports":1,"owner":"${Showcase.PROGRAM_ID}","rentEpoch":0}"""
            fun dataSlice(params: List<Any?>) = (params.last() as JsonObject)["dataSlice"]

            /** [field] read from the encoded account [data] at its offset, as the codec wrote it there. */
            fun <V> readAt(data: ByteArray, field: SolanaAccountField<*, V>): V = field.read(BorshReader(data, 8 + field.offset))

            val ledger = Showcase.Ledger(
                version = 1,
                origin = Showcase.ShowcasePoint(2, 3),
                slots = listOf(
                    Showcase.Slot(SolanaBytes.fromHex("0a0b0c0d"), bigIntegerOf(4), Showcase.ShowcasePoint(5, 6), Showcase.Path(7)),
                    Showcase.Slot(SolanaBytes.fromHex("1a1b1c1d"), bigIntegerOf(8), Showcase.ShowcasePoint(9, 10), Showcase.Path(11)),
                ),
                grid = listOf(listOf(1, 2, 3), listOf(4, 5, 6)),
                empty = Showcase.Marker,
                path = Showcase.Path(12),
                note = Showcase.Note(13, "memo"),
                after = bigIntegerOf(14),
            )
            val ledgerBytes = Showcase.Ledger.ACCOUNT.encode(ledger)

            test("fields of nested Borsh structs and elements of fixed arrays are at their encoded offsets") {
                with(Showcase.Ledger.Fields) {
                    origin.offset shouldBe 1
                    origin.y.offset shouldBe 5
                    slots.offset shouldBe 9
                    slots.size shouldBe 42
                    slots.elementSize shouldBe 21
                    slots[1].offset shouldBe 30
                    slots[1].nameField[2].offset shouldBe 32
                    slots[1].sizeField.offset shouldBe 34
                    slots[1].point.y.offset shouldBe 46
                    slots[1].path.depth.offset shouldBe 50
                    grid.offset shouldBe 51
                    grid[1][2].offset shouldBe 61
                    // the empty marker takes no bytes, and has no descriptor
                    path.offset shouldBe 63
                    path.depth.offset shouldBe 63

                    readAt(ledgerBytes, version) shouldBe 1
                    readAt(ledgerBytes, origin) shouldBe ledger.origin
                    readAt(ledgerBytes, origin.y) shouldBe 3
                    readAt(ledgerBytes, slots) shouldBe ledger.slots
                    readAt(ledgerBytes, slots[0].path.depth) shouldBe 7
                    readAt(ledgerBytes, slots[1]) shouldBe ledger.slots[1]
                    readAt(ledgerBytes, slots[1].nameField) shouldBe SolanaBytes.fromHex("1a1b1c1d")
                    readAt(ledgerBytes, slots[1].nameField[2]) shouldBe 0x1c
                    readAt(ledgerBytes, slots[1].sizeField) shouldBe bigIntegerOf(8)
                    readAt(ledgerBytes, slots[1].point.y) shouldBe 10
                    readAt(ledgerBytes, grid[1]) shouldBe listOf(4, 5, 6)
                    readAt(ledgerBytes, grid[1][2]) shouldBe 6
                    readAt(ledgerBytes, path.depth) shouldBe 12
                }
            }

            test("fields of nested zero-copy structs and array elements are at their padded offsets") {
                with(Showcase.Gauge.Fields) {
                    reading.offset shouldBe 36
                    reading.kind.offset shouldBe 36
                    // three bytes of padding after kind
                    reading.value.offset shouldBe 40
                    readings.elementSize shouldBe 8
                    readings[0].kind.offset shouldBe 44
                    readings[1].kind.offset shouldBe 52
                    readings[1].value.offset shouldBe 56
                    aligned.x.offset shouldBe 64
                    aligned.size shouldBe 16

                    readAt(gaugeBytes, reading.value) shouldBe 6
                    readAt(gaugeBytes, readings[0].kind) shouldBe 7
                    readAt(gaugeBytes, readings[1]) shouldBe Showcase.Reading(9, 10)
                    readAt(gaugeBytes, readings[1].value) shouldBe 10
                    readAt(gaugeBytes, aligned.x) shouldBe 12
                }
            }

            test("paths are named after the fields and indices they go through, and equal when built twice") {
                Showcase.Ledger.Fields.slots[1].point.y.name shouldBe "slots[1].point.y"
                Showcase.Ledger.Fields.slots[0].nameField[3].name shouldBe "slots[0].name[3]"
                Showcase.Ledger.Fields.grid[1][2].name shouldBe "grid[1][2]"
                Showcase.Gauge.Fields.readings[1].value shouldBe Showcase.Gauge.Fields.readings[1].value
                Showcase.Gauge.select { fields(readings[1].value, readings[1].value) }.fields.size shouldBe 1
            }

            test("an index outside a fixed array fails") {
                shouldThrow<IndexOutOfBoundsException> { Showcase.Gauge.Fields.readings[2] }
                shouldThrow<IndexOutOfBoundsException> { Showcase.Ledger.Fields.grid[0][-1] }
            }

            test("a selection of nested fields slices from the first to the end of the last, and combines them") {
                val selection = Showcase.Gauge.select { combine(reading.value, readings[1].value, aligned.x) { a, b, c -> Triple(a, b, c) } }
                selection.dataSlice shouldBe DataSlice(48, 25)
                selection.checksDiscriminator shouldBe false
                val client = CannedClient("""{"context":{"slot":7},"value":${account(gaugeBytes.copyOfRange(48, 73))}}""")
                val api = SolanaProvider(client)
                selection.fetch(api, CONFIG).send().unwrap() shouldBe Triple(6L, 10L, 12)
                dataSlice(client.params) shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"offset":48,"length":25}""")

                client.response = """{"context":{"slot":7},"value":[{"pubkey":"$CONFIG","account":${account(gaugeBytes.copyOfRange(48, 73))}}]}"""
                Showcase.Gauge.filter(api).field(Showcase.Gauge.Fields.readings[1].kind, 9).query(selection).send().unwrap().value.single().unwrap() shouldBe
                    Triple(6L, 10L, 12)
                api.close()
            }

            test("a slice of a struct or array reads the fields and elements within it") {
                val selection = Showcase.Ledger.select { fields(slots) }
                selection.dataSlice shouldBe DataSlice(17, 42)
                val client = CannedClient("""{"context":{"slot":7},"value":${account(ledgerBytes.copyOfRange(17, 59))}}""")
                val api = SolanaProvider(client)
                val slice = selection.fetch(api, CONFIG).send().unwrap()
                slice.slots shouldBe ledger.slots
                slice[Showcase.Ledger.Fields.slots[1].point.y] shouldBe 10
                slice[Showcase.Ledger.Fields.slots[0].nameField[1]] shouldBe 0x0b
                shouldThrow<IllegalArgumentException> { slice[Showcase.Ledger.Fields.grid[0][0]] }
                shouldThrow<IllegalArgumentException> { slice[Showcase.Ledger.Fields.origin.x] }
                api.close()
            }

            test("filters by nested descriptors compare at their offsets from the start of the account data") {
                val api = SolanaProvider(HttpClient("https://example.invalid"))
                Showcase.Gauge.filter(api)
                    .field(Showcase.Gauge.Fields.readings[1].value, 10)
                    .field(Showcase.Gauge.Fields.aligned.x, 12)
                    .field(Showcase.Gauge.Fields.reading, Showcase.Reading(5, 6))
                    .accountFilters.drop(1) shouldBe listOf(
                    AccountFilter.Memcmp(64, Base58.encode(hex("0a000000"))),
                    AccountFilter.Memcmp(72, Base58.encode(hex("0c"))),
                    // a struct encodes with its padding, as zeros
                    AccountFilter.Memcmp(44, Base58.encode(hex("05 000000 06000000"))),
                )
                Showcase.Ledger.filter(api)
                    .field(Showcase.Ledger.Fields.slots[1].point.y, 10)
                    .field(Showcase.Ledger.Fields.grid[1], listOf(4, 5, 6))
                    .field(Showcase.Ledger.Fields.slots[0].nameField, SolanaBytes.fromHex("0a0b0c0d"))
                    .accountFilters.drop(1) shouldBe listOf(
                    AccountFilter.Memcmp(54, Base58.encode(hex("0a000000"))),
                    AccountFilter.Memcmp(65, Base58.encode(hex("0400 0500 0600"))),
                    AccountFilter.Memcmp(17, Base58.encode(hex("0a0b0c0d"))),
                )
                // a top-level descriptor filters as the field's own filter method does
                Showcase.Gauge.filter(api).field(Showcase.Gauge.Fields.tail, 11).accountFilters shouldBe Showcase.Gauge.filter(api).tail(11).accountFilters
                // values that do not fit the field fail instead of comparing other bytes
                shouldThrow<IllegalArgumentException> { Showcase.Ledger.filter(api).field(Showcase.Ledger.Fields.grid[1], listOf(4, 5)) }
                shouldThrow<IllegalArgumentException> { Showcase.Ledger.filter(api).field(Showcase.Ledger.Fields.slots[0].nameField, SolanaBytes.fromHex("0a")) }
                api.close()
            }
        }

        test("fetch, watch and query decode accounts the program owns") {
            val counter = """{"data":["${Base64.encode(Showcase.Counter.ACCOUNT.encode(COUNTER))}","base64"],"executable":false,"lamports":1,"owner":"${Showcase.PROGRAM_ID}","rentEpoch":0}"""
            val client = CannedClient("""{"context":{"slot":7},"value":$counter}""")
            val api = SolanaProvider(client)
            Showcase.Counter.fetch(api, CONFIG).send().unwrap().data shouldBe COUNTER
            Showcase.Counter.watch(api, CONFIG).send().unwrap().receive()!!.unwrap().data shouldBe COUNTER
            Showcase.Counter.watch(api, CONFIG, programId = EXTRA).send().unwrap().receive()!!.unwrapError()
                .shouldBeInstanceOf<SolanaAccountError.Decode>().error.shouldBeInstanceOf<SolanaDecodeError.UnexpectedOwner>()
            client.response = """{"context":{"slot":7},"value":[$counter,null]}"""
            val batch = Showcase.Counter.fetch(api, listOf(CONFIG, EXTRA)).send().unwrap()
            batch[0].unwrap().data shouldBe COUNTER
            batch[1].unwrapError().shouldBeInstanceOf<SolanaAccountError.Missing>().address shouldBe EXTRA
            client.response = """{"context":{"slot":7},"value":$counter}"""
            // another deployment owns nothing here, so the same account fails its owner check
            Showcase.Counter.fetch(api, CONFIG, programId = EXTRA).send().unwrapError()
                .shouldBeInstanceOf<SolanaAccountError.Decode>().error.shouldBeInstanceOf<SolanaDecodeError.UnexpectedOwner>()

            client.response = """{"context":{"slot":7},"value":[{"pubkey":"$CONFIG","account":$counter}]}"""
            Showcase.Counter.filter(api).bump(254).query().send().unwrap().single().unwrap().data shouldBe COUNTER
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
            Showcase.INSTRUCTIONS.map { it.name } shouldBe listOf("initialize", "swap", "reset", "ping", "open_vault")
            Showcase.PING.encodeData(Unit) shouldBe hex("09")
        }
    }

    // expected addresses are derived independently of this library, from the seed bytes each IDL seed encodes to
    context("program derived addresses") {
        val owner = address(1)
        val key = address(2)
        val mint = address(3)
        val authority = address(4)
        val configAuthority = address(5)
        val maxU64 = BigInteger("18446744073709551615")
        val salt = SolanaBytes.fromHex("01020304")

        fun vault(programId: SolanaAddress = Showcase.PROGRAM_ID) = Showcase.OpenVaultAccounts.findVault(owner, maxU64, "vault-name", 0x1234, key, -5, 7, salt, programId)

        test("argument, nested argument field and account seeds are encoded by type, integers little-endian") {
            vault().address shouldBe SolanaAddress("8mnXDdNjEPaQN9Sf4su1ZJXvUVKA85nxfddwMcogBeVw")
            vault().bump shouldBe 253
        }

        test("an alternate deployment derives under its own program id") {
            vault(address(9)).address shouldBe SolanaAddress("FxExz3yiAbkHeHojY5UhdHN97JkjEtpJx1r3Nb1VuikG")
        }

        test("a seed configured as big-endian is encoded big-endian, and account data fields are parameters") {
            val position = Showcase.OpenVaultAccounts.findPosition(0x0102, configAuthority)
            position.address shouldBe SolanaAddress("43tL2cUnBvw2bWLkjvJm91rB7qzJmAE5A7gwREP33M5v")
            // the little-endian encoding of the same value derives another address
            (position.address == SolanaAddress("6vW8drMcMJZKGtZdJKwJPj9QLpEZi1DbJ3ADGr8m1pVh")) shouldBe false
        }

        test("a PDA of another program derives under that program, with no program id parameter") {
            val tokenAccount = Showcase.OpenVaultAccounts.findTokenAccount(owner, mint)
            tokenAccount shouldBe SolanaAddress.findAssociatedTokenAddress(owner, mint)
            tokenAccount.address shouldBe SolanaAddress("HwD4QpS4bsutLbWZWhbmFUZfkXC5Au1DbkzYEzjDgps8")
        }

        test("seeds whose encoding the IDL does not determine take their bytes, including fields of undeclared account types") {
            Showcase.OpenVaultAccounts.findFlagged(byteArrayOf(1)).address shouldBe SolanaAddress("7BaFZesW8K1AxYx98uAbSKVzzoNz65bC2cfpc8JuFRE6")
            val supply = ByteArray(8).also {
                it[0] = 0xe8.toByte()
                it[1] = 0x03
            }
            Showcase.OpenVaultAccounts.findMintInfo(supply).address shouldBe SolanaAddress("29KHnrQnub2g1Ci1FqBdHYyJ3BfU4yd7ede3G4qtt8DL")
        }

        test("a PDA in an account group resolves its account seeds within the group") {
            Showcase.OpenVaultAccounts.Escrow.findEscrowVault(authority).address shouldBe SolanaAddress("Dmv7vANgrva6hgvEKssdx4RB13Lsd4zdErfYh7cNTcb7")
        }

        test("derived addresses are accepted by the instruction's accounts") {
            val accounts = Showcase.OpenVaultAccounts(
                owner = owner,
                config = CONFIG,
                mint = mint,
                vault = vault().address,
                position = Showcase.OpenVaultAccounts.findPosition(0x0102, configAuthority).address,
                flagged = Showcase.OpenVaultAccounts.findFlagged(byteArrayOf(1)).address,
                mintInfo = Showcase.OpenVaultAccounts.findMintInfo(ByteArray(8)).address,
                tokenAccount = Showcase.OpenVaultAccounts.findTokenAccount(owner, mint).address,
                escrow = Showcase.OpenVaultAccounts.Escrow(authority, Showcase.OpenVaultAccounts.Escrow.findEscrowVault(authority).address),
            )
            accounts.toAccountMetas(Showcase.PROGRAM_ID)[3] shouldBe AccountMeta.writable(vault().address)
        }

        test("values outside a seed's type, and seeds over 32 bytes, are rejected") {
            shouldThrow<IllegalArgumentException> {
                Showcase.OpenVaultAccounts.findVault(owner, bigIntegerOf(-1), "vault-name", 0x1234, key, -5, 7, salt)
            }
            shouldThrow<IllegalArgumentException> {
                Showcase.OpenVaultAccounts.findVault(owner, maxU64, "a".repeat(33), 0x1234, key, -5, 7, salt)
            }
            shouldThrow<IllegalArgumentException> { Showcase.OpenVaultAccounts.findPosition(0x10000, configAuthority) }
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

/** Answers every request and subscription with [response], recording the last one's parameters, a subscription's method first. */
private class CannedClient(var response: String) : JsonRpcClient {
    var params: List<Any?> = emptyList()

    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> {
        this.params = listOf(method) + params.toList()
        return success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(response)))
    }

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> {
        this.params = params.toList()
        return success(QueueChannel.spscUnbounded<T>().apply { offer(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(response))) })
    }

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}
