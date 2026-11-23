package io.ethers.aa.webauthn

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.core.types.Bytes
import io.ethers.crypto.Hashing
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * A [WebAuthnAuthenticator] holding a single P-256 passkey in memory. It produces assertions in the same format as
 * real authenticators, so passkey-owned accounts can be exercised in tests and against local chains without a
 * device.
 *
 * Not for production use: the private key lives in process memory, while real passkeys never leave secure hardware.
 *
 * @param origin the web origin written into `clientDataJSON`, e.g. `https://example.com`.
 */
class SoftwareWebAuthnAuthenticator(
    private val origin: String,
    val credentialId: Bytes = Bytes(Hashing.generateRandomBytes(16)),
) : WebAuthnAuthenticator {
    private val keyPair = CryptographyProvider.Default.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
    private var signCount = 0

    /**
     * The passkey's public key, which a passkey-owned account registers as its owner.
     */
    val publicKey: P256PublicKey = P256PublicKey.fromBytes(keyPair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW))

    override suspend fun getAssertion(rpId: String, challenge: ByteArray, allowedCredentialIds: List<Bytes>): WebAuthnResponse {
        require(allowedCredentialIds.isEmpty() || credentialId in allowedCredentialIds) { "Credential not allowed" }

        signCount++
        // rpIdHash || flags (user present, user verified) || uint32 signCount
        val authenticatorData = AbiCodec.encodePacked(
            listOf(AbiType.FixedBytes(32), AbiType.UInt(8), AbiType.UInt(32)),
            listOf(Bytes(Hashing.sha256(rpId.encodeToByteArray())), FLAGS, bigIntegerOf(signCount.toLong())),
        ).asByteArray()
        val clientDataJSON = """{"type":"webauthn.get","challenge":"${WebAuthn.base64Url(challenge)}","origin":"$origin","crossOrigin":false}"""
            .encodeToByteArray()

        val message = authenticatorData + Hashing.sha256(clientDataJSON)
        val signature = keyPair.privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.DER).generateSignatureBlocking(message)

        return WebAuthnResponse(credentialId, Bytes(authenticatorData), clientDataJSON, signature)
    }

    private companion object {
        val FLAGS = bigIntegerOf(0x05)
    }
}
