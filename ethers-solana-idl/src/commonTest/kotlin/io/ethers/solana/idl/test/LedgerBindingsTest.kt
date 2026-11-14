package io.ethers.solana.idl.test

import io.ethers.core.FastHex
import io.ethers.crypto.Base58
import io.ethers.providers.HttpClient
import io.ethers.solana.codec.BorshReader
import io.ethers.solana.codec.SolanaAccountField
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaEventContext
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

private fun address(byte: Int) = SolanaAddress(ByteArray(32) { byte.toByte() })

private val TOKEN_PROGRAM = SolanaAddress("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")

/** The ledger IDL's bindings, generated with the overrides in src/commonTest/overrides.json. */
class LedgerBindingsTest : FunSpec({
    context("custom codecs") {
        test("a type declared with custom serialization reads and writes through its handwritten codec") {
            // LEB128: 300 is 0xac 0x02
            Ledger.Compressed.encode(Ledger.Compressed(300)) shouldBe hex("ac02")
            Ledger.Compressed.decode(hex("ac02")).unwrap() shouldBe Ledger.Compressed(300)
            Ledger.Stamp.encode(Ledger.Stamp(0x01020304)) shouldBe hex("01020304")
        }

        test("an enum with a custom codec keeps its generated variants, renamed") {
            Ledger.Side.encode(Ledger.Side.Bid) shouldBe hex("00000000")
            Ledger.Side.encode(Ledger.Side.Sell(maxPrice = 7)) shouldBe hex("01000000 07")
            Ledger.Side.decode(hex("01000000 07")).unwrap() shouldBe Ledger.Side.Sell(7)
            Ledger.Side.decode(hex("02000000")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe
                SolanaDecodeError.Reason.INVALID_VARIANT
        }

        test("an account with a custom codec decodes through it, after the discriminator") {
            val record = Ledger.TaggedRecord(tag = 5, label = "hi")
            // the codec writes the label before the tag
            val data = hex("0c 02000000 6869 05")
            Ledger.TaggedRecord.ACCOUNT.encode(record) shouldBe data
            Ledger.TaggedRecord.ACCOUNT.decode(data).unwrap() shouldBe record
            Ledger.decodeAccount(data).unwrap() shouldBe record
        }

        test("an account with a custom codec gets no field filters, since its layout is the codec's") {
            val api = SolanaProvider(HttpClient("https://example.invalid"))
            Ledger.TaggedRecord.filter(api).accountFilters shouldBe listOf(AccountFilter.Memcmp(0, Base58.encode(hex("0c"))))
            api.close()
        }
    }

    context("type substitutions") {
        test("substituted types take the existing Kotlin type and codec wherever the IDL uses them") {
            val state = Ledger.LedgerState(
                authority = address(1),
                stamp = Ledger.Stamp(2),
                window = Ledger.Window(Ledger.Stamp(4), 5),
                stamps = listOf(Ledger.Stamp(6), Ledger.Stamp(7)),
                packed = Ledger.Compressed(1),
                price = PythPrice(mantissa = -5, expo = -2),
                total = TokenAmount(9),
            )
            val payload = "01".repeat(32) + "00000002" + "00000004 0500" + "00000006 00000007" + "01" + "fbffffffffffffff feffffff" + "0900000000000000"
            Ledger.LedgerState.encode(state) shouldBe hex(payload)
            Ledger.LedgerState.ACCOUNT.decode(hex("0a $payload")).unwrap() shouldBe state
        }

        test("a substitution with a declared size and align follows a zero-copy C layout") {
            val pool = Ledger.Pool(flag = 1, liquidity = TokenAmount(2), stamp = Ledger.Stamp(3))
            // u8, 7 bytes to the 8-aligned amount, the 4-byte stamp, then 4 bytes to a size of 24
            val payload = "01 00000000000000 0200000000000000 00000003 00000000"
            Ledger.Pool.encode(pool) shouldBe hex(payload)
            Ledger.Pool.decode(hex(payload)).unwrap() shouldBe pool
            Ledger.Pool.Fields.liquidity.offset shouldBe 8
            Ledger.Pool.Fields.stamp.offset shouldBe 16
            Ledger.Pool.Fields.stamp.size shouldBe 4
        }
    }

    context("offsets") {
        test("offsets continue past an opaque type of declared size and stop after one of variable size") {
            Ledger.LedgerState.Fields.authority.offset shouldBe 0
            Ledger.LedgerState.Fields.stamp.offset shouldBe 32
            val api = SolanaProvider(HttpClient("https://example.invalid"))
            // the varint starts at a known offset, but nothing after it has one: there is no price or total filter
            Ledger.LedgerState.filter(api).authority(address(1)).stamp(Ledger.Stamp(2)).packed(Ledger.Compressed(300)).accountFilters shouldBe listOf(
                AccountFilter.Memcmp(0, Base58.encode(hex("0a"))),
                // the discriminator is a single byte
                AccountFilter.Memcmp(1, address(1).toBase58()),
                AccountFilter.Memcmp(33, Base58.encode(hex("00000002"))),
                AccountFilter.Memcmp(51, Base58.encode(hex("ac02"))),
            )
            api.close()
        }

        test("paths describe an opaque type of declared size as a whole, and take renamed field names") {
            val window = Ledger.LedgerState.Fields.window
            window.offset shouldBe 36
            window.size shouldBe 6
            // the opaque Stamp gets a plain descriptor, read through its codec, rather than a path into its IDL fields
            val start: SolanaAccountField<Ledger.LedgerState, Ledger.Stamp> = window.start
            start.offset shouldBe 36
            start.size shouldBe 4
            start.read(BorshReader(hex("00000009"))) shouldBe Ledger.Stamp(9)
            // `length` is renamed `size`, which a descriptor member takes, so its descriptor is `sizeField`
            window.sizeField.offset shouldBe 40
            window.sizeField.name shouldBe "window.size"
            // an array of a sized opaque type describes each element
            Ledger.LedgerState.Fields.stamps[1].offset shouldBe 46
            Ledger.LedgerState.Fields.stamps[1].read(BorshReader(hex("00000003"))) shouldBe Ledger.Stamp(3)
        }
    }

    context("constants") {
        test("a composite constant of a type with a custom codec is constructed from the IDL value, under its renamed name and fields") {
            Ledger.FIRST_WINDOW shouldBe Ledger.Window(start = Ledger.Stamp(7), size = 3)
        }
    }

    context("renames and account defaults") {
        val accounts = Ledger.RecordEntryAccounts(
            ledgerState = address(1),
            authority = address(2),
            parties = Ledger.RecordEntryAccounts.Parties(makerAccount = address(3)),
        )
        val args = Ledger.RecordEntryArgs(
            quantity = TokenAmount(10),
            price = PythPrice(1, 0),
            side = Ledger.Side.Sell(3),
            packed = Ledger.Compressed(1),
        )

        test("instruction accounts default to the configured addresses, including in groups and when optional") {
            accounts.tokenProgram shouldBe TOKEN_PROGRAM
            accounts.parties.taker shouldBe Ledger.PROGRAM_ID
            accounts.referrer shouldBe SolanaAddress("11111111111111111111111111111111")
            accounts.toAccountMetas(Ledger.PROGRAM_ID) shouldBe listOf(
                AccountMeta(address(1), false, true),
                AccountMeta(address(2), true, false),
                AccountMeta(TOKEN_PROGRAM, false, false),
                AccountMeta(address(3), false, true),
                AccountMeta(Ledger.PROGRAM_ID, false, false),
                AccountMeta(SolanaAddress("11111111111111111111111111111111"), false, false),
            )
            // an optional account with a default can still be left out
            accounts.copy(referrer = null).toAccountMetas(Ledger.PROGRAM_ID).last() shouldBe AccountMeta(Ledger.PROGRAM_ID)
        }

        test("a renamed instruction builds and decodes under its new names, keeping its IDL name on the wire") {
            val instruction = Ledger.recordEntry(accounts, args)
            Ledger.RECORD_ENTRY.name shouldBe "record"
            val decoded = Ledger.decodeInstruction(instruction).unwrap().shouldBeInstanceOf<Ledger.Instruction.RecordEntry>()
            decoded.accounts shouldBe accounts
            decoded.args shouldBe args
            Ledger.RECORD_ENTRY.decodeReturn(hex("ac02")).unwrap() shouldBe Ledger.Compressed(300)
        }

        test("renamed events, errors and constants keep their IDL names where the program reports them") {
            val context = SolanaEventContext(Ledger.PROGRAM_ID)
            val event = Ledger.Event.EntryRecorded(quantity = TokenAmount(4), side = Ledger.Side.Bid, context = context)
            val data = Ledger.Event.EntryRecorded.encode(event)
            data shouldBe hex("14 0400000000000000 00000000")
            Ledger.decodeEvent(data, context).unwrap() shouldBe event
            Ledger.Event.EntryRecorded.name shouldBe "Recorded"
            Ledger.decodeError(6000) shouldBe Ledger.Error.TooLarge
            Ledger.Error.TooLarge.name shouldBe "Overflow"
            Ledger.MAX_ENTRIES shouldBe 10
        }
    }
})
