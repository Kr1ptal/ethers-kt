package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.corpus.liveTransactionCorpus
import io.ethers.solana.types.SolanaRPCTransaction
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.transaction.CompiledInstruction
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.BigDecimal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LiveTransactionCorpusTest : FunSpec({
    val fixtures = liveTransactionCorpus().map { Kotlinx.DEFAULT.parseToJsonElement(it).jsonObject }
    val json = Json(Kotlinx.DEFAULT) { encodeDefaults = true }

    test("committed live corpus has 200 legacy and 200 v0 samples; v1 capture is still pending") {
        // See resources/transactions/README.md: do not substitute synthetic v1 fixtures here.
        fixtures.size shouldBe 400
        fixtures.map { it.getValue("signature") }.distinct().size shouldBe 400
        fixtures.groupingBy { it.getValue("rpc").jsonObject.getValue("version").jsonPrimitive.content }.eachCount() shouldBe mapOf("legacy" to 200, "0" to 200)
        fixtures.all { it.getValue("cluster").jsonPrimitive.content in setOf("mainnet", "testnet", "devnet") } shouldBe true
        fixtures.map { it.getValue("blockhash") }.distinct().size.let { it >= 10 } shouldBe true
    }

    test("live corpus includes failures, multiple signers, CPI, return data, and v0 with and without lookups") {
        val transactions = fixtures.map { json.decodeFromJsonElement<SolanaRPCTransaction>(it.getValue("rpc")) }
        for (group in transactions.groupBy { it.type }.values) {
            group.any { it.meta?.err != null } shouldBe true
            group.any { it.transaction.signatures.size > 1 } shouldBe true
            group.any { !it.meta?.innerInstructions.isNullOrEmpty() } shouldBe true
            group.any { it.meta?.returnData != null } shouldBe true
        }
        val v0 = transactions.filter { it.type == SolanaTxType.V0 }
        v0.any { it.transaction.message.addressTableLookups.isNotEmpty() } shouldBe true
        v0.any { it.transaction.message.addressTableLookups.isEmpty() } shouldBe true
    }

    for (fixture in fixtures) {
        val signature = fixture.getValue("signature").jsonPrimitive.content
        val rawRpc = fixture.getValue("rpc").jsonObject
        val version = rawRpc.getValue("version").jsonPrimitive.content
        test("live $version $signature: wire, RPC, signatures, reconstruction and metadata") {
            val rpc = json.decodeFromJsonElement<SolanaRPCTransaction>(rawRpc)
            val signed = SolanaTransactionSigned.fromBase64(fixture.getValue("wire").jsonPrimitive.content)
            val tx = signed.tx
            rpc.slot.toString() shouldBe fixture.getValue("slot").jsonPrimitive.content
            signed.id.toString() shouldBe signature
            signed.type shouldBe rpc.type
            signed.signatures shouldBe rpc.transaction.signatures
            signed.toBase64() shouldBe fixture.getValue("wire").jsonPrimitive.content
            SolanaTransaction.deserialize(signed.serialize()).serializeForSimulation() shouldBe signed.serialize()
            val decodedMessage = SolanaTransactionUnsigned.deserializeMessage(signed.serializeMessage())
            decodedMessage.serializeMessage() shouldBe signed.serializeMessage()

            // Reconstruct from independent RPC JSON fields, never from the binary decoder's fields.
            val message = rpc.transaction.message
            val instructions = message.instructions.map { CompiledInstruction(it.programIdIndex, it.accounts, it.data.toByteArray()) }
            val reconstructed = when (rpc.type) {
                SolanaTxType.Legacy -> SolanaTxLegacy(message.header, message.accountKeys, message.recentBlockhash, instructions)
                SolanaTxType.V0 -> SolanaTxV0(message.header, message.accountKeys, message.recentBlockhash, instructions, message.addressTableLookups)
                SolanaTxType.V1 -> SolanaTxV1(message.header, message.accountKeys, message.recentBlockhash, instructions, requireNotNull(message.transactionConfig))
                is SolanaTxType.Unsupported -> error("Unexpected corpus version")
            }
            reconstructed.serializeMessage() shouldBe signed.serializeMessage()
            SolanaTransactionSigned(reconstructed, rpc.transaction.signatures).serialize() shouldBe signed.serialize()
            signed.accounts shouldBe message.accountKeys
            signed.header shouldBe message.header
            signed.recentBlockhash shouldBe message.recentBlockhash
            signed.signers.size shouldBe signed.signatures.size
            if (tx is SolanaTxV1) {
                signed.serialize().first() shouldBe 129.toByte()
                message.addressTableLookups shouldBe emptyList()
                tx.config shouldBe message.transactionConfig
            } else {
                message.transactionConfig shouldBe null
            }

            // Validate every signature through import, preserve partial slots, and reject corruption.
            val partial = SolanaTransactionSigned.Builder(tx, signed.signatures.mapIndexed { i, value -> if (i == 0) null else value })
            val imported = SolanaTransactionSigned.Builder.deserializePartial(partial.serializePartial())
            imported.missingSigners shouldBe listOf(signed.feePayer)
            imported.addSignature(signed.feePayer, signed.id).build().serialize() shouldBe signed.serialize()
            val badSignature = signed.id.toByteArray().also { it[0] = (it[0].toInt() xor 1).toByte() }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned(tx, listOf(SolanaSignature(badSignature)) + signed.signatures.drop(1)) }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(signed.serialize().dropLast(1).toByteArray()) }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(signed.serialize() + byteArrayOf(0)) }

            val encoded = json.encodeToJsonElement(rpc)
            assertCorpusJsonPreserved(rawRpc, encoded)
            json.encodeToJsonElement(json.decodeFromJsonElement<SolanaRPCTransaction>(encoded)) shouldBe encoded

            rpc.meta?.let { meta ->
                val loaded = meta.loadedAddresses
                val writableCount = message.addressTableLookups.sumOf { it.writableIndexes.size }
                val readonlyCount = message.addressTableLookups.sumOf { it.readonlyIndexes.size }
                (loaded?.writable?.size ?: 0) shouldBe writableCount
                (loaded?.readonly?.size ?: 0) shouldBe readonlyCount
                val accountCount = message.accountKeys.size + writableCount + readonlyCount
                meta.preBalances.size shouldBe accountCount
                meta.postBalances.size shouldBe accountCount
                instructions.all { ix -> ix.programIdIndex in 0 until accountCount && ix.accounts.all { it in 0 until accountCount } } shouldBe true
                meta.innerInstructions?.forEach { group ->
                    (group.index in instructions.indices) shouldBe true
                    group.instructions.forEach { ix ->
                        (ix.programIdIndex in 0 until accountCount) shouldBe true
                        ix.accounts.all { it in 0 until accountCount } shouldBe true
                    }
                }
                (meta.preTokenBalances.orEmpty() + meta.postTokenBalances.orEmpty()).all { it.accountIndex in 0 until accountCount } shouldBe true
            }
        }
    }
})

/** Defaults may add fields; every captured value, including unknown fields, must survive semantically. */
private fun assertCorpusJsonPreserved(original: JsonElement, encoded: JsonElement) {
    when (original) {
        is JsonObject -> original.forEach { (key, value) -> assertCorpusJsonPreserved(value, encoded.jsonObject.getValue(key)) }
        is JsonArray -> {
            encoded as JsonArray
            encoded.size shouldBe original.size
            original.indices.forEach { assertCorpusJsonPreserved(original[it], encoded[it]) }
        }
        is JsonPrimitive -> {
            val other = encoded.jsonPrimitive
            if (original == JsonNull || original.isString || original.content in setOf("true", "false")) {
                other shouldBe original
            } else {
                other.isString shouldBe false
                BigDecimal(original.content).compareTo(BigDecimal(other.content)) shouldBe 0
            }
        }
    }
}
