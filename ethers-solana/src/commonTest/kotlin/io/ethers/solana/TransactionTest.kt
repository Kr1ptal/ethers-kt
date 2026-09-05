package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.solana.instruction.BaseInstruction
import io.ethers.solana.instruction.SetComputeUnitLimitInstruction
import io.ethers.solana.instruction.SetComputeUnitPriceInstruction
import io.ethers.solana.instruction.SplTransferInstruction
import io.ethers.solana.instruction.TransferInstruction
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.MessageVersion
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.TransactionMessage
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

class TransactionTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = Blockhash(ByteArray(32) { 3 })

    test("message bytes and signature agree with the independent Solana CLI") {
        // solana transfer SYSTEM 0.000000042 --sign-only --dump-transaction-message;
        // RFC8032 vector 1 key is both sender and fee payer, with a zero blockhash.
        val signer = KeypairSigner.fromSeed(FastHex.decode("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"))
        val message = TransactionMessage.compile(signer.publicKey, Blockhash(ByteArray(32)), TransferInstruction(signer.publicKey, Programs.SYSTEM, 42L), version = MessageVersion.LEGACY)
        Base64.encode(message.serialize()) shouldBe "AQAAAtdamAGCsQq31Uv+08lkBzoO4XLz2qYjJa8CGmj3B1EaAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEBAgABDAIAAAAqAAAAAAAAAA=="
        signer.signMessage(message.serialize()).toString() shouldBe "634LhRk4qrhs1pFXm9mCE7it3Ha7hebHSrD1ySBpNoKHmkLMooaM6fgqNFZPa57TbLMCvCM7joA81XMpkqP5yueK"
    }

    test("legacy and v0 messages, signing and roundtrip") {
        for (version in MessageVersion.entries) {
            val instruction = TransferInstruction(alice.publicKey, bob.publicKey, 42L)
            val message = TransactionMessage.compile(alice.publicKey, blockhash, instruction, version = version)
            val transaction = SolanaTransaction(message).sign(alice)
            transaction.isFullySigned shouldBe true
            SolanaTransaction.deserialize(transaction.serialize()).serialize() shouldBe transaction.serialize()
            message.instructions.single().data shouldBe FastHex.decode("020000002a00000000000000")
            transaction.withNewBlockhash(Blockhash(ByteArray(32))).isFullySigned shouldBe false
            shouldThrow<IllegalArgumentException> { transaction.addSignature(bob.publicKey, bob.signMessage(message.serialize())) }
            shouldThrow<IllegalArgumentException> { TransactionMessage.deserialize(message.serialize() + byteArrayOf(0)) }
            shouldThrow<IllegalArgumentException> { TransactionMessage.deserialize(message.serialize().dropLast(1).toByteArray()) }
        }
    }

    test("partial and externally signed transactions preserve required signer order") {
        val instruction = BaseInstruction(Programs.SYSTEM, listOf(AccountMeta.signer(alice.publicKey), AccountMeta.signer(bob.publicKey)), byteArrayOf(7))
        val original = SolanaTransaction(TransactionMessage.compile(alice.publicKey, blockhash, instruction))
        val partial = original.sign(bob)
        original.signatures shouldBe listOf(null, null)
        partial.signatures[0] shouldBe null
        shouldThrow<IllegalArgumentException> { partial.serialize() }
        shouldThrow<IllegalArgumentException> { SolanaTransaction.deserialize(partial.serializePartial()) }
        val completed = SolanaTransaction.deserialize(partial.serializePartial(), allowPartial = true).sign(alice)
        completed.serialize() shouldBe original.sign(alice).sign(bob).serialize()
        shouldThrow<IllegalArgumentException> { completed.addSignature(alice.publicKey, alice.signMessage(byteArrayOf())) }
    }

    test("lookup tables load writable then readonly accounts, keeping signers static") {
        val table = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 5 }), listOf(bob.publicKey, alice.publicKey))
        val message = TransactionMessage.compile(alice.publicKey, blockhash, TransferInstruction(alice.publicKey, bob.publicKey, 1), listOf(table))
        message.accounts shouldBe listOf(alice.publicKey, Programs.SYSTEM)
        message.addressLookupTables.single().writableIndexes shouldBe listOf(0)
        message.instructions.single().accounts shouldBe listOf(0, 2)
        TransactionMessage.deserialize(message.serialize()).serialize() shouldBe message.serialize()
        shouldThrow<IllegalArgumentException> { TransactionMessage.compile(alice.publicKey, blockhash, TransferInstruction(alice.publicKey, bob.publicKey, 1), listOf(table), MessageVersion.LEGACY) }
    }

    test("unsigned indices above 127 and invalid versions") {
        val accounts = (1..140).map { n ->
            SolanaAddress(
                ByteArray(32).also {
                    it[0] = n.toByte()
                    it[1] = 8
                },
            )
        }
        val instruction = BaseInstruction(Programs.SYSTEM, accounts.map(AccountMeta::writable), byteArrayOf())
        val message = TransactionMessage.compile(alice.publicKey, blockhash, instruction)
        message.instructions.single().accounts.any { it >= 128 } shouldBe true
        TransactionMessage.deserialize(message.serialize()).serialize() shouldBe message.serialize()
        shouldThrow<IllegalArgumentException> { TransactionMessage.deserialize(message.serialize().also { it[0] = 129.toByte() }) }
    }

    test("compute budget encodings use u32 limit and u64 price") {
        SetComputeUnitLimitInstruction(200000).data shouldBe FastHex.decode("02400d0300")
        SetComputeUnitPriceInstruction(1000).data shouldBe FastHex.decode("03e803000000000000")
        shouldThrow<IllegalArgumentException> { SetComputeUnitLimitInstruction(4294967296) }
        shouldThrow<IllegalArgumentException> { TransferInstruction(alice.publicKey, bob.publicKey, -1L) }
        SplTransferInstruction(alice.publicKey, bob.publicKey, Programs.TOKEN, alice.publicKey, BigInteger("18446744073709551615"), 9).data shouldBe FastHex.decode("0cffffffffffffffff09")
        val message = TransactionMessage.compile(alice.publicKey, blockhash, listOf(SetComputeUnitLimitInstruction(200000), SetComputeUnitPriceInstruction(1001), TransferInstruction(alice.publicKey, bob.publicKey, 42L)))
        SolanaTransaction(message).estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(5201)
        val priceOnly = TransactionMessage.compile(alice.publicKey, blockhash, listOf(SetComputeUnitPriceInstruction(1000), TransferInstruction(alice.publicKey, bob.publicKey, 42L)))
        shouldThrow<IllegalArgumentException> { SolanaTransaction(priceOnly).estimateFee(bigIntegerOf(5000)) }
    }

    test("upstream transaction fixtures roundtrip including partial transactions") {
        upstreamTransactions.forEach { encoded ->
            val transaction = SolanaTransaction.fromBase64(encoded, allowPartial = true)
            Base64.encode(transaction.serializePartial()) shouldBe encoded
        }
    }
})
