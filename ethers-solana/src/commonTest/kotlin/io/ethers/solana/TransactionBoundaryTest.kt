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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class TransactionBoundaryTest : FunSpec({
    val signer = KeypairSigner.fromSeed(ByteArray(32) { 11 })
    val keys = listOf(signer.publicKey, Programs.SYSTEM)
    val hash = SolanaBlockhash(ByteArray(32) { 12 })
    val header = MessageHeader(1, 0, 1)

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

    test("legacy and v0 data lengths cross every compact-u16 boundary") {
        // Wire-format limits, not a claim that oversized messages can be submitted to a validator.
        for (size in listOf(0, 1, 127, 128, 129, 16383, 16384, 16385, 65535)) {
            val instructions = listOf(CompiledInstruction(1, listOf(0), ByteArray(size) { it.toByte() }))
            for (tx in listOf(SolanaTxLegacy(header, keys, hash, instructions), SolanaTxV0(header, keys, hash, instructions, emptyList()))) {
                val decoded = SolanaTransactionUnsigned.deserializeMessage(tx.serializeMessage())
                decoded.instructions.single().data shouldBe instructions.single().data
                decoded.serializeMessage() shouldBe tx.serializeMessage()
            }
        }
        for (tx in listOf(SolanaTxLegacy(header, keys, hash, listOf(CompiledInstruction(1, emptyList(), ByteArray(65536)))), SolanaTxV0(header, keys, hash, listOf(CompiledInstruction(1, emptyList(), ByteArray(65536))), emptyList()))) {
            shouldThrow<IllegalArgumentException> { tx.serializeMessage() }
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
