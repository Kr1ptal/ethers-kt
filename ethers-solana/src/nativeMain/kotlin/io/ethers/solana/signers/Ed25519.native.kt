package io.ethers.solana.signers

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EdDSA

internal actual object Ed25519 {
    private val algorithm get() = CryptographyProvider.Default.get(EdDSA)
    private fun privateKey(seed: ByteArray) = algorithm.privateKeyDecoder(EdDSA.Curve.Ed25519)
        .decodeFromByteArrayBlocking(EdDSA.PrivateKey.Format.RAW, seed)

    actual fun publicKey(seed: ByteArray): ByteArray = privateKey(seed).getPublicKeyBlocking().encodeToByteArrayBlocking(EdDSA.PublicKey.Format.RAW)
    actual fun sign(seed: ByteArray, message: ByteArray): ByteArray = privateKey(seed).signatureGenerator().generateSignatureBlocking(message)
    actual fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        // any 32 bytes reach here, PDAs included; a key OpenSSL will not import verifies nothing rather than
        // throwing out of `verify` and the Result-returning transaction decoders built on it
        val key = try {
            algorithm.publicKeyDecoder(EdDSA.Curve.Ed25519).decodeFromByteArrayBlocking(EdDSA.PublicKey.Format.RAW, publicKey)
        } catch (_: Exception) {
            return false
        }
        return key.signatureVerifier().tryVerifySignatureBlocking(message, signature)
    }
}
