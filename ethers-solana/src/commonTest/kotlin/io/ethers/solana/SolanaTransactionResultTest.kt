package io.ethers.solana

import io.ethers.core.isSuccess
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionException
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SolanaTransactionResultTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })
    val config = SolanaTransactionConfig()
    val transfer = SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L)

    test("tryCompile succeeds with the same payload the throwing overload builds") {
        SolanaTxLegacy.tryCompile(alice.publicKey, blockhash, transfer).unwrap().serializeMessage() shouldBe
            SolanaTxLegacy.compile(alice.publicKey, blockhash, transfer).serializeMessage()
        SolanaTxV0.tryCompile(alice.publicKey, blockhash, listOf(transfer)).unwrap().serializeMessage() shouldBe
            SolanaTxV0.compile(alice.publicKey, blockhash, listOf(transfer)).serializeMessage()
        SolanaTxV1.tryCompile(alice.publicKey, blockhash, transfer, config).unwrap().serializeMessage() shouldBe
            SolanaTxV1.compile(alice.publicKey, blockhash, transfer, config).serializeMessage()
        SolanaTxLegacy.tryCompile(alice.publicKey, blockhash, transfer).isSuccess() shouldBe true
    }

    test("an oversized envelope reports its size and the version limit") {
        val big = List(40) { Instruction(Programs.SYSTEM, listOf(AccountMeta.writable(bob.publicKey)), ByteArray(60)) }
        val legacy = SolanaTxLegacy.tryCompile(alice.publicKey, blockhash, big).unwrapError()
        legacy.shouldBeInstanceOf<SolanaTransactionError.EnvelopeTooLarge>()
        legacy.type shouldBe SolanaTxType.Legacy
        legacy.max shouldBe SolanaTxLegacy.MAX_TRANSACTION_SIZE
        (legacy.size > SolanaTxLegacy.MAX_TRANSACTION_SIZE) shouldBe true

        // the same instructions fit in v1, which allows a larger envelope
        SolanaTxV1.tryCompile(alice.publicKey, blockhash, big, config).isSuccess() shouldBe true
    }

    test("v1 limits are reported as their own cases") {
        val many = List(65) { Instruction(Programs.SYSTEM, emptyList(), byteArrayOf(1)) }
        SolanaTxV1.tryCompile(alice.publicKey, blockhash, many, config).unwrapError() shouldBe
            SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.INSTRUCTIONS, 65, 0..64)

        val heap = SolanaTxV1.tryCompile(alice.publicKey, blockhash, transfer, SolanaTransactionConfig(heapSize = 1000)).unwrapError()
        heap.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        heap.reason shouldBe SolanaTransactionError.Reason.CONFIG
    }

    test("structural failures name the offending account or index") {
        val compiled = SolanaTxLegacy.compile(alice.publicKey, blockhash, transfer)
        val duplicate = shouldThrow<SolanaTransactionException> {
            SolanaTxLegacy(compiled.header, compiled.accounts + compiled.accounts.first(), blockhash, compiled.instructions)
        }.error
        duplicate.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        duplicate.reason shouldBe SolanaTransactionError.Reason.DUPLICATE_ACCOUNT

        val outOfRange = SolanaTxV0.tryCompile(alice.publicKey, blockhash, transfer).unwrap()
        val error = shouldThrow<SolanaTransactionException> {
            SolanaTxV0(outOfRange.header, outOfRange.accounts, blockhash, listOf(CompiledInstruction(1, listOf(9), byteArrayOf())))
        }.error
        error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        error.reason shouldBe SolanaTransactionError.Reason.ACCOUNT_INDEX
    }

    test("the throwing path stays an IllegalArgumentException that carries the typed error") {
        val thrown = shouldThrow<IllegalArgumentException> {
            SolanaTxLegacy(MessageHeader(1, 0, 0), listOf(alice.publicKey, alice.publicKey), blockhash, emptyList())
        }
        thrown.shouldBeInstanceOf<SolanaTransactionException>()
        val error = thrown.error
        error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        error.reason shouldBe SolanaTransactionError.Reason.DUPLICATE_ACCOUNT
        thrown.message shouldBe error.message
    }

    test("decoding reports truncation, unsupported versions and partial signatures") {
        val signed = SolanaTxLegacy.compile(alice.publicKey, blockhash, transfer).sign(alice)
        val wire = signed.serialize()
        SolanaTransactionSigned.tryDeserialize(wire).unwrap().serialize() shouldBe wire
        SolanaTransaction.tryDeserialize(wire).isSuccess() shouldBe true
        SolanaTransactionSigned.tryFromBase64(signed.toBase64()).unwrap().serialize() shouldBe wire

        SolanaTransactionSigned.tryDeserialize(wire.copyOf(wire.size - 1)).unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
        SolanaTransactionUnsigned.tryDeserializeMessage(byteArrayOf()).unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()

        val unsupported = SolanaTransactionUnsigned.tryDeserializeMessage(byteArrayOf(200.toByte(), 0, 0)).unwrapError()
        unsupported shouldBe SolanaTransactionError.UnsupportedVersion(200)

        val twoSigners = SolanaTxLegacy.compile(
            alice.publicKey,
            blockhash,
            Instruction(Programs.SYSTEM, listOf(AccountMeta.signer(alice.publicKey), AccountMeta.signer(bob.publicKey)), byteArrayOf(7)),
        )
        val partial = twoSigners.signingBuilder().sign(alice).serializePartial()
        SolanaTransaction.tryDeserialize(partial).unwrapError() shouldBe SolanaTransactionError.PartiallySigned(1, 2)
        SolanaTransactionSigned.Builder.tryDeserializePartial(partial).unwrap().missingSigners shouldBe listOf(bob.publicKey)
    }

    test("v1 signatures in a signatures-first envelope are rejected by name") {
        val v1 = SolanaTxV1.compile(alice.publicKey, blockhash, transfer, config)
        val message = v1.serializeMessage()
        val envelope = byteArrayOf(1) + ByteArray(64) + message
        SolanaTransactionSigned.tryDeserialize(envelope).unwrapError() shouldBe
            SolanaTransactionError.MalformedBytes("V1 signatures must follow the message")
    }

    test("the throwing and safe paths report the identical error value") {
        val big = List(40) { Instruction(Programs.SYSTEM, listOf(AccountMeta.writable(bob.publicKey)), ByteArray(60)) }
        shouldThrow<SolanaTransactionException> { SolanaTxLegacy.compile(alice.publicKey, blockhash, big) }.error shouldBe
            SolanaTxLegacy.tryCompile(alice.publicKey, blockhash, big).unwrapError()

        val heap = SolanaTransactionConfig(heapSize = 1000)
        shouldThrow<SolanaTransactionException> { SolanaTxV1.compile(alice.publicKey, blockhash, transfer, heap) }.error shouldBe
            SolanaTxV1.tryCompile(alice.publicKey, blockhash, transfer, heap).unwrapError()
    }

    test("construction failures carry no exception cause, unlike decoding failures") {
        val big = List(40) { Instruction(Programs.SYSTEM, listOf(AccountMeta.writable(bob.publicKey)), ByteArray(60)) }
        SolanaTxLegacy.tryCompile(alice.publicKey, blockhash, big).unwrapError().cause shouldBe null
        SolanaTxLegacy.tryCreate(MessageHeader(1, 0, 0), listOf(alice.publicKey, alice.publicKey), blockhash, emptyList())
            .unwrapError().cause shouldBe null

        // decoding still maps the wire decoder's own failures, which do carry the original throwable
        SolanaTransactionUnsigned.tryDeserializeMessage(byteArrayOf(1, 0)).unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>().cause shouldNotBe null
    }

    test("tryCreate validates the same fields as the constructor") {
        val compiled = SolanaTxV0.compile(alice.publicKey, blockhash, transfer)
        SolanaTxV0.tryCreate(compiled.header, compiled.accounts, blockhash, compiled.instructions).unwrap().serializeMessage() shouldBe
            compiled.serializeMessage()
        SolanaTxV1.tryCreate(compiled.header, compiled.accounts, blockhash, compiled.instructions, config).unwrap().type shouldBe SolanaTxType.V1
        SolanaTxLegacy.tryCreate(compiled.header, compiled.accounts + compiled.accounts.first(), blockhash, compiled.instructions)
            .unwrapError().shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
            .reason shouldBe SolanaTransactionError.Reason.DUPLICATE_ACCOUNT
    }

    test("errors convert to exceptions that keep the error reachable") {
        val error = SolanaTransactionError.EnvelopeTooLarge(SolanaTxType.V1, 5000, 4096)
        val exception = error.toException()
        exception.error shouldBe error
        exception.shouldBeInstanceOf<IllegalArgumentException>()
        exception.message shouldBe error.toString()
    }
})
