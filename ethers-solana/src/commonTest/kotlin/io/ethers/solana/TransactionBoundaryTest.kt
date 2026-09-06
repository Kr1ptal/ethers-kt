package io.ethers.solana

import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class TransactionBoundaryTest : FunSpec({
    val signer = KeypairSigner.fromSeed(ByteArray(32) { 11 })
    val keys = listOf(signer.publicKey, Programs.SYSTEM)
    val hash = SolanaBlockhash(ByteArray(32) { 12 })
    val header = MessageHeader(1, 0, 1)

    test("envelopeSize matches the serialized envelope for every version") {
        val random = Random(7)
        repeat(40) {
            val instructions = List(random.nextInt(1, 5)) {
                CompiledInstruction(1, List(random.nextInt(0, 5)) { random.nextInt(2) }, random.nextBytes(random.nextInt(0, 140)))
            }
            val lookups = listOf(CompiledAddressLookupTable(Programs.TOKEN, List(random.nextInt(0, 4)) { it }, List(random.nextInt(0, 4)) { it + 8 }))
            val messages = listOf(
                SolanaTxLegacy(header, keys, hash, instructions),
                SolanaTxV0(header, keys, hash, instructions, emptyList()),
                SolanaTxV0(header, keys, hash, instructions, lookups),
                SolanaTxV1(header, keys, hash, instructions, SolanaTransactionConfig()),
                SolanaTxV1(header, keys, hash, instructions, SolanaTransactionConfig(priorityFee = bigIntegerOf(7), computeUnitLimit = 1000)),
            )
            for (tx in messages) {
                tx.envelopeSize() shouldBe tx.serializeForSimulation().size.toLong()
                tx.envelopeSize() shouldBe tx.sign(signer).serialize().size.toLong()
            }
        }
    }

    test("all compact-u16 values have canonical independently calculated bytes") {
        for (value in 0..65535) {
            val expected = when {
                value < 128 -> byteArrayOf(value.toByte())
                value < 16384 -> byteArrayOf(((value % 128) + 128).toByte(), (value / 128).toByte())
                else -> byteArrayOf(((value % 128) + 128).toByte(), (((value / 128) % 128) + 128).toByte(), (value / 16384).toByte())
            }
            SolanaMessageEncoder().writeShortVecLength(value).toByteArray() shouldBe expected
            val decoder = SolanaMessageDecoder(expected)
            decoder.readShortVecLength() shouldBe value
            decoder.requireDone()
        }
        for (value in listOf(-1, 65536, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { SolanaMessageEncoder().writeShortVecLength(value) }
        }
    }

    test("compact-u16 rejects aliases overflow and unterminated lengths") {
        val invalid = listOf(
            byteArrayOf(),
            byteArrayOf(128.toByte()),
            byteArrayOf(255.toByte(), 255.toByte()),
            byteArrayOf(128.toByte(), 0),
            byteArrayOf(129.toByte(), 0),
            byteArrayOf(128.toByte(), 128.toByte(), 0),
            byteArrayOf(255.toByte(), 255.toByte(), 4),
            byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 0),
        )
        for (bytes in invalid) shouldThrow<IllegalArgumentException> { SolanaMessageDecoder(bytes).readShortVecLength() }
    }

    test("legacy and v0 data lengths cross the packet-reachable compact-u16 boundary") {
        for (size in listOf(0, 1, 127, 128, 129)) {
            val instructions = listOf(CompiledInstruction(1, listOf(0), ByteArray(size) { it.toByte() }))
            for (tx in listOf(SolanaTxLegacy(header, keys, hash, instructions), SolanaTxV0(header, keys, hash, instructions, emptyList()))) {
                val decoded = SolanaTransactionUnsigned.deserializeMessage(tx.serializeMessage())
                decoded.instructions.single().data shouldBe instructions.single().data
                decoded.serializeMessage() shouldBe tx.serializeMessage()
            }
        }
        for (size in listOf(16383, 16384, 16385, 65535, 65536)) {
            val instructions = listOf(CompiledInstruction(1, emptyList(), ByteArray(size)))
            shouldThrow<IllegalArgumentException> { SolanaTxLegacy(header, keys, hash, instructions) }
            shouldThrow<IllegalArgumentException> { SolanaTxV0(header, keys, hash, instructions, emptyList()) }
        }
    }

    test("construction and decoding reserve signature bytes at the legacy and v0 packet boundary") {
        for (versioned in listOf(false, true)) {
            for (signerCount in listOf(1, 2, 12)) {
                val signers = List(signerCount) { KeypairSigner.fromSeed(ByteArray(32) { _ -> (it + 20).toByte() }) }
                val accounts = signers.map { it.publicKey } + Programs.SYSTEM
                val messageHeader = MessageHeader(signerCount, 0, 1)
                fun construct(dataSize: Int): SolanaTransactionUnsigned {
                    val instructions = listOf(CompiledInstruction(signerCount, emptyList(), ByteArray(dataSize)))
                    return if (versioned) SolanaTxV0(messageHeader, accounts, hash, instructions) else SolanaTxLegacy(messageHeader, accounts, hash, instructions)
                }
                // Length-prefix width may increase when filling the remaining space.
                val baseline = construct(0).serializeForSimulation().size
                val available = 1232 - baseline
                val dataSize = available - if (available >= 128) 1 else 0
                val tx = construct(dataSize)
                tx.serializeForSimulation().size shouldBe 1232
                tx.signingBuilder().serializePartial().size shouldBe 1232
                tx.sign(*signers.toTypedArray()).serialize().size shouldBe 1232
                shouldThrow<IllegalArgumentException> { construct(dataSize + 1) }
                // An otherwise well-formed unsigned message whose eventual envelope needs 1233 bytes.
                val encoder = SolanaMessageEncoder()
                if (versioned) encoder.writeByte(128)
                encoder.writeByte(signerCount).writeByte(0).writeByte(1).writeShortVecLength(accounts.size)
                accounts.forEach { encoder.writeBytes(it.toByteArray()) }
                encoder.writeBytes(hash.toByteArray()).writeShortVecLength(1).writeByte(signerCount).writeShortVecLength(0)
                    .writeShortVecLength(dataSize + 1).writeBytes(ByteArray(dataSize + 1))
                if (versioned) encoder.writeShortVecLength(0)
                shouldThrow<IllegalArgumentException> { SolanaTransactionUnsigned.deserializeMessage(encoder.toByteArray()) }
                val envelope = SolanaMessageEncoder().writeShortVecLength(signerCount).writeBytes(ByteArray(signerCount * 64)).writeBytes(encoder.toByteArray()).toByteArray()
                shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.Builder.deserializePartial(envelope) }
            }
        }
    }

    test("v0 loaded account indices reach 255 but reject a 257th account") {
        val lookup = CompiledAddressLookupTable(Programs.TOKEN, (0..126).toList(), (127..253).toList())
        val tx = SolanaTxV0(header, keys, hash, listOf(CompiledInstruction(1, listOf(0, 127, 128, 255), byteArrayOf())), listOf(lookup))
        val decoded = SolanaTransactionUnsigned.deserializeMessage(tx.serializeMessage()) as SolanaTxV0
        decoded.instructions.single().accounts shouldBe listOf(0, 127, 128, 255)
        decoded.serializeMessage() shouldBe tx.serializeMessage()
        shouldThrow<IllegalArgumentException> {
            SolanaTxV0(header, keys, hash, emptyList(), listOf(CompiledAddressLookupTable(Programs.TOKEN, (0..254).toList(), emptyList())))
        }
    }

    test("seeded messages across all versions reject every truncation and signed byte mutation") {
        val random = Random(0x501A)
        repeat(8) {
            val instructions = List(random.nextInt(1, 5)) {
                CompiledInstruction(1, List(random.nextInt(0, 5)) { random.nextInt(2) }, random.nextBytes(random.nextInt(0, 140)))
            }
            val messages = listOf(
                SolanaTxLegacy(header, keys, hash, instructions),
                SolanaTxV0(header, keys, hash, instructions, emptyList()),
                SolanaTxV1(header, keys, hash, instructions, SolanaTransactionConfig()),
            )
            for (tx in messages) {
                val message = tx.serializeMessage()
                SolanaTransactionUnsigned.deserializeMessage(message).serializeMessage() shouldBe message
                for (end in message.indices) {
                    shouldThrow<IllegalArgumentException> { SolanaTransactionUnsigned.deserializeMessage(message.copyOf(end)) }
                }
                val wire = tx.sign(signer).serialize()
                SolanaTransactionSigned.deserialize(wire).serialize() shouldBe wire
                for (end in wire.indices) {
                    shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(wire.copyOf(end)) }
                }
                for (index in wire.indices) {
                    val mutated = wire.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
                    shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(mutated) }
                }
                shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(wire + byteArrayOf(0)) }
            }
        }
    }
})
