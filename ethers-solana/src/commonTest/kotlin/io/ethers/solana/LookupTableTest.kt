package io.ethers.solana

import io.ethers.core.isFailure
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.utils.U64_MAX
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random

class LookupTableTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })
    fun address(seed: Int) = SolanaAddress(ByteArray(32) { seed.toByte() })

    fun request(accounts: List<SolanaAddress>) = SolanaTransactionRequest {
        feePayer(alice.publicKey)
        blockhash(blockhash)
        instruction(Instruction(Programs.SYSTEM, accounts.map { AccountMeta.writable(it) }, byteArrayOf(1)))
    }

    test("a table is used once it covers two movable accounts, not one") {
        val accounts = List(2) { address(it + 10) }
        val covering1 = AddressLookupTableAccount(address(90), listOf(accounts[0]))
        val covering2 = AddressLookupTableAccount(address(91), accounts)

        // one account: naming the table would cost three bytes more than it saves, so it is skipped
        val skipped = request(accounts).compileV0(listOf(covering1))
        skipped.addressLookupTables shouldBe emptyList()
        skipped.envelopeSize() shouldBe request(accounts).compileV0().envelopeSize()

        val used = request(accounts).compileV0(listOf(covering2))
        used.addressLookupTables.single().writableIndexes shouldBe listOf(0, 1)
        (used.envelopeSize() < skipped.envelopeSize()) shouldBe true
    }

    test("overlapping tables concentrate into as few as possible") {
        val accounts = List(4) { address(it + 10) }
        val partial = AddressLookupTableAccount(address(90), accounts.take(2))
        val full = AddressLookupTableAccount(address(91), accounts)

        val tx = request(accounts).compileV0(listOf(partial, full))
        // the wider table covers everything, so the narrower one is never named
        tx.addressLookupTables.map { it.key } shouldBe listOf(full.key)
        tx.addressLookupTables.single().writableIndexes.size shouldBe 4
    }

    test("selection is deterministic and follows the caller's ordering on ties") {
        val accounts = List(2) { address(it + 10) }
        val first = AddressLookupTableAccount(address(90), accounts)
        val second = AddressLookupTableAccount(address(91), accounts)

        request(accounts).compileV0(listOf(first, second)).addressLookupTables.single().key shouldBe first.key
        request(accounts).compileV0(listOf(second, first)).addressLookupTables.single().key shouldBe second.key
        repeat(5) { request(accounts).compileV0(listOf(first, second)).serializeMessage() shouldBe request(accounts).compileV0(listOf(first, second)).serializeMessage() }
    }

    test("a transaction too large for legacy fits as v0 once its accounts move into a table") {
        val accounts = List(36) { address(it + 10) }
        val oversized = request(accounts)
        oversized.tryCompileLegacy().unwrapError().shouldBeInstanceOf<SolanaTransactionError.EnvelopeTooLarge>()
        // without a table v0 is no better, since the accounts still sit inline
        oversized.tryCompileV0().isFailure() shouldBe true

        val table = AddressLookupTableAccount(address(90), accounts)
        val fitted = oversized.compileV0(listOf(table))
        (fitted.envelopeSize() <= SolanaTxLegacy.MAX_TRANSACTION_SIZE) shouldBe true
        fitted.accounts shouldBe listOf(alice.publicKey, Programs.SYSTEM)
    }

    test("supplying tables never produces a larger transaction than supplying none") {
        val random = Random(11)
        repeat(50) {
            val accounts = List(random.nextInt(1, 12)) { address(it + 10) }
            val tables = List(random.nextInt(0, 4)) { table ->
                AddressLookupTableAccount(address(90 + table), accounts.shuffled(random).take(random.nextInt(0, accounts.size + 1)))
            }
            val withTables = request(accounts).compileV0(tables).envelopeSize()
            val without = request(accounts).compileV0().envelopeSize()
            (withTables <= without) shouldBe true
        }
    }

    test("an on-chain lookup table account decodes into its addresses, authority and deactivation slot") {
        val key = address(90)
        val addresses = List(3) { address(it + 10) }
        fun encode(discriminant: Int, deactivation: ByteArray, authority: SolanaAddress?): ByteArray {
            val header = byteArrayOf(discriminant.toByte(), 0, 0, 0) + deactivation + ByteArray(9) +
                (if (authority == null) ByteArray(33) else byteArrayOf(1) + authority.asByteArray()) + ByteArray(2)
            return header + addresses.fold(ByteArray(0)) { acc, it -> acc + it.asByteArray() }
        }

        val never = ByteArray(8) { -1 } // u64::MAX
        val decoded = AddressLookupTableAccount.decode(key, encode(1, never, alice.publicKey))
        decoded.addresses shouldBe addresses
        decoded.authority shouldBe alice.publicKey
        decoded.deactivationSlot shouldBe null

        val deactivated = AddressLookupTableAccount.decode(key, encode(1, byteArrayOf(7, 0, 0, 0, 0, 0, 0, 0), null))
        deactivated.deactivationSlot shouldBe bigIntegerOf(7)
        deactivated.authority shouldBe null
        U64_MAX shouldBe io.github.artificialpb.bignum.BigInteger("18446744073709551615")

        AddressLookupTableAccount.tryDecode(key, encode(2, never, null)).unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
        AddressLookupTableAccount.tryDecode(key, encode(1, never, null).dropLast(1).toByteArray()).unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
        AddressLookupTableAccount.tryDecode(key, ByteArray(10)).unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()

        // a decoded table compiles like a hand-built one
        val built = AddressLookupTableAccount(key, addresses)
        request(addresses.take(2)).compileV0(listOf(decoded)).serializeMessage() shouldBe
            request(addresses.take(2)).compileV0(listOf(built)).serializeMessage()
    }
})
