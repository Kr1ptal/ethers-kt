package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.corpus.transactionCorpus
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.serialization.U8List
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.MessageInstruction
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.ethers.solana.types.transaction.messageError
import io.github.artificialpb.bignum.BigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/**
 * Decoding keeps the message bytes it read instead of encoding them again, and decoded index lists
 * are stored unboxed. These check that neither shortcut is observable.
 */
class TransactionCodecReuseTest : FunSpec({
    val wires = transactionCorpus().map { Base64.decode(Kotlinx.DEFAULT.parseToJsonElement(it).jsonObject.getValue("wire").jsonPrimitive.content) }

    /** The same fields built from scratch, with plain lists instead of decoded ones, so nothing is reused. */
    fun rebuild(tx: SolanaTransactionUnsigned): SolanaTransactionUnsigned {
        val instructions = tx.instructions.map { MessageInstruction(it.programIdIndex, it.accounts.toList(), it.data) }
        return when (tx) {
            is SolanaTxLegacy -> SolanaTxLegacy(tx.header, tx.accounts.toList(), tx.recentBlockhash, instructions)
            is SolanaTxV0 -> SolanaTxV0(
                tx.header,
                tx.accounts.toList(),
                tx.recentBlockhash,
                instructions,
                tx.addressLookupTables.map { it.copy(writableIndexes = it.writableIndexes.toList(), readonlyIndexes = it.readonlyIndexes.toList()) },
            )
            is SolanaTxV1 -> SolanaTxV1(tx.header, tx.accounts.toList(), tx.recentBlockhash, instructions, tx.config)
        }
    }

    test("decoded message bytes match encoding the decoded fields from scratch, for every corpus transaction") {
        wires.size shouldBe 1340
        for (wire in wires) {
            val builder = SolanaTransactionSigned.Builder.deserializePartial(wire).unwrap()
            val rebuilt = rebuild(builder.tx)
            rebuilt.serializeMessage() shouldBe builder.tx.serializeMessage()
            rebuilt shouldBe builder.tx
            rebuilt.hashCode() shouldBe builder.tx.hashCode()
            rebuilt.encodeEnvelope(builder.signatures) shouldBe wire
            builder.serializePartial() shouldBe wire
            // a new blockhash drops the decoded bytes, so this encodes the decoded lists afresh
            builder.tx.withNewBlockhash(builder.tx.recentBlockhash).encodeEnvelope(builder.signatures) shouldBe wire
        }
    }

    test("a decoded transaction does not share the caller's input array") {
        val wire = wires.first()
        val tx = SolanaTransactionSigned.deserialize(wire.copyOf()).unwrap()
        val input = wire.copyOf()
        val decoded = SolanaTransactionSigned.deserialize(input).unwrap()
        input.fill(0)
        decoded.serialize() shouldBe wire
        decoded shouldBe tx

        // nor does the unsigned message decoder
        val message = tx.serializeMessage()
        val unsigned = SolanaTransactionUnsigned.deserializeMessage(message).unwrap()
        message.fill(0)
        unsigned.serializeMessage() shouldBe tx.serializeMessage()
    }

    test("withNewBlockhash encodes the new blockhash without revalidating unchanged fields") {
        for (wire in wires.take(50)) {
            val tx = SolanaTransactionSigned.Builder.deserializePartial(wire).unwrap().tx
            val blockhash = SolanaBlockhash(ByteArray(32) { 7 })
            val moved = tx.withNewBlockhash(blockhash)
            moved.recentBlockhash shouldBe blockhash
            moved shouldBe rebuild(tx).withNewBlockhash(blockhash)
            SolanaTransactionUnsigned.deserializeMessage(moved.serializeMessage()).unwrap() shouldBe moved
        }
    }

    test("U8List is a List<Int> equal to its boxed counterpart, including indices above 127") {
        val bytes = ByteArray(256) { it.toByte() }
        val list = U8List(bytes)
        val boxed = (0..255).toList()
        list shouldBe boxed
        (boxed == list) shouldBe true
        list.hashCode() shouldBe boxed.hashCode()
        list[200] shouldBe 200
        list.indexOf(255) shouldBe 255
        list.contains(128) shouldBe true
        list.toString() shouldBe boxed.toString()
        MessageInstruction(1, U8List(byteArrayOf(0, -1)), byteArrayOf()) shouldBe MessageInstruction(1, listOf(0, 255), byteArrayOf())
    }

    test("duplicate accounts name the address whose first appearance comes earliest") {
        val a = SolanaAddress(ByteArray(32) { 1 })
        val b = SolanaAddress(ByteArray(32) { 2 })
        val c = SolanaAddress(ByteArray(32) { 3 })
        val error = SolanaTxLegacy.create(MessageHeader(1, 0, 0), listOf(a, b, c, c, b), SolanaBlockhash(ByteArray(32)), emptyList()).unwrapError()
        error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        error.reason shouldBe SolanaTransactionError.Reason.DUPLICATE_ACCOUNT
        error.message shouldBe "Account $b appears more than once"
    }

    test("duplicate detection has no false positives or misses across a full account table") {
        // addresses that differ in one byte only, so many of them share hash-table probe chains
        val accounts = List(256) { i -> SolanaAddress(ByteArray(32).also { it[31] = i.toByte() }) }
        // the structural checks alone, since 256 inline accounts exceed every packet size limit
        messageError(MessageHeader(1, 0, 0), accounts, emptyList(), emptyList()) shouldBe null
        for (duplicate in listOf(0, 17, 254)) {
            val repeated = accounts.take(255) + accounts[duplicate]
            val error = messageError(MessageHeader(1, 0, 0), repeated, emptyList(), emptyList())
            error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
            error.message shouldBe "Account ${accounts[duplicate]} appears more than once"
        }
    }

    test("little-endian reads cover the full unsigned range without BigInteger for small values") {
        SolanaMessageDecoder(ByteArray(8) { -1 }).readUnsignedLittleEndian(8) shouldBe BigInteger("18446744073709551615")
        SolanaMessageDecoder(byteArrayOf(0, 0, 0, 0, 0, 0, 0, -128)).readUnsignedLittleEndian(8) shouldBe BigInteger("9223372036854775808")
        SolanaMessageDecoder(byteArrayOf(1, 2)).readLittleEndianLong(2) shouldBe 0x0201L
        SolanaMessageDecoder(byteArrayOf(-1, -1, -1, -1)).readLittleEndianLong(4) shouldBe 0xffffffffL
        SolanaMessageDecoder(ByteArray(12) { 1 }).readUnsignedLittleEndian(12) shouldBe BigInteger(1, ByteArray(12) { 1 })

        val truncated = SolanaMessageDecoder(byteArrayOf(1, 2, 3))
        truncated.readLittleEndianLong(4) shouldBe 0L
        truncated.failed shouldBe true
    }

    test("a signature slot is empty only when all 64 bytes are zero") {
        val decoder = SolanaMessageDecoder(ByteArray(64) + ByteArray(64).also { it[63] = 1 })
        decoder.peekZeros(64) shouldBe true
        decoder.skip(64)
        decoder.peekZeros(64) shouldBe false
        decoder.peekZeros(65) shouldBe false
        decoder.readBytes(64)[63] shouldBe 1
        decoder.requireDone()
        decoder.failed shouldBe false
    }

    test("the encoder hands over an exactly filled buffer and copies a partly filled one") {
        val full = SolanaMessageEncoder(3).writeByte(1).writeZeros(1).writeLittleEndian(255L, 1)
        full.finish() shouldBe byteArrayOf(1, 0, -1)
        SolanaMessageEncoder(8).writeU8List(listOf(1, 200)).finish() shouldBe byteArrayOf(1, -56)
        SolanaMessageEncoder(8).writeU8List(U8List(byteArrayOf(3, 4))).writeLittleEndian(BigInteger("65535"), 2).finish() shouldBe byteArrayOf(3, 4, -1, -1)
        // growing past an exact capacity still works
        SolanaMessageEncoder(1).writeZeros(4).finish() shouldBe ByteArray(4)
    }
})
