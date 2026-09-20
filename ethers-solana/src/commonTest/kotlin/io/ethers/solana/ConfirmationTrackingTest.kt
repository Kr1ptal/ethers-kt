package io.ethers.solana

import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.ConfirmationTracking
import io.ethers.solana.providers.confirmationTracking
import io.ethers.solana.providers.decodeConfirmationTracking
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class ConfirmationTrackingTest : FunSpec({
    val signer = KeypairSigner.fromSeed(ByteArray(32) { 7 })
    val hash = SolanaBlockhash(ByteArray(32) { 2 })

    fun request(nonce: Boolean = false) = SolanaTransactionRequest {
        feePayer(signer.publicKey)
        blockhash(hash)
        if (nonce) instruction(SystemProgram.advanceNonceAccount(Programs.SYSVAR_RENT, signer.publicKey))
        instruction(SystemProgram.transfer(signer.publicKey, Programs.SYSTEM, 1L))
    }

    test("all wire versions agree with typed confirmation hints and reject every truncation") {
        for (nonce in listOf(false, true)) {
            val request = request(nonce)
            val versions = listOf(request.compileLegacy().unwrap(), request.compileV0().unwrap(), request.compileV1().unwrap())
            for (tx in versions) {
                val wire = tx.sign(signer).serialize()
                val expected = if (nonce) ConfirmationTracking.DurableNonce(Programs.SYSVAR_RENT, hash) else ConfirmationTracking.Blockhash(hash)
                tx.confirmationTracking() shouldBe expected
                decodeConfirmationTracking(wire) shouldBe expected
                for (length in wire.indices) {
                    decodeConfirmationTracking(wire.copyOf(length)) shouldBe ConfirmationTracking.StatusOnly
                }
                decodeConfirmationTracking(wire + byteArrayOf(0)) shouldBe ConfirmationTracking.StatusOnly
            }
        }
    }

    test("v1 config survives raw confirmation decoding") {
        val tx = request().apply {
            priorityFee(bigIntegerOf(5))
            computeUnitLimit(1000)
            loadedAccountsDataSizeLimit(1024)
            heapSize(32768)
        }.compileV1().unwrap().sign(signer)
        decodeConfirmationTracking(tx.serialize()) shouldBe ConfirmationTracking.Blockhash(hash)
    }

    test("v0 lookup sections preserve raw confirmation tracking") {
        val recipient = SolanaAddress(ByteArray(32) { 9 })
        val secondRecipient = SolanaAddress(ByteArray(32) { 10 })
        val table = AddressLookupTableAccount(Programs.SYSVAR_RENT, listOf(recipient, secondRecipient))
        val tx = SolanaTransactionRequest {
            feePayer(signer.publicKey)
            blockhash(hash)
            instruction(SystemProgram.transfer(signer.publicKey, recipient, 1L))
            instruction(SystemProgram.transfer(signer.publicKey, secondRecipient, 1L))
        }.compileV0(listOf(table)).unwrap()
        tx.addressLookupTables.size shouldBe 1
        val wire = tx.sign(signer).serialize()
        decodeConfirmationTracking(wire) shouldBe ConfirmationTracking.Blockhash(hash)
        decodeConfirmationTracking(wire.copyOf(wire.size - 1)) shouldBe ConfirmationTracking.StatusOnly
    }

    test("nonce addresses in unresolved lookup tables retain status-only tracking") {
        val nonce = SolanaAddress(ByteArray(32) { 9 })
        val recipient = SolanaAddress(ByteArray(32) { 10 })
        val table = AddressLookupTableAccount(Programs.SYSVAR_RENT, listOf(nonce, recipient))
        val tx = SolanaTransactionRequest {
            feePayer(signer.publicKey)
            blockhash(hash)
            instruction(SystemProgram.advanceNonceAccount(nonce, signer.publicKey))
            instruction(SystemProgram.transfer(signer.publicKey, recipient, 1L))
        }.compileV0(listOf(table)).unwrap()
        tx.addressLookupTables.size shouldBe 1
        tx.confirmationTracking() shouldBe ConfirmationTracking.StatusOnly
        decodeConfirmationTracking(tx.sign(signer).serialize()) shouldBe ConfirmationTracking.StatusOnly
    }

    test("raw confirmation decoding does not verify signatures") {
        val wire = request().compileLegacy().unwrap().sign(signer).serialize()
        wire[1] = (wire[1].toInt() xor 1).toByte()
        decodeConfirmationTracking(wire) shouldBe ConfirmationTracking.Blockhash(hash)
    }

    test("unsupported and oversized envelopes use status-only tracking") {
        val wire = request().compileLegacy().unwrap().sign(signer).serialize()
        wire[65] = 130.toByte()
        decodeConfirmationTracking(wire) shouldBe ConfirmationTracking.StatusOnly
        decodeConfirmationTracking(ByteArray(4097)) shouldBe ConfirmationTracking.StatusOnly
        decodeConfirmationTracking(byteArrayOf(128.toByte(), 0)) shouldBe ConfirmationTracking.StatusOnly
    }

    test("arbitrary bytes never throw during confirmation decoding") {
        val random = Random(31)
        repeat(1000) { decodeConfirmationTracking(random.nextBytes(random.nextInt(0, 4097))) }
    }
})
