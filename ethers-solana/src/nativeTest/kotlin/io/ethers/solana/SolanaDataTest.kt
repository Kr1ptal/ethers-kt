package io.ethers.solana

import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.deserialize
import io.ethers.solana.types.deserializeMessage
import io.ethers.solana.types.deserializePartial
import io.ethers.solana.types.fromData
import io.ethers.solana.types.fromSecretKey
import io.ethers.solana.types.fromSeed
import io.ethers.solana.types.serializeData
import io.ethers.solana.types.serializeMessageData
import io.ethers.solana.types.serializePartialData
import io.ethers.solana.types.toByteArray
import io.ethers.solana.types.toData
import io.ethers.solana.types.toNSData
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionException
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SolanaDataTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })
    val tx = SolanaTxLegacy.compile(alice.publicKey, blockhash, SystemProgram.transfer(bob.publicKey, alice.publicKey, 42L)).unwrap()

    test("NSData conversions copy the bytes both ways") {
        val bytes = ByteArray(300) { it.toByte() }
        bytes.toNSData().toByteArray() shouldBe bytes
        ByteArray(0).toNSData().toByteArray() shouldBe ByteArray(0)
    }

    test("primitives round trip through NSData") {
        SolanaAddress.fromData(alice.publicKey.toData()) shouldBe alice.publicKey
        SolanaBlockhash.fromData(blockhash.toData()) shouldBe blockhash
        val signature = alice.signMessage(tx.serializeMessage())
        SolanaSignature.fromData(signature.toData()) shouldBe signature
        SolanaBytes.fromData(ByteArray(5) { 7 }.toNSData()) shouldBe SolanaBytes.fromBytes(ByteArray(5) { 7 })
        SolanaBytes.fromData(ByteArray(0).toNSData()) shouldBe SolanaBytes.EMPTY
    }

    test("primitives reject NSData of the wrong length") {
        shouldThrow<IllegalArgumentException> { SolanaAddress.fromData(ByteArray(31).toNSData()) }
        shouldThrow<IllegalArgumentException> { SolanaSignature.fromData(ByteArray(63).toNSData()) }
        shouldThrow<IllegalArgumentException> { SolanaBlockhash.fromData(ByteArray(0).toNSData()) }
        shouldThrow<IllegalArgumentException> { KeypairSigner.fromSeed(ByteArray(31).toNSData()) }
    }

    test("keypairs import from NSData") {
        KeypairSigner.fromSeed(ByteArray(32) { 1 }.toNSData()) shouldBe alice
        KeypairSigner.fromSecretKey(alice.toSecretKey().toNSData()) shouldBe alice
    }

    test("a partially signed envelope is completed and serialized through NSData") {
        val partial = tx.signingBuilder().sign(bob).serializePartialData()
        val builder = SolanaTransactionSigned.Builder.deserializePartial(partial).unwrap()
        val signature = SolanaSignature.fromData(alice.signMessage(tx.serializeMessageData().toByteArray()).toData())
        val signed = builder.addSignature(alice.publicKey, signature).build().unwrap()

        val expected = tx.sign(alice, bob).serialize()
        signed.serializeData().toByteArray() shouldBe expected
        SolanaTransactionSigned.deserialize(expected.toNSData()).unwrap() shouldBe signed
        SolanaTransactionCompiled.deserialize(expected.toNSData()).unwrap() shouldBe signed
        SolanaTransactionUnsigned.deserializeMessage(tx.serializeMessageData()).unwrap() shouldBe tx
    }

    test("a malformed envelope fails with its error reachable from the exception") {
        val result = SolanaTransactionSigned.Builder.deserializePartial(ByteArray(3).toNSData())
        val e = shouldThrow<SolanaTransactionException> { result.unwrap() }
        e.error.shouldBeInstanceOf<SolanaTransactionError>()
    }

    test("an unknown signer is rejected as IllegalArgumentException") {
        val stranger = KeypairSigner.fromSeed(ByteArray(32) { 9 })
        shouldThrow<IllegalArgumentException> { tx.signingBuilder().sign(stranger) }
        shouldThrow<IllegalArgumentException> { tx.signingBuilder().addSignature(stranger.publicKey, stranger.signMessage(tx.serializeMessage())) }
    }
})
