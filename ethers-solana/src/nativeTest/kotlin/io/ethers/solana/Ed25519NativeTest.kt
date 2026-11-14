package io.ethers.solana

import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * On Apple targets `verify` imports the address as an OpenSSL key. Off-curve addresses, PDAs among them, must
 * verify as false rather than throw, since `verify` runs inside the `Result`-returning transaction decoders.
 */
class Ed25519NativeTest : FunSpec({
    val signer = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val message = "message".encodeToByteArray()
    val signature = signer.signMessage(message)

    test("a signature verifies against its signer") {
        signer.publicKey.verify(signature, message) shouldBe true
    }

    test("an off-curve address verifies nothing, without throwing") {
        val pda = SolanaAddress.findProgramAddress(listOf("seed".encodeToByteArray()), Programs.SYSTEM).address
        pda.isOnCurve() shouldBe false
        pda.verify(signature, message) shouldBe false
        SolanaAddress(ByteArray(32) { -1 }).verify(signature, message) shouldBe false
    }
})
