package io.ethers.solana

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.MemoProgram
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.utils.SolUnit
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Regressions for paths that threw where their contract says they do not, or threw a type their `@Throws` does not
 * name. Either one aborts a Swift caller, and the first also breaks a JVM caller relying on the returned `Result`.
 */
class ThrowContractTest : FunSpec({
    val payer = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val recipient = KeypairSigner.fromSeed(ByteArray(32) { 2 }).publicKey
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })
    val transfer = SystemProgram.transfer(payer.publicKey, recipient, 1L)
    val tx = SolanaTxLegacy.compile(payer.publicKey, blockhash, transfer).unwrap()

    context("functions returning a Result fail instead of throwing") {
        test("a fully signed envelope with a bad signature") {
            val envelope = tx.sign(payer).serialize()
            envelope[1] = (envelope[1] + 1).toByte() // first signature byte, still non-zero
            val error = SolanaTransactionCompiled.deserialize(envelope).unwrapError()
            error.shouldBeInstanceOf<SolanaTransactionError.InvalidSignature>()
        }

        test("instruction data too long for a shortvec length") {
            val huge = Instruction(recipient, emptyList(), ByteArray(70_000))
            SolanaTxLegacy.compile(payer.publicKey, blockhash, huge).unwrapError().shouldBeInstanceOf<SolanaTransactionError.EnvelopeTooLarge>()
            SolanaTxV0.compile(payer.publicKey, blockhash, listOf(huge)).unwrapError().shouldBeInstanceOf<SolanaTransactionError.EnvelopeTooLarge>()
        }

        test("more lookup tables than a shortvec length holds") {
            val compiled = SolanaTxV0.compile(payer.publicKey, blockhash, listOf(transfer)).unwrap()
            val tables = List(70_000) { CompiledAddressLookupTable(recipient, emptyList(), emptyList()) }
            val error = SolanaTxV0.create(compiled.header, compiled.accounts, blockhash, compiled.instructions, tables).unwrapError()
            error.shouldBeInstanceOf<SolanaTransactionError.EnvelopeTooLarge>()
        }

        test("a priority fee whose per-unit price overflows u64") {
            val request = SolanaTransactionRequest().feePayer(payer.publicKey).blockhash(blockhash).instruction(transfer)
                .computeUnitLimit(1L)
                .priorityFee(BigInteger("20000000000000"))
            for (result in listOf(request.compileLegacy(), request.compileV0(), request.compile())) {
                val error = result.unwrapError()
                error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
                error.reason shouldBe SolanaTransactionError.Reason.CONFIG
            }
        }

        test("a v1 priority fee derived from limit and price that overflows u64") {
            val request = SolanaTransactionRequest().feePayer(payer.publicKey).blockhash(blockhash).instruction(transfer)
                .computeUnitLimit(4_000_000_000L)
                .computeUnitPrice(BigInteger("10000000000000000"))
            val error = request.compileV1().unwrapError()
            error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
            error.reason shouldBe SolanaTransactionError.Reason.CONFIG
        }

        test("a lookup table account holding more than 256 addresses") {
            val data = ByteArray(AddressLookupTableAccount.META_SIZE + 32 * 257).also { it[0] = 1 }
            val error = AddressLookupTableAccount.decode(recipient, data).unwrapError()
            error.shouldBeInstanceOf<SolanaTransactionError.CountOutOfRange>()
            error.limit shouldBe SolanaTransactionError.Limit.LOOKUP_TABLE
        }
    }

    context("throwing functions throw only the declared, unchecked type") {
        test("an envelope with the wrong number of signature slots") {
            shouldThrow<IllegalArgumentException> { tx.encodeEnvelope(emptyList()) }
            shouldThrow<IllegalArgumentException> { tx.encodeEnvelope(List(2) { null }) }
            tx.encodeEnvelope(listOf(null)).size.toLong() shouldBe tx.envelopeSize()
        }

        test("text with an unpaired surrogate, which has no UTF-8 encoding") {
            shouldThrow<IllegalArgumentException> { MemoProgram.memo("bad \uD800 text") }
            shouldThrow<IllegalArgumentException> { SolanaAddress.createWithSeed(payer.publicKey, "\uDC00", recipient) }
            shouldThrow<IllegalArgumentException> {
                SystemProgram.transferWithSeed(payer.publicKey, payer.publicKey, "\uD800", recipient, recipient, 1L)
            }
        }

        test("unit conversions of unparseable or non-finite amounts") {
            shouldThrow<IllegalArgumentException> { SolUnit.SOL.toLamports("not a number") }
            shouldThrow<IllegalArgumentException> { SolUnit.SOL.toLamports(Double.NaN) }
            shouldThrow<IllegalArgumentException> { SolUnit.SOL.toLamports(Double.POSITIVE_INFINITY) }
            SolUnit.SOL.toLamports(1.5) shouldBe SolUnit.SOL.toLamports("1.5")
        }
    }
})
