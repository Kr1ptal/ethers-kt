package io.ethers.aa.webauthn

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ethers.aa.internal.concatBytes
import io.ethers.core.types.Bytes
import io.ethers.crypto.Hashing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The raw response of a WebAuthn authentication ceremony (`navigator.credentials.get`, Android Credential Manager
 * `GetCredentialRequest`, or iOS `ASAuthorizationPlatformPublicKeyCredentialAssertion`).
 *
 * @property credentialId the ID of the passkey that signed.
 * @property authenticatorData the authenticator data, starting with the RP ID hash and flags.
 * @property clientDataJSON the client data JSON, as the exact bytes the authenticator signed.
 * @property signature the DER-encoded ECDSA signature.
 */
class WebAuthnResponse(
    val credentialId: Bytes,
    val authenticatorData: Bytes,
    val clientDataJSON: ByteArray,
    val signature: ByteArray,
)

/**
 * Performs WebAuthn authentication ceremonies with a platform authenticator. Every platform has its own passkey
 * API, so apps implement this interface on top of it, e.g. with Android's Credential Manager or iOS
 * AuthenticationServices. [SoftwareWebAuthnAuthenticator] implements it in software, for tests.
 */
fun interface WebAuthnAuthenticator {
    /**
     * Ask the user to sign [challenge] with a passkey of relying party [rpId], one of [allowedCredentialIds] if not
     * empty, requiring user verification.
     */
    suspend fun getAssertion(rpId: String, challenge: ByteArray, allowedCredentialIds: List<Bytes>): WebAuthnResponse
}

/**
 * A parsed WebAuthn assertion, in the form smart account contracts verify on-chain: the authenticator data, the
 * client data JSON, the offsets of its `challenge` and `type` fields, and a low-s P-256 signature.
 *
 * @property challengeIndex offset of `"challenge":` in [clientDataJSON].
 * @property typeIndex offset of `"type":` in [clientDataJSON].
 */
data class WebAuthnAssertion(
    val credentialId: Bytes,
    val authenticatorData: Bytes,
    val clientDataJSON: String,
    val challengeIndex: Int,
    val typeIndex: Int,
    val signature: P256Signature,
) {
    /**
     * Whether the authenticator verified the user (the UV flag), e.g. with a biometric or PIN.
     */
    val userVerified: Boolean
        get() = flags and FLAG_USER_VERIFIED != 0

    /**
     * Whether the user was present (the UP flag).
     */
    val userPresent: Boolean
        get() = flags and FLAG_USER_PRESENT != 0

    private val flags: Int
        get() = if (authenticatorData.size > 32) authenticatorData.asByteArray()[32].toInt() and 0xFF else 0

    /**
     * The message the authenticator signed: `authenticatorData || sha256(clientDataJSON)`. ECDSA hashes it again
     * with SHA-256 before signing.
     */
    fun signedMessage(): ByteArray {
        return concatBytes(authenticatorData.asByteArray(), Hashing.sha256(clientDataJSON.encodeToByteArray()))
    }

    /**
     * Verify this assertion was made by [publicKey] for [challenge], and that the user was present and, if
     * [requireUserVerification], verified. This is what on-chain WebAuthn verifiers check.
     *
     * On-chain verifiers do not check the relying party, as the account's passkey only signs for its own. Pass
     * [rpId] and [origin] to also check that the assertion was made for that relying party and web origin.
     */
    @JvmOverloads
    fun verify(
        publicKey: P256PublicKey,
        challenge: ByteArray,
        requireUserVerification: Boolean = true,
        rpId: String? = null,
        origin: String? = null,
    ): Boolean {
        if (authenticatorData.size < 37 || !userPresent) return false
        if (requireUserVerification && !userVerified) return false
        if (rpId != null && !Hashing.sha256(rpId.encodeToByteArray()).contentEquals(authenticatorData.asByteArray().copyOfRange(0, 32))) {
            return false
        }

        val clientData = try {
            Json.parseToJsonElement(clientDataJSON).jsonObject
        } catch (e: Exception) {
            return false
        }
        if (clientData.string("type") != "webauthn.get") return false
        if (clientData.string("challenge") != WebAuthn.base64Url(challenge)) return false
        if (origin != null && clientData.string("origin") != origin) return false

        return WebAuthn.verifySignature(publicKey, signedMessage(), signature)
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        private const val FLAG_USER_PRESENT = 0x01
        private const val FLAG_USER_VERIFIED = 0x04

        /**
         * Parse a raw [response], normalizing its signature to low-s.
         *
         * @throws IllegalArgumentException if the response is malformed.
         */
        @JvmStatic
        fun fromResponse(response: WebAuthnResponse): WebAuthnAssertion {
            val clientDataJSON = response.clientDataJSON.decodeToString()
            val challengeIndex = clientDataJSON.indexOf("\"challenge\":")
            val typeIndex = clientDataJSON.indexOf("\"type\":")
            require(challengeIndex >= 0 && typeIndex >= 0) { "clientDataJSON has no challenge or type" }

            return WebAuthnAssertion(
                credentialId = response.credentialId,
                authenticatorData = response.authenticatorData,
                clientDataJSON = clientDataJSON,
                challengeIndex = challengeIndex,
                typeIndex = typeIndex,
                signature = P256Signature.fromDer(response.signature).normalized(),
            )
        }
    }
}

/**
 * WebAuthn helpers.
 */
object WebAuthn {
    private val ecdsa by lazy { CryptographyProvider.Default.get(ECDSA) }

    /**
     * Sign [challenge] with [authenticator] and parse the result, see [WebAuthnAssertion.fromResponse].
     */
    suspend fun sign(
        authenticator: WebAuthnAuthenticator,
        rpId: String,
        challenge: ByteArray,
        allowedCredentialIds: List<Bytes> = emptyList(),
    ): WebAuthnAssertion {
        return WebAuthnAssertion.fromResponse(authenticator.getAssertion(rpId, challenge, allowedCredentialIds))
    }

    /**
     * Verify the P-256 ECDSA [signature] of SHA-256([message]) by [publicKey].
     */
    @JvmStatic
    fun verifySignature(publicKey: P256PublicKey, message: ByteArray, signature: P256Signature): Boolean {
        return try {
            val key = ecdsa.publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, publicKey.toUncompressed())
            key.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)
                .tryVerifySignatureBlocking(message, signature.toBytes().asByteArray())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encode [data] as unpadded base64url, the encoding of the challenge in `clientDataJSON`.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @JvmStatic
    fun base64Url(data: ByteArray): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(data)
}
