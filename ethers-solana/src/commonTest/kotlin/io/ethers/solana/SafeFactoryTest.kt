package io.ethers.solana

import io.ethers.crypto.Base58
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaDecodingError
import io.ethers.solana.types.SolanaSignature
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The companion factories return a [io.ethers.core.Result] where the constructors throw. */
class SafeFactoryTest : FunSpec({
    val address = SolanaAddress("11111111111111111111111111111112")
    val signature = SolanaSignature(ByteArray(64) { it.toByte() })
    val blockhash = SolanaBlockhash(ByteArray(32) { 9 })

    test("valid input decodes to the value the constructor builds") {
        SolanaAddress.fromBase58(address.toBase58()).unwrap() shouldBe address
        SolanaAddress.fromBytes(address.toByteArray()).unwrap() shouldBe address
        SolanaSignature.fromBase58(signature.toBase58()).unwrap() shouldBe signature
        SolanaSignature.fromBytes(signature.toByteArray()).unwrap() shouldBe signature
        SolanaBlockhash.fromBase58(blockhash.toBase58()).unwrap() shouldBe blockhash
        SolanaBlockhash.fromBytes(blockhash.toByteArray()).unwrap() shouldBe blockhash
    }

    test("text outside the Base58 alphabet fails, naming the value") {
        for (text in listOf("0", "O", "I", "l", "not base58!", "é")) {
            SolanaAddress.fromBase58(text).unwrapError().message shouldContain "Address is not valid Base58"
            SolanaSignature.fromBase58(text).unwrapError().message shouldContain "Signature is not valid Base58"
            SolanaBlockhash.fromBase58(text).unwrapError().message shouldContain "Blockhash is not valid Base58"
        }
    }

    test("the wrong length fails, from text or bytes") {
        SolanaAddress.fromBase58("").unwrapError() shouldBe SolanaDecodingError("Address must contain 32 bytes, got 0")
        SolanaAddress.fromBase58(Base58.encode(ByteArray(33) { 1 })).unwrapError() shouldBe SolanaDecodingError("Address must contain 32 bytes, got 33")
        SolanaAddress.fromBytes(ByteArray(31)).unwrapError() shouldBe SolanaDecodingError("Address must contain 32 bytes, got 31")
        SolanaSignature.fromBase58(address.toBase58()).unwrapError() shouldBe SolanaDecodingError("Signature must contain 64 bytes, got 32")
        SolanaSignature.fromBytes(ByteArray(65)).unwrapError() shouldBe SolanaDecodingError("Signature must contain 64 bytes, got 65")
        SolanaBlockhash.fromBase58(signature.toBase58()).unwrapError() shouldBe SolanaDecodingError("Blockhash must contain 32 bytes, got 64")
        SolanaBlockhash.fromBytes(ByteArray(0)).unwrapError() shouldBe SolanaDecodingError("Blockhash must contain 32 bytes, got 0")
    }

    test("fromBytes takes ownership of the array, as the constructor does") {
        val bytes = ByteArray(32) { 4 }
        SolanaAddress.fromBytes(bytes).unwrap().asByteArray() shouldBe bytes
        (SolanaAddress.fromBytes(bytes).unwrap().asByteArray() === bytes) shouldBe true
    }
})
