package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.corpus.transactionCorpus
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.LoadedAddresses
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.bigIntegerOf
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TransactionCorpusTest : FunSpec({
    val fixtures = transactionCorpus().map { Kotlinx.DEFAULT.parseToJsonElement(it).jsonObject }
    val liveFixtures = fixtures.filter { it.getValue("cluster").jsonPrimitive.content != "localnet" }
    val json = Json(Kotlinx.DEFAULT) { encodeDefaults = true }

    test("committed live corpus has 320 legacy and 320 v0 samples across historical periods; v1 capture is still pending") {
        // Local-validator samples are deliberately excluded from public-chain counts.
        liveFixtures.size shouldBe 640
        fixtures.size shouldBe 840
        fixtures.map { it.getValue("signature") }.distinct().size shouldBe 840
        liveFixtures.groupingBy { it.getValue("rpc").jsonObject.getValue("version").jsonPrimitive.content }.eachCount() shouldBe mapOf("legacy" to 320, "0" to 320)
        liveFixtures.all { it.getValue("cluster").jsonPrimitive.content in setOf("mainnet", "testnet", "devnet") } shouldBe true
        liveFixtures.map { it.getValue("blockhash") }.distinct().size.let { it >= 16 } shouldBe true
        for (version in listOf("legacy", "0")) {
            val slots = fixtures.filter { it.getValue("rpc").jsonObject.getValue("version").jsonPrimitive.content == version }
                .map { it.getValue("slot").jsonPrimitive.content.toLong() }
            slots.count { it in 299_990_000L..300_000_000L } shouldBe 40
            slots.count { it in 399_990_000L..400_000_000L } shouldBe 40
            slots.count { it in 439_990_000L..440_000_000L } shouldBe 40
        }
    }

    test("live corpus includes failures, multiple signers, CPI, return data, and v0 with and without lookups") {
        val transactions = liveFixtures.map { json.decodeFromJsonElement<SolanaRPCTransaction>(it.getValue("rpc")) }
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

    test("200 local v1 fixtures cover signer and instruction counts, large envelopes and optional config") {
        val local = fixtures.filter { it.getValue("cluster").jsonPrimitive.content == "localnet" }
        local.size shouldBe 200
        local.all { it.getValue("origin").jsonPrimitive.content == "generated-local-validator" } shouldBe true
        val transactions = local.map { SolanaTransactionSigned.fromBase64(it.getValue("wire").jsonPrimitive.content).unwrap() }
        transactions.all { it.type == SolanaTxType.V1 } shouldBe true
        transactions.map { it.signatures.size }.toSet() shouldBe (1..12).toSet()
        transactions.any { it.instructions.size == 64 } shouldBe true
        transactions.any { it.serialize().size > 3900 } shouldBe true
        transactions.any { it.serialize().size <= 1232 } shouldBe true
        val configs = transactions.map { (it.tx as SolanaTxV1).config }
        configs.any { it.priorityFee == null } shouldBe true
        configs.any { it.priorityFee?.toString() == "0" } shouldBe true
        configs.map { it.heapSize }.toSet() shouldBe setOf(null, 32768L, 65536L, 262144L)
    }

    for (fixture in fixtures) {
        val signature = fixture.getValue("signature").jsonPrimitive.content
        val rawRpc = fixture.getValue("rpc").jsonObject
        val version = rawRpc.getValue("version").jsonPrimitive.content
        test("${fixture.getValue("cluster").jsonPrimitive.content} $version $signature: wire, RPC, signatures, reconstruction and metadata") {
            val rpc = json.decodeFromJsonElement<SolanaRPCTransaction>(rawRpc)
            val signed = SolanaTransactionSigned.fromBase64(fixture.getValue("wire").jsonPrimitive.content).unwrap()
            val tx = signed.tx
            rpc.slot.toString() shouldBe fixture.getValue("slot").jsonPrimitive.content
            signed.id.toString() shouldBe signature
            signed.type shouldBe rpc.type
            signed.signatures shouldBe rpc.transaction.signatures
            signed.toBase64() shouldBe fixture.getValue("wire").jsonPrimitive.content
            SolanaTransactionCompiled.deserialize(signed.serialize()).unwrap().serializeForSimulation() shouldBe signed.serialize()
            val decodedMessage = SolanaTransactionUnsigned.deserializeMessage(signed.serializeMessage()).unwrap()
            decodedMessage.serializeMessage() shouldBe signed.serializeMessage()

            // Reconstruct from independent RPC JSON fields, never from the binary decoder's fields.
            val message = rpc.transaction.message
            val reconstructed = message.toTransaction(rpc.type).unwrap()
            reconstructed.serializeMessage() shouldBe signed.serializeMessage()
            rpc.toUnsignedTransaction().unwrap().serializeMessage() shouldBe signed.serializeMessage()
            rpc.toSignedTransaction().unwrap().serialize() shouldBe signed.serialize()
            signed.accounts shouldBe message.accountKeys
            signed.header shouldBe message.header
            signed.recentBlockhash shouldBe message.recentBlockhash
            signed.signers.size shouldBe signed.signatures.size
            if (tx is SolanaTxV1) {
                signed.serialize().first() shouldBe 129.toByte()
                message.addressTableLookups shouldBe emptyList()
                tx.config shouldBe message.transactionConfig
                rpc.meta!!.fee shouldBe tx.estimateFee(bigIntegerOf(5000))
            } else {
                message.transactionConfig shouldBe null
            }

            // Resolving the message back to addresses is a true inverse: recompiling the recovered
            // request and resolving that again yields the same instructions, accounts and flags.
            // Bytes are not compared, since these were compiled elsewhere and may order accounts
            // differently within a header group than this library's canonical sort.
            if (rpc.addressLookupTables.isEmpty() || rpc.meta?.loadedAddresses != null) {
                val recovered = rpc.toRequest().unwrap()
                recovered.feePayer shouldBe signed.feePayer
                recovered.blockhash shouldBe signed.recentBlockhash
                recovered.instructions.size shouldBe signed.instructions.size

                // rebuild the tables this message drew on, so the recompiled v0 can move the same
                // accounts back out of the inline list and still fit the envelope
                val tables = rebuildLookupTables(rpc.addressLookupTables, rpc.meta?.loadedAddresses)
                val recompiled = when (rpc.type) {
                    SolanaTxType.Legacy -> recovered.compileLegacy().unwrap()
                    SolanaTxType.V1 -> recovered.compileV1().unwrap()
                    else -> recovered.compileV0(tables).unwrap()
                }
                recompiled.toRequest(tables).unwrap().instructions shouldBe recovered.instructions
            }

            // Validate every signature through import, preserve partial slots, and reject corruption.
            val partial = SolanaTransactionSigned.Builder(tx, signed.signatures.mapIndexed { i, value -> if (i == 0) null else value })
            val imported = SolanaTransactionSigned.Builder.deserializePartial(partial.serializePartial()).unwrap()
            imported.missingSigners shouldBe listOf(signed.feePayer)
            imported.addSignature(signed.feePayer, signed.id).build().unwrap().serialize() shouldBe signed.serialize()
            val badSignature = signed.id.toByteArray().also { it[0] = (it[0].toInt() xor 1).toByte() }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned(tx, listOf(SolanaSignature(badSignature)) + signed.signatures.drop(1)) }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(signed.serialize().dropLast(1).toByteArray()).unwrap() }
            shouldThrow<IllegalArgumentException> { SolanaTransactionSigned.deserialize(signed.serialize() + byteArrayOf(0)).unwrap() }

            // unknown fields are re-emitted as the text they arrived as, so the wire string is what
            // carries their structure; parse it back before comparing trees
            val encoded = json.parseToJsonElement(json.encodeToString(rpc))
            assertCorpusJsonPreserved(rawRpc, encoded)
            json.parseToJsonElement(json.encodeToString(json.decodeFromJsonElement<SolanaRPCTransaction>(encoded))) shouldBe encoded

            rpc.meta?.let { meta ->
                val loaded = meta.loadedAddresses
                val writableCount = message.addressTableLookups.sumOf { it.writableIndexes.size }
                val readonlyCount = message.addressTableLookups.sumOf { it.readonlyIndexes.size }
                (loaded?.writable?.size ?: 0) shouldBe writableCount
                (loaded?.readonly?.size ?: 0) shouldBe readonlyCount
                val accountCount = message.accountKeys.size + writableCount + readonlyCount
                meta.preBalances.size shouldBe accountCount
                meta.postBalances.size shouldBe accountCount
                message.instructions.all { ix -> ix.programIdIndex in 0 until accountCount && ix.accounts.all { it in 0 until accountCount } } shouldBe true
                meta.innerInstructions?.forEach { group ->
                    (group.index in message.instructions.indices) shouldBe true
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

/**
 * Reconstruct the lookup tables a message drew on, from the slots it names and the addresses the node
 * resolved for them. Slots the message does not name are filled with addresses derived from the table
 * key, which are never referenced but keep the resolved ones at their original indexes.
 */
private fun rebuildLookupTables(lookups: List<CompiledAddressLookupTable>, loaded: LoadedAddresses?): List<AddressLookupTableAccount> {
    if (loaded == null) return emptyList()
    var writable = 0
    var readonly = 0
    return lookups.map { lookup ->
        val slots = lookup.writableIndexes.map { it to loaded.writable[writable++] } + lookup.readonlyIndexes.map { it to loaded.readonly[readonly++] }
        val size = (slots.maxOfOrNull { it.first } ?: -1) + 1
        val addresses = MutableList(size) { index ->
            SolanaAddress(lookup.key.asByteArray().copyOf().also { it[0] = index.toByte() })
        }
        slots.forEach { (index, address) -> addresses[index] = address }
        AddressLookupTableAccount(lookup.key, addresses)
    }
}
