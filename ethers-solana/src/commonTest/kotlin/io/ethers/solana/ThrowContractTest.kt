package io.ethers.solana

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.MemoProgram
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.rpc.DataSlice
import io.ethers.solana.types.rpc.SolanaSendConfig
import io.ethers.solana.types.rpc.SolanaSimulationConfig
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionConfig
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

    // Not data classes, so their copy can declare @Throws; these pin that they still behave like data classes.
    context("validating classes copy like data classes, throwing only the declared type") {
        test("DataSlice") {
            val slice = DataSlice(1, 2)
            slice.copy(length = 3) shouldBe DataSlice(1, 3)
            shouldThrow<IllegalArgumentException> { slice.copy(offset = -1) }
            val (offset, length) = slice
            (offset to length) shouldBe (1 to 2)
            slice.hashCode() shouldBe DataSlice(1, 2).hashCode()
            slice.toString() shouldBe "DataSlice(offset=1, length=2)"
        }

        test("SolanaSendConfig") {
            val config = SolanaSendConfig(maxRetries = 3)
            config.copy(skipPreflight = true) shouldBe SolanaSendConfig(skipPreflight = true, maxRetries = 3)
            shouldThrow<IllegalArgumentException> { config.copy(maxRetries = -1) }
            val (skip, _, retries) = config
            (skip to retries) shouldBe (false to 3)
            config.hashCode() shouldBe SolanaSendConfig(maxRetries = 3).hashCode()
            config.toString() shouldBe "SolanaSendConfig(skipPreflight=false, preflightCommitment=null, maxRetries=3, minContextSlot=null)"
        }

        test("SolanaSimulationConfig") {
            val config = SolanaSimulationConfig.READ_ONLY
            config.copy(innerInstructions = true) shouldBe SolanaSimulationConfig(replaceRecentBlockhash = true, innerInstructions = true)
            shouldThrow<IllegalArgumentException> { config.copy(sigVerify = true) }
            shouldThrow<IllegalArgumentException> { config.copy(accounts = List(256) { recipient }) }
            config.component3() shouldBe true
            config.hashCode() shouldBe SolanaSimulationConfig(replaceRecentBlockhash = true).hashCode()
        }

        test("SolanaTransactionConfig") {
            val config = SolanaTransactionConfig(computeUnitLimit = 10)
            config.copy(heapSize = 32768) shouldBe SolanaTransactionConfig(computeUnitLimit = 10, heapSize = 32768)
            shouldThrow<IllegalArgumentException> { config.copy(computeUnitLimit = -1) }
            config.component2() shouldBe 10L
            config.hashCode() shouldBe SolanaTransactionConfig(computeUnitLimit = 10).hashCode()
        }

        test("AddressLookupTableAccount") {
            val table = AddressLookupTableAccount(recipient, listOf(payer.publicKey))
            table.copy(addresses = emptyList()) shouldBe AddressLookupTableAccount(recipient, emptyList())
            shouldThrow<IllegalArgumentException> { table.copy(addresses = List(257) { recipient }) }
            val (key, addresses) = table
            (key to addresses) shouldBe (recipient to listOf(payer.publicKey))
            table.hashCode() shouldBe AddressLookupTableAccount(recipient, listOf(payer.publicKey)).hashCode()
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
