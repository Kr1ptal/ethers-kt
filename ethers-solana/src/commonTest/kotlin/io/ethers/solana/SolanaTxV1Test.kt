package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaRPCTransaction
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class SolanaTxV1Test : FunSpec({
    val alice = KeypairSigner.fromSeed(FastHex.decode("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"))
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = SolanaBlockhash(ByteArray(32))
    val empty = SolanaTransactionConfig()
    val config = SolanaTransactionConfig(bigIntegerOf(5000), 20000, 65536, 65536)
    fun transfer(requests: SolanaTransactionConfig = config) = SolanaTxV1.compile(alice.publicKey, blockhash, SystemProgram.transfer(alice.publicKey, bob.publicKey, 42), requests)
    fun decode(bytes: ByteArray) = SolanaTransactionUnsigned.deserializeMessage(bytes) as SolanaTxV1

    test("SIMD-0385 golden layout and independent Node crypto Ed25519 signature") {
        // Explicit layout checked against solana-message 4.5.0 versions/v1/message.rs SchemaWrite.
        // Signature independently generated with Node crypto.sign(null, message, RFC8032 vector 1 key).
        val expected = FastHex.decode(
            "810100001f000000" + "00".repeat(32) + "0202" +
                "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a" + "00".repeat(32) +
                "ffffffffffffffff204e00000000010000000100" + // priority u64, compute/data/heap u32
                "01020c0001000300" + // BOTH instruction headers precede any payload
                "0001020000002a00000000000000aabbcc",
        )
        val tx = SolanaTxV1.compile(
            alice.publicKey,
            blockhash,
            listOf(SystemProgram.transfer(alice.publicKey, Programs.SYSTEM, 42), Instruction(Programs.SYSTEM, emptyList(), FastHex.decode("aabbcc"))),
            config.copy(priorityFee = BigInteger("18446744073709551615")),
        )
        tx.serializeMessage() shouldBe expected
        decode(expected).serializeMessage() shouldBe expected
        val signed = tx.sign(alice)
        signed.id.toByteArray() shouldBe FastHex.decode("cc7a3989600e8a7b6cf4c4a917e46b6887f7151b393f4672b354f018c10e9a66f1d22955779cfe3502534d6cf74b7c94b83704a924886ac5355b3e349620c605")
        signed.serialize() shouldBe expected + signed.id.toByteArray()
        SolanaTransactionSigned.fromBase64(signed.toBase64()).serialize() shouldBe signed.serialize()
        signed.type shouldBe SolanaTxType.V1
        signed.tx::class shouldBe SolanaTxV1::class
    }

    test("every config mask roundtrips including absent versus explicit zero and unsigned maxima") {
        for (bits in 0..15) {
            val requests = SolanaTransactionConfig(
                if (bits and 1 != 0) BigInteger("18446744073709551615") else null,
                if (bits and 2 != 0) 4294967295 else null,
                if (bits and 4 != 0) 4294967295 else null,
                if (bits and 8 != 0) 262144 else null,
            )
            val tx = transfer(requests)
            decode(tx.serializeMessage()).config shouldBe requests
            SolanaTransaction.deserialize(tx.serializeForSimulation()).serializeMessage() shouldBe tx.serializeMessage()
        }
        val zeros = SolanaTransactionConfig(bigIntegerOf(0), 0, 0)
        decode(transfer(zeros).serializeMessage()).config shouldBe zeros
        transfer(zeros).serializeMessage().size shouldBe transfer(empty).serializeMessage().size + 16
        decode(transfer(empty).serializeMessage()).config shouldBe empty
        for (invalid in listOf(-1L, 4294967296L)) {
            shouldThrow<IllegalArgumentException> { config.copy(computeUnitLimit = invalid) }
            shouldThrow<IllegalArgumentException> { config.copy(loadedAccountsDataSizeLimit = invalid) }
            shouldThrow<IllegalArgumentException> { config.copy(heapSize = invalid) }
        }
        shouldThrow<IllegalArgumentException> { config.copy(priorityFee = bigIntegerOf(-1)) }
        shouldThrow<IllegalArgumentException> { config.copy(priorityFee = BigInteger("18446744073709551616")) }
    }

    test("inline priority fee is total lamports and ComputeBudget instructions are ignored") {
        val tx = SolanaTxV1.compile(alice.publicKey, blockhash, Instruction(Programs.COMPUTE_BUDGET, emptyList(), byteArrayOf()), config)
        tx.estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(10000)
        tx.sign(alice).estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(10000)
        tx.withConfig(empty).estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(5000)
        shouldThrow<IllegalArgumentException> { tx.estimateFee(bigIntegerOf(-1)) }
    }

    test("partial signatures, unsigned simulations and config/blockhash replacement") {
        val tx = SolanaTxV1.compile(alice.publicKey, blockhash, Instruction(Programs.SYSTEM, listOf(AccountMeta.signer(bob.publicKey)), byteArrayOf(7)), config)
        val builder = tx.signingBuilder().sign(bob)
        builder.signatures.first() shouldBe null
        shouldThrow<IllegalArgumentException> { builder.build() }
        shouldThrow<IllegalArgumentException> { SolanaTransaction.deserialize(builder.serializePartial()) }
        val imported = SolanaTransactionSigned.Builder.fromBase64Partial(builder.toBase64Partial())
        imported.missingSigners shouldBe listOf(alice.publicKey)
        val signed = imported.sign(alice).build()
        signed.serialize() shouldBe tx.sign(bob, alice).serialize()
        signed.serialize().takeLast(128).toByteArray() shouldBe signed.signatures.flatMap { it.toByteArray().toList() }.toByteArray()
        signed.serializeForSimulation() shouldBe signed.serialize()
        SolanaTransaction.deserialize(tx.serializeForSimulation())::class shouldBe SolanaTxV1::class
        shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(tx.serializeForSimulation()) }
        val changed = signed.withNewBlockhash(SolanaBlockhash(ByteArray(32) { 1 })) as SolanaTxV1
        changed.config shouldBe config
        changed.signingBuilder().missingSigners shouldBe tx.signers
        shouldThrow<IllegalArgumentException> { changed.signingBuilder().addSignature(alice.publicKey, signed.id) }
        shouldThrow<IllegalArgumentException> { tx.withConfig(empty).signingBuilder().addSignature(alice.publicKey, signed.id) }
        builder.clearSignatures()
        signed.serialize() shouldBe tx.sign(alice, bob).serialize()
    }

    test("reject malformed masks, truncated fields, trailing bytes and wrong envelopes") {
        val tx = transfer()
        val message = tx.serializeMessage()
        for (mask in listOf(1, 2, 32, 255)) {
            shouldThrow<IllegalArgumentException> { decode(message.copyOf().also { it[4] = mask.toByte() }) }
        }
        shouldThrow<IllegalArgumentException> { decode(message.copyOf().also { it[7] = 128.toByte() }) }
        for ((offset, value) in listOf(1 to 0, 1 to 13, 2 to 1, 3 to 255, 40 to 65, 41 to 65)) {
            shouldThrow<IllegalArgumentException> { decode(message.copyOf().also { it[offset] = value.toByte() }) }
        }
        for (length in message.indices) {
            shouldThrow<IllegalArgumentException> { decode(message.copyOf(length)) }
        }
        val signed = tx.sign(alice).serialize()
        for (length in signed.indices) {
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(signed.copyOf(length)) }
        }
        shouldThrow<IllegalArgumentException> { decode(message + byteArrayOf(0)) }
        shouldThrow<IllegalArgumentException> { SolanaTransaction.deserialize(signed + byteArrayOf(0)) }
        shouldThrow<IllegalArgumentException> { SolanaTransaction.deserialize(signed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }) }
        shouldThrow<IllegalArgumentException> { SolanaTransaction.deserialize(byteArrayOf(1) + ByteArray(64) + message) }
        shouldThrow<IllegalArgumentException> { decode(message.copyOf().also { it[0] = 130.toByte() }) }
    }

    test("v1 size boundary includes trailing signatures and instruction data lengths are u16") {
        fun sized(size: Int) = SolanaTxV1(MessageHeader(1, 0, 1), listOf(alice.publicKey, Programs.SYSTEM), blockhash, listOf(CompiledInstruction(1, emptyList(), ByteArray(size) { 7 })), empty)
        // 42 fixed + 64 addresses + 4 instruction header + 64 signature = 174.
        val tx = sized(4096 - 174)
        tx.serializeForSimulation().size shouldBe 4096
        decode(tx.serializeMessage()).serializeMessage() shouldBe tx.serializeMessage()
        val signed = tx.sign(alice)
        SolanaTransactionSigned.deserialize(signed.serialize()).serialize() shouldBe signed.serialize()
        shouldThrow<IllegalArgumentException> { sized(4096 - 173) }
        shouldThrow<IllegalArgumentException> { sized(65536) }
    }

    test("v1 counts, heap boundaries and account privileges are validated") {
        val accounts = (1..65).map { SolanaAddress(ByteArray(32) { _ -> it.toByte() }) }
        fun construct(header: MessageHeader = MessageHeader(1, 0, 0), keys: List<SolanaAddress> = accounts.take(2), instructions: List<CompiledInstruction> = emptyList(), requests: SolanaTransactionConfig = empty) = SolanaTxV1(header, keys, blockhash, instructions, requests)
        construct(keys = accounts.take(64)).accounts.size shouldBe 64
        shouldThrow<IllegalArgumentException> { construct(keys = accounts) }
        construct(header = MessageHeader(12, 11, 0), keys = accounts.take(12)).header.requiredSignatures shouldBe 12
        shouldThrow<IllegalArgumentException> { construct(header = MessageHeader(13, 0, 0), keys = accounts.take(13)) }
        shouldThrow<IllegalArgumentException> { construct(keys = listOf(alice.publicKey, alice.publicKey)) }
        shouldThrow<IllegalArgumentException> { construct(header = MessageHeader(1, 1, 0)) }
        shouldThrow<IllegalArgumentException> { construct(header = MessageHeader(1, 0, 2)) }
        val instruction = CompiledInstruction(1, List(255) { 0 }, byteArrayOf())
        construct(instructions = listOf(instruction)).instructions.single().accounts.size shouldBe 255
        construct(instructions = List(64) { CompiledInstruction(1, emptyList(), byteArrayOf()) }).instructions.size shouldBe 64
        shouldThrow<IllegalArgumentException> { construct(instructions = List(65) { CompiledInstruction(1, emptyList(), byteArrayOf()) }) }
        for (invalid in listOf(CompiledInstruction(0, emptyList(), byteArrayOf()), CompiledInstruction(2, emptyList(), byteArrayOf()), CompiledInstruction(1, listOf(2), byteArrayOf()), CompiledInstruction(1, List(256) { 0 }, byteArrayOf()))) {
            shouldThrow<IllegalArgumentException> { construct(instructions = listOf(invalid)) }
        }
        for (heap in listOf(0L, 32767L, 32769L, 263168L)) {
            shouldThrow<IllegalArgumentException> { construct(requests = empty.copy(heapSize = heap)) }
        }
        for (heap in listOf(32768L, 65536L, 262144L)) construct(requests = empty.copy(heapSize = heap))
    }

    test("v1 signing bytes and instruction data cannot be mutated through the transaction") {
        val tx = transfer()
        val bytes = tx.serializeMessage()
        tx.instructions.first().data.toByteArray().fill(0)
        tx.serializeMessage().fill(0)
        tx.serializeMessage() shouldBe bytes
    }

    test("RPC config is strongly typed, nullable only when absent, and preserves full unsigned ranges") {
        // Shape from Agave transaction-status-client-types UiRawMessage/UiTransactionConfig.
        val payload = """{"slot":1,"blockTime":null,"version":1,"meta":null,"transaction":{"signatures":[],"message":{"header":{"numRequiredSignatures":1,"numReadonlySignedAccounts":0,"numReadonlyUnsignedAccounts":1},"accountKeys":["${alice.publicKey}","${Programs.SYSTEM}"],"recentBlockhash":"$blockhash","instructions":[],"transactionConfig":{"priorityFee":18446744073709551615,"computeUnitLimit":4294967295,"loadedAccountsDataSizeLimit":0,"heapSize":null}}}}"""
        val tx = Kotlinx.DEFAULT.decodeFromString<SolanaRPCTransaction>(payload)
        tx.type shouldBe SolanaTxType.V1
        tx.transaction.message.transactionConfig shouldBe SolanaTransactionConfig(BigInteger("18446744073709551615"), 4294967295, 0)
        tx.transaction.message.otherFields.containsKey("transactionConfig") shouldBe false
        val encoded = Kotlinx.DEFAULT.encodeToJsonElement(tx)
        Kotlinx.DEFAULT.decodeFromString<SolanaRPCTransaction>(encoded.toString()) shouldBe tx
        val jsonConfig = Kotlinx.DEFAULT.encodeToJsonElement(tx.transaction.message.transactionConfig!!).jsonObject
        val future = Kotlinx.DEFAULT.decodeFromString<SolanaTransactionConfig>(JsonObject(jsonConfig + ("futureLimit" to JsonPrimitive(42))).toString())
        future.otherFields["futureLimit"] shouldBe JsonPrimitive(42)
        Kotlinx.DEFAULT.encodeToJsonElement(future).jsonObject["futureLimit"] shouldBe JsonPrimitive(42)
        shouldThrow<IllegalArgumentException> { transfer(future) }
        for (field in listOf("computeUnitLimit", "loadedAccountsDataSizeLimit", "heapSize")) {
            for (value in listOf(JsonPrimitive(-1), JsonPrimitive(4294967296L), JsonPrimitive("1"), JsonPrimitive(1.5))) {
                shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString<SolanaTransactionConfig>(JsonObject(jsonConfig + (field to value)).toString()) }
            }
        }
    }
})
