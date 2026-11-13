package io.ethers.solana

import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.CallbackSigner
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.signMessage
import io.ethers.solana.types.toByteArray
import io.ethers.solana.types.toData
import io.ethers.solana.types.toNSData
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CallbackSignerTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })
    val tx = SolanaTxLegacy.compile(alice.publicKey, blockhash, SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L)).unwrap()

    test("signs with the signature the callback returns for the exact message bytes") {
        val messages = mutableListOf<ByteArray>()
        val external = CallbackSigner(alice.publicKey) { message ->
            messages += message.toByteArray()
            alice.signMessage(message).toData()
        }
        external.signTransaction(tx) shouldBe tx.sign(alice)
        tx.sign(listOf(external)) shouldBe tx.sign(alice)
        messages.forEach { it shouldBe tx.serializeMessage() }
        external.trySignMessage(tx.serializeMessage()).unwrap() shouldBe alice.signMessage(tx.serializeMessage())
    }

    test("a nil or malformed signature fails signing with IllegalArgumentException") {
        for (external in listOf(CallbackSigner(alice.publicKey) { null }, CallbackSigner(alice.publicKey) { ByteArray(63).toNSData() })) {
            shouldThrow<IllegalArgumentException> { tx.sign(listOf(external)) }
            shouldThrow<IllegalArgumentException> { external.signTransaction(tx) }
            shouldThrow<IllegalArgumentException> { tx.signingBuilder().sign(listOf(external)) }
            external.trySignMessage(tx.serializeMessage()).unwrapError().cause.shouldBeInstanceOf<IllegalArgumentException>()
        }
    }

    test("a failed signer leaves the builder's earlier signatures intact") {
        val builder = tx.signingBuilder().sign(listOf(alice))
        shouldThrow<IllegalArgumentException> { builder.sign(listOf(CallbackSigner(alice.publicKey) { null })) }
        builder.build().unwrap() shouldBe tx.sign(alice)
    }

    test("a signature that does not verify fails at build") {
        val wrong = CallbackSigner(alice.publicKey) { bob.signMessage(it).toData() }
        tx.signingBuilder().sign(listOf(wrong)).build().unwrapError().shouldBeInstanceOf<SolanaTransactionError.UnsignedSlots>()
    }
})
