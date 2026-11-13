package io.ethers.solana.idl.test

import io.ethers.core.FastHex
import io.ethers.crypto.Base58
import io.ethers.providers.HttpClient
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaEventContext
import io.ethers.solana.program.decodeReturn
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

private fun address(byte: Int) = SolanaAddress(ByteArray(32) { byte.toByte() })

private fun repeated(byte: String) = byte.repeat(32)

private val VAULT = GenericVault.Vault(
    bump = 254,
    label = GenericVault.LabelN8(SolanaBytes.fromHex("6d61696e00000000")),
    balances = GenericVault.PairU64I32(bigIntegerOf(1000), -2),
    history = GenericVault.RingBufferU64N3(1, listOf(bigIntegerOf(1), bigIntegerOf(2), bigIntegerOf(3))),
    slot = GenericVault.SlotPairU64U8.Locked(address(3), GenericVault.PairU64U8(bigIntegerOf(5), 6)),
    window = GenericVault.WindowU16N2(GenericVault.RingBufferU16N2(0, listOf(7, 8)), 9),
)

// bump, label: [u8; 8], balances: (u64, i32), history: (u8, [u64; 3]), slot: Locked(pubkey, (u64, u8)),
// window: ((u8, [u16; 2]), Option<u16>)
private val VAULT_PAYLOAD = "fe 6d61696e00000000 e803000000000000 feffffff 01 0100000000000000 0200000000000000 0300000000000000 " +
    "02 ${repeated("03")} 0500000000000000 06 00 0700 0800 01 0900"

private val ORACLE = GenericVault.Oracle(
    authority = address(1),
    ticks = GenericVault.TicksN4(listOf(bigIntegerOf(1), bigIntegerOf(2), bigIntegerOf(3), bigIntegerOf(4)), 4),
    updated = -1,
)

// authority, then Ticks<4> at 32, 8-byte aligned: [u64; 4], count, 7 bytes of trailing padding; then updated at 72
private val ORACLE_PAYLOAD = "${repeated("01")} 0100000000000000 0200000000000000 0300000000000000 0400000000000000 04 00000000000000 ffffffffffffffff"

class GenericVaultBindingsTest : FunSpec({
    context("instantiations") {
        test("each instantiation is a class named after its definition and arguments, with a concrete codec") {
            GenericVault.PairU64I32.encode(GenericVault.PairU64I32(bigIntegerOf(1), -1)) shouldBe hex("0100000000000000 ffffffff")
            GenericVault.PairU8U8.encode(GenericVault.PairU8U8(1, 2)) shouldBe hex("01 02")
            // a const argument fixes the array length
            GenericVault.LabelN4.encode(GenericVault.LabelN4(SolanaBytes.fromHex("61626364"))) shouldBe hex("61626364")
            GenericVault.RingBufferU16N2.encode(GenericVault.RingBufferU16N2(1, listOf(2, 3))) shouldBe hex("01 0200 0300")
        }

        test("a fixed array of the wrong length is rejected when encoding") {
            shouldThrow<IllegalArgumentException> { GenericVault.RingBufferU16N2.encode(GenericVault.RingBufferU16N2(1, listOf(2))) }.message shouldBe
                "Expected 2 elements, got 1"
            shouldThrow<IllegalArgumentException> { GenericVault.LabelN4.encode(GenericVault.LabelN4(SolanaBytes.fromHex("6162"))) }.message shouldBe
                "Expected 4 bytes, got 2"
        }

        test("generic enums have a variant per declared variant, with the type argument substituted") {
            GenericVault.SlotU64.encode(GenericVault.SlotU64.Empty) shouldBe hex("00")
            GenericVault.SlotU64.encode(GenericVault.SlotU64.Filled(bigIntegerOf(2))) shouldBe hex("01 0200000000000000")
            GenericVault.SlotU32.encode(GenericVault.SlotU32.Locked(address(2), 7)) shouldBe hex("02 ${repeated("02")} 07000000")
            GenericVault.SlotU64.decode(hex("03")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe
                SolanaDecodeError.Reason.INVALID_VARIANT
        }

        test("a generic within a generic, and generics within options, vecs and arrays, round-trip") {
            val nested = GenericVault.SlotPairU64U8.Filled(GenericVault.PairU64U8(bigIntegerOf(5), 6))
            GenericVault.SlotPairU64U8.encode(nested) shouldBe hex("01 0500000000000000 06")
            GenericVault.SlotPairU64U8.decode(hex("01 0500000000000000 06")).unwrap() shouldBe nested

            val holder = GenericVault.HolderU32(null, listOf(5), listOf(GenericVault.PairU32U8(6, 7), GenericVault.PairU32U8(8, 9)))
            val holderBytes = hex("00 01000000 05000000 06000000 07 08000000 09")
            GenericVault.HolderU32.encode(holder) shouldBe holderBytes
            GenericVault.HolderU32.decode(holderBytes).unwrap() shouldBe holder
            val some = holder.copy(maybe = 4294967295L)
            GenericVault.HolderU32.decode(GenericVault.HolderU32.encode(some)).unwrap() shouldBe some

            // the const parameter of Window is passed on to RingBuffer
            val window = GenericVault.WindowU16N2(GenericVault.RingBufferU16N2(1, listOf(2, 3)), null)
            GenericVault.WindowU16N2.encode(window) shouldBe hex("01 0200 0300 00")
        }
    }

    context("accounts") {
        test("an account with generic fields round-trips through its factory") {
            val data = hex("d308e82b02987577 $VAULT_PAYLOAD")
            GenericVault.Vault.ACCOUNT.encode(VAULT) shouldBe data
            GenericVault.Vault.ACCOUNT.decode(data + ByteArray(8)).unwrap() shouldBe VAULT
            GenericVault.decodeAccount(data).unwrap() shouldBe VAULT
        }

        test("fields of instantiations with a known const argument have a fixed offset and size") {
            GenericVault.Vault.Fields.label.offset shouldBe 1
            GenericVault.Vault.Fields.label.size shouldBe 8
            GenericVault.Vault.Fields.balances.offset shouldBe 9
            GenericVault.Vault.Fields.balances.size shouldBe 12
            GenericVault.Vault.Fields.history.offset shouldBe 21
            GenericVault.Vault.Fields.history.size shouldBe 25

            val api = SolanaProvider(HttpClient("https://example.invalid"))
            val history = GenericVault.RingBufferU64N3(0, List(3) { bigIntegerOf(it) })
            GenericVault.Vault.filter(api).balances(VAULT.balances).history(history).accountFilters.drop(1) shouldBe listOf(
                AccountFilter.Memcmp(17, Base58.encode(hex("e803000000000000 feffffff"))),
                AccountFilter.Memcmp(29, Base58.encode(hex("00 0000000000000000 0100000000000000 0200000000000000"))),
            )
            api.close()
        }

        test("a zero-copy account lays out a zero-copy instantiation with its const length and padding") {
            val data = hex("8bc283b38cb3e5f4 $ORACLE_PAYLOAD")
            GenericVault.Oracle.ACCOUNT.encode(ORACLE) shouldBe data
            GenericVault.Oracle.ACCOUNT.decode(data).unwrap() shouldBe ORACLE
            GenericVault.Oracle.encode(ORACLE).size shouldBe 80
            GenericVault.Oracle.Fields.ticks.offset shouldBe 32
            GenericVault.Oracle.Fields.ticks.size shouldBe 40
            GenericVault.Oracle.Fields.updated.offset shouldBe 72
        }
    }

    context("instructions") {
        val accounts = GenericVault.DepositAccounts(address(1), address(2))
        val args = GenericVault.DepositArgs(
            amount = GenericVault.PairU64I32(bigIntegerOf(1000), -2),
            ring = GenericVault.RingBufferU16N2(1, listOf(2, 3)),
            hint = GenericVault.SlotU32.Filled(7),
            batches = listOf(listOf(GenericVault.PairU8U8(1, 2), GenericVault.PairU8U8(3, 4))),
            holder = GenericVault.HolderU32(null, listOf(5), listOf(GenericVault.PairU32U8(6, 7), GenericVault.PairU32U8(8, 9))),
        )
        // amount, ring through the generic alias Bounded<u16>, hint: Some(Filled(7)), batches: one [Pair<u8, u8>; 2], holder
        val data = "f223c68952e1f2b6 e803000000000000 feffffff 01 0200 0300 01 01 07000000 01000000 01 02 03 04 " +
            "00 01000000 05000000 06000000 07 08000000 09"

        test("instruction arguments of generic types encode and decode") {
            val instruction = GenericVault.deposit(accounts, args)
            instruction.keys shouldBe listOf(AccountMeta.writable(address(1)), AccountMeta.signerAndWritable(address(2)))
            instruction.data shouldBe SolanaBytes.fromHex(data.replace(" ", ""))
            val decoded = GenericVault.decodeInstruction(instruction).unwrap().shouldBeInstanceOf<GenericVault.Instruction.Deposit>()
            decoded.args shouldBe args
            decoded.accounts shouldBe accounts

            val labels = listOf(GenericVault.LabelN4(SolanaBytes.fromHex("61626364")), GenericVault.LabelN4(SolanaBytes.fromHex("65666768")))
            GenericVault.relabel(GenericVault.RelabelAccounts(address(1)), GenericVault.RelabelArgs(labels)).data shouldBe
                SolanaBytes.fromHex("809dce92f54708e9 61626364 65666768".replace(" ", ""))
        }

        test("a generic return type decodes as its instantiation") {
            GenericVault.DEPOSIT.decodeReturn(hex("02 ${repeated("03")} 2a00000000000000")).unwrap() shouldBe
                GenericVault.SlotU64.Locked(address(3), bigIntegerOf(42))
        }

        test("a seed reaching into a generic argument takes the substituted field type") {
            val first = BigInteger("18446744073709551615")
            GenericVault.DepositAccounts.findVault(first) shouldBe
                SolanaAddress.findProgramAddress(listOf("vault".encodeToByteArray(), hex("ffffffffffffffff")), GenericVault.PROGRAM_ID)
        }
    }

    test("events with generic fields decode with their context") {
        val context = SolanaEventContext(GenericVault.PROGRAM_ID)
        val event = GenericVault.Event.Deposited(
            address(1),
            GenericVault.PairU64U64(bigIntegerOf(1), bigIntegerOf(2)),
            listOf(GenericVault.LabelN4(SolanaBytes.fromHex("61626364"))),
            context,
        )
        val data = hex("6f8d1a2da1236439 ${repeated("01")} 0100000000000000 0200000000000000 01000000 61626364")
        GenericVault.Event.Deposited.encode(event) shouldBe data
        GenericVault.decodeEvent(data, context).unwrap() shouldBe event
    }
})
