package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.instruction.TokenProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxV0
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

class TransactionTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })

    test("message bytes and signature agree with the independent Solana CLI") {
        // solana transfer SYSTEM 0.000000042 --sign-only --dump-transaction-message;
        // RFC8032 vector 1 key is both sender and fee payer, with a zero blockhash.
        val signer = KeypairSigner.fromSeed(FastHex.decode("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"))
        val message = SolanaTxLegacy.compile(signer.publicKey, SolanaBlockhash(ByteArray(32)), SystemProgram.transfer(signer.publicKey, Programs.SYSTEM, 42L)).unwrap()
        Base64.encode(message.serializeMessage()) shouldBe "AQAAAtdamAGCsQq31Uv+08lkBzoO4XLz2qYjJa8CGmj3B1EaAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEBAgABDAIAAAAqAAAAAAAAAA=="
        message.sign(signer).id.toString() shouldBe "634LhRk4qrhs1pFXm9mCE7it3Ha7hebHSrD1ySBpNoKHmkLMooaM6fgqNFZPa57TbLMCvCM7joA81XMpkqP5yueK"
    }

    test("legacy and v0 messages, signing and roundtrip") {
        val instruction = SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L)
        for (message in listOf(SolanaTxLegacy.compile(alice.publicKey, blockhash, instruction).unwrap(), SolanaTxV0.compile(alice.publicKey, blockhash, instruction).unwrap())) {
            val transaction = message.sign(alice)
            transaction.tx shouldBe message
            transaction.feePayer shouldBe alice.publicKey
            transaction.signers shouldBe listOf(alice.publicKey)
            transaction.id shouldBe transaction.signatures.first()
            SolanaTransactionSigned.deserialize(transaction.serialize()).unwrap().serialize() shouldBe transaction.serialize()
            SolanaTransactionUnsigned.deserializeMessage(message.serializeMessage()).unwrap()::class shouldBe message::class
            SolanaTransactionCompiled.deserialize(transaction.serialize()).unwrap()::class shouldBe SolanaTransactionSigned::class
            val unsigned = SolanaTransactionCompiled.deserialize(message.serializeForSimulation()).unwrap()
            unsigned::class shouldBe message::class
            unsigned.serializeMessage() shouldBe message.serializeMessage()
            message.instructions.single().data.toByteArray() shouldBe FastHex.decode("020000002a00000000000000")
            val changed = transaction.withNewBlockhash(SolanaBlockhash(ByteArray(32)))
            changed::class shouldBe message::class
            changed.signingBuilder().missingSigners shouldBe listOf(alice.publicKey)
            // a signature over the old blockhash is collected, then reported against its slot by build
            changed.signingBuilder().addSignature(alice.publicKey, transaction.id).build()
                .unwrapError() shouldBe SolanaTransactionError.UnsignedSlots(emptyList(), listOf(0))
            // an address that is not a required signer has no slot to fill, so it is still rejected here
            shouldThrow<IllegalArgumentException> { message.signingBuilder().addSignature(bob.publicKey, bob.signMessage(message.serializeMessage())) }
            shouldThrow<IllegalArgumentException> { SolanaTransactionUnsigned.deserializeMessage(message.serializeMessage() + byteArrayOf(0)).unwrap() }
            shouldThrow<IllegalArgumentException> { SolanaTransactionUnsigned.deserializeMessage(message.serializeMessage().dropLast(1).toByteArray()).unwrap() }
            transaction.serializeForSimulation() shouldBe transaction.serialize()
        }
    }

    test("partial and externally signed transactions preserve required signer order") {
        val instruction = Instruction(Programs.SYSTEM, listOf(AccountMeta.signer(alice.publicKey), AccountMeta.signer(bob.publicKey)), byteArrayOf(7))
        for (original in listOf(SolanaTxLegacy.compile(alice.publicKey, blockhash, instruction).unwrap(), SolanaTxV0.compile(alice.publicKey, blockhash, instruction).unwrap())) {
            val partial = original.signingBuilder()
            val liveSignatures = partial.signatures
            partial.sign(bob)
            // the builder's list is a live view of its slots, not a snapshot
            liveSignatures shouldBe partial.signatures
            partial.signatures[0] shouldBe null
            partial.missingSigners shouldBe listOf(alice.publicKey)
            partial.isFullySigned shouldBe false
            shouldThrow<IllegalArgumentException> { original.sign(alice) }
            partial.build().unwrapError() shouldBe SolanaTransactionError.UnsignedSlots(listOf(0), emptyList())
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(partial.serializePartial()).unwrap() }
            shouldThrow<IllegalArgumentException> { SolanaTransactionCompiled.deserialize(partial.serializePartial()).unwrap() }
            val completed = SolanaTransactionSigned.Builder.deserializePartial(partial.serializePartial()).unwrap().sign(alice).build().unwrap()
            completed.serialize() shouldBe original.sign(alice, bob).serialize()
            original.sign(bob, alice).serialize() shouldBe completed.serialize()
            val externallySigned = partial.addSignature(alice.publicKey, alice.signMessage(original.serializeMessage()))
            externallySigned.isFullySigned shouldBe true
            val snapshot = externallySigned.build().unwrap()
            snapshot.serialize() shouldBe completed.serialize()
            original.withNewBlockhash(SolanaBlockhash(ByteArray(32))).signingBuilder().missingSigners shouldBe original.signers
            // a signature that does not hold is now held rather than rejected on the spot, and named by build
            partial.addSignature(alice.publicKey, alice.signMessage(byteArrayOf()))
            partial.build().unwrapError() shouldBe SolanaTransactionError.UnsignedSlots(emptyList(), listOf(0))
            partial.addSignature(alice.publicKey, alice.signMessage(original.serializeMessage()))
            partial.build().unwrap().serialize() shouldBe completed.serialize()
            partial.clearSignatures()
            partial.missingSigners shouldBe original.signers
            partial.isFullySigned shouldBe false
            snapshot.serialize() shouldBe completed.serialize()
            shouldThrow<IllegalArgumentException> { partial.build().unwrap() }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned(original, completed.signatures.reversed()) }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.Builder(original, listOf(null)) }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned(original, emptyList()) }
        }
    }

    test("builder signing failures are atomic") {
        val instruction = Instruction(Programs.SYSTEM, listOf(AccountMeta.signer(alice.publicKey), AccountMeta.signer(bob.publicKey)), byteArrayOf())
        val tx = SolanaTxV0.compile(alice.publicKey, blockhash, instruction).unwrap()
        val builder = tx.signingBuilder()
        val invalidBob = object : SolanaSigner {
            override val publicKey = bob.publicKey
            override fun signMessage(message: ByteArray): SolanaSignature = bob.signMessage(byteArrayOf())
        }
        // a signer that produces a signature over the wrong bytes is reported by build, against its slot
        builder.sign(alice, invalidBob)
        builder.build().unwrapError() shouldBe SolanaTransactionError.UnsignedSlots(emptyList(), listOf(1))
        builder.clearSignatures()
        val throwingBob = object : SolanaSigner {
            override val publicKey = bob.publicKey
            override fun signMessage(message: ByteArray): SolanaSignature = throw IllegalStateException("Signer unavailable")
        }
        shouldThrow<IllegalStateException> { builder.sign(alice, throwingBob) }
        builder.signatures shouldBe listOf(null, null)
        builder.sign(alice, bob).build().unwrap().serialize() shouldBe tx.sign(alice, bob).serialize()
    }

    test("lookup tables load writable then readonly accounts, keeping signers static") {
        // the table must cover two movable accounts, or naming it would cost more than it saves
        val carol = KeypairSigner.fromSeed(ByteArray(32) { 9 }).publicKey
        val table = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 5 }), listOf(bob.publicKey, carol, alice.publicKey))
        val instructions = listOf(
            SystemProgram.transfer(alice.publicKey, bob.publicKey, 1),
            Instruction(Programs.SYSTEM, listOf(AccountMeta(carol)), byteArrayOf(1)),
        )
        val message = SolanaTxV0.compile(alice.publicKey, blockhash, instructions, listOf(table)).unwrap()
        message.accounts shouldBe listOf(alice.publicKey, Programs.SYSTEM)
        message.addressLookupTables.single().writableIndexes shouldBe listOf(0)
        message.addressLookupTables.single().readonlyIndexes shouldBe listOf(1)
        message.instructions.first().accounts shouldBe listOf(0, 2)
        val decoded = SolanaTransactionUnsigned.deserializeMessage(message.serializeMessage()).unwrap() as SolanaTxV0
        decoded.serializeMessage() shouldBe message.serializeMessage()
        decoded.addressLookupTables.single().writableIndexes shouldBe listOf(0)
        val signed = message.sign(alice)
        SolanaTransactionSigned.deserialize(signed.serialize()).unwrap().serialize() shouldBe signed.serialize()
        signed.withNewBlockhash(SolanaBlockhash(ByteArray(32)))::class shouldBe SolanaTxV0::class
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
        val instruction = Instruction(Programs.SYSTEM, accounts.map(AccountMeta::writable), byteArrayOf())
        val message = SolanaTxV0.compile(alice.publicKey, blockhash, instruction, listOf(AddressLookupTableAccount(Programs.TOKEN, accounts))).unwrap()
        message.instructions.single().accounts.any { it >= 128 } shouldBe true
        SolanaTransactionUnsigned.deserializeMessage(message.serializeMessage()).unwrap().serializeMessage() shouldBe message.serializeMessage()
        shouldThrow<IllegalArgumentException> { SolanaTransactionUnsigned.deserializeMessage(message.serializeMessage().also { it[0] = 129.toByte() }).unwrap() }
    }

    test("compute budget encodings use u32 limit and u64 price") {
        ComputeBudgetProgram.setComputeUnitLimit(200000).data.toByteArray() shouldBe FastHex.decode("02400d0300")
        ComputeBudgetProgram.setComputeUnitPrice(1000).data.toByteArray() shouldBe FastHex.decode("03e803000000000000")
        shouldThrow<IllegalArgumentException> { ComputeBudgetProgram.setComputeUnitLimit(4294967296) }
        shouldThrow<IllegalArgumentException> { SystemProgram.transfer(alice.publicKey, bob.publicKey, -1L) }
        TokenProgram.transferChecked(alice.publicKey, bob.publicKey, Programs.TOKEN, alice.publicKey, BigInteger("18446744073709551615"), 9).data.toByteArray() shouldBe FastHex.decode("0cffffffffffffffff09")
        val message = SolanaTxV0.compile(alice.publicKey, blockhash, listOf(ComputeBudgetProgram.setComputeUnitLimit(200000), ComputeBudgetProgram.setComputeUnitPrice(1001), SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L))).unwrap()
        message.estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(5201)
        message.sign(alice).estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(5201)
        val priceOnly = SolanaTxV0.compile(alice.publicKey, blockhash, listOf(ComputeBudgetProgram.setComputeUnitPrice(1000), SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L))).unwrap()
        shouldThrow<IllegalArgumentException> { priceOnly.estimateFee(bigIntegerOf(5000)) }
    }

    test("upstream transaction fixtures roundtrip including partial transactions") {
        upstreamTransactions.forEach { encoded ->
            val builder = SolanaTransactionSigned.Builder.fromBase64Partial(encoded).unwrap()
            builder.toBase64Partial() shouldBe encoded
            if (builder.isFullySigned) {
                SolanaTransactionCompiled.fromBase64(encoded).unwrap().serializeForSimulation() shouldBe builder.build().unwrap().serialize()
            }
        }
    }

    test("signed envelopes reject corrupted, missing or mismatched signatures") {
        val tx = SolanaTxLegacy.compile(alice.publicKey, blockhash, SystemProgram.transfer(alice.publicKey, bob.publicKey, 1)).unwrap()
        val signed = tx.sign(alice)
        val corrupt = signed.serialize().also { it[1] = (it[1].toInt() xor 1).toByte() }
        shouldThrow<IllegalArgumentException> { SolanaTransactionCompiled.deserialize(corrupt).unwrap() }
        shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(corrupt).unwrap() }
        // a collector imports the corrupt slot and reports it when asked to build
        SolanaTransactionSigned.Builder.deserializePartial(corrupt).unwrap().build()
            .unwrapError() shouldBe SolanaTransactionError.UnsignedSlots(emptyList(), listOf(0))
        shouldThrow<IllegalArgumentException> { SolanaTransactionSigned(tx, listOf(SolanaSignature(ByteArray(64)))) }
        shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(tx.serializeForSimulation()).unwrap() }
        shouldThrow<IllegalArgumentException> { SolanaTransactionCompiled.deserialize(byteArrayOf(0) + tx.serializeMessage()).unwrap() }
        shouldThrow<IllegalArgumentException> { SolanaTransactionCompiled.deserialize(byteArrayOf(2) + ByteArray(128) + tx.serializeMessage()).unwrap() }
        shouldThrow<IllegalArgumentException> { SolanaTransactionCompiled.deserialize(signed.serialize() + byteArrayOf(0)).unwrap() }
    }

    test("serialized payloads and instruction data cannot be mutated through the transaction") {
        val instruction = SystemProgram.transfer(alice.publicKey, bob.publicKey, 42)
        val tx = SolanaTxLegacy.compile(alice.publicKey, blockhash, instruction).unwrap()
        val bytes = tx.serializeMessage()

        // every call hands back a fresh array, so writing to one cannot affect the transaction
        tx.serializeMessage()[0] = 0
        tx.serializeMessage() shouldBe bytes
        // instruction data is SolanaBytes, so it exposes no mutable storage at all
        tx.instructions.first().data.toByteArray().fill(0)
        tx.serializeMessage() shouldBe bytes

        val signed = SolanaTransactionSigned(tx, listOf(alice.signMessage(bytes)))
        signed.signatures.size shouldBe 1
        alice.signTransaction(tx).serialize() shouldBe signed.serialize()
        tx.signingBuilder().sign(alice).build().unwrap().serialize() shouldBe signed.serialize()
    }

    test("concrete transaction constructors validate their compiled fields") {
        shouldThrow<IllegalArgumentException> { SolanaTxLegacy(MessageHeader(0, 0, 0), listOf(alice.publicKey), blockhash, emptyList()) }
        shouldThrow<IllegalArgumentException> { SolanaTxLegacy(MessageHeader(1, 1, 0), listOf(alice.publicKey), blockhash, emptyList()) }
        shouldThrow<IllegalArgumentException> {
            SolanaTxV0(MessageHeader(1, 0, 0), listOf(alice.publicKey), blockhash, emptyList(), listOf(CompiledAddressLookupTable(Programs.SYSTEM, listOf(256), emptyList())))
        }
    }

    test("each caller owns the message bytes it is given, however many times it asks") {
        val tx = SolanaTxV0.compile(alice.publicKey, blockhash, SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L)).unwrap()

        // the encoding is cached, but never handed out: each call returns a fresh array
        val first = tx.serializeMessage()
        val second = tx.serializeMessage()
        (first === second) shouldBe false
        first shouldBe second

        // so writing to one cannot corrupt the transaction's identity for anyone else
        first[0] = (first[0] + 1).toByte()
        tx.serializeMessage() shouldBe second
        tx.sign(alice).tx.serializeMessage() shouldBe second

        // and collecting signatures one at a time still verifies against those same bytes
        val builder = tx.signingBuilder()
        builder.addSignature(alice.publicKey, alice.signMessage(second))
        builder.build().unwrap().serialize() shouldBe tx.sign(alice).serialize()
    }
})
