@file:OptIn(ExperimentalEncodingApi::class)

package io.ethers.signers

import com.google.cloud.kms.v1.CryptoKeyVersion
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.Digest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import dev.whyoleg.cryptography.bigint.toJavaBigInteger
import dev.whyoleg.cryptography.serialization.asn1.Der
import dev.whyoleg.cryptography.serialization.asn1.modules.EcdsaSignatureValue
import dev.whyoleg.cryptography.serialization.asn1.modules.SubjectPublicKeyInfo
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Signature
import io.ethers.crypto.Secp256k1
import io.ethers.signers.GcpSigner.Companion.create
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Google Cloud Key Management Service (GCP KMS) signer.
 *
 * The signer calls into GCP KMS to sign the hashes. The signer is configured with a [CryptoKeyVersionName]
 * which is used to identify the key to use. The key must be a secp256k1 key.
 *
 * To create a signer by automatically looking up the address, use [create] functions. Note that these functions
 * will do an external call to GCP to fetch the signer's public key, from which an address is resolved.
 *
 * Example usage:
 * ```kotlin
 * // create a client - can be reused for multiple signers
 * val client = KeyManagementServiceClient.create()
 *
 * // define the key name
 * val projectId = "my-project"
 * val locationId = "global"
 * val keyRingId = "my-keyring"
 * val keyId = "my-key"
 * val versionId = "1"
 * val keyName = CryptoKeyVersionName.of(projectId, locationId, keyRingId, keyId, versionId)
 *
 * // create a signer by automatically resolving the address by doing an external call to GCP
 * val signer = GcpSigner.create(client, keyName).unwrap()
 * val signature = signer.signHash(ByteArray(32))
 * ```
 * */
class GcpSigner(
    private val client: KeyManagementServiceClient,
    private val keyName: CryptoKeyVersionName,
    override val address: Address,
) : Signer {

    constructor(client: KeyManagementServiceClient, keyName: String, address: Address) : this(
        client,
        CryptoKeyVersionName.parse(keyName),
        address,
    )

    constructor(
        client: KeyManagementServiceClient,
        projectId: String,
        locationId: String,
        keyRingId: String,
        keyId: String,
        versionId: String,
        address: Address,
    ) : this(client, CryptoKeyVersionName.of(projectId, locationId, keyRingId, keyId, versionId), address)

    override fun signHash(hash: ByteArray): Signature {
        val response = client.asymmetricSign(
            keyName,
            Digest.newBuilder().setSha256(ByteString.copyFrom(hash)).build(),
        )

        val sig = Der.decodeFromByteArray(EcdsaSignatureValue.serializer(), response.signature.toByteArray())
        val r = sig.r.toJavaBigInteger()
        val s = sig.s.toJavaBigInteger()

        for (v in 0..3L) {
            val signature = Signature(r, s, v)
            if (signature.recoverFromHash(hash) == address) {
                return signature
            }
        }

        throw IllegalStateException("Failed to derive recovery id from signature")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GcpSigner

        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    data class AddressFetchError(val message: String, val cause: Throwable? = null) : Result.Error {
        override fun doThrow(): Nothing {
            throw RuntimeException(message, cause)
        }
    }

    companion object {
        /**
         * Create a new instance of [io.ethers.signers.GcpSigner] from the provided [keyName]. Does an external
         * call via [client] to resolve the key [Address].
         *
         * @return [Result] with either created [io.ethers.signers.GcpSigner], or an error if resolving the address failed.
         * */
        @JvmStatic
        fun create(
            client: KeyManagementServiceClient,
            keyName: CryptoKeyVersionName,
        ): Result<GcpSigner, AddressFetchError> {
            return try {
                val publicKey = client.getPublicKey(keyName)
                if (publicKey.algorithm != CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256) {
                    return failure(AddressFetchError("Only secp256k1 keys are supported"))
                }

                // Parse PEM-encoded public key
                val pubKeyBase64 = publicKey.pem
                    .lineSequence()
                    .filter { !it.startsWith("-----") && it.isNotBlank() }
                    .joinToString("")

                val spki = Der.decodeFromByteArray(SubjectPublicKeyInfo.serializer(), Base64.decode(pubKeyBase64))
                val pubKeyUncompressed = spki.subjectPublicKey.byteArray

                val address = Address(Secp256k1.publicKeyToAddress(pubKeyUncompressed))
                success(GcpSigner(client, keyName, address))
            } catch (e: Exception) {
                failure(AddressFetchError("Failed to create GCP signer", e))
            }
        }

        /**
         * Create a new instance of [io.ethers.signers.GcpSigner] from the provided [keyName]. Does an external
         * call via [client] to resolve the key [Address].
         *
         * @return [Result] with either created [io.ethers.signers.GcpSigner], or an error if resolving the address failed.
         * */
        @JvmStatic
        fun create(client: KeyManagementServiceClient, keyName: String): Result<GcpSigner, AddressFetchError> {
            return create(client, CryptoKeyVersionName.parse(keyName))
        }

        /**
         * Create a new instance of [io.ethers.signers.GcpSigner] from the provided key parameters. Does an external
         * call via [client] to resolve the key [Address].
         *
         * @return [Result] with either created [io.ethers.signers.GcpSigner], or an error if resolving the address failed.
         * */
        @JvmStatic
        fun create(
            client: KeyManagementServiceClient,
            projectId: String,
            locationId: String,
            keyRingId: String,
            keyId: String,
            versionId: String,
        ): Result<GcpSigner, AddressFetchError> {
            return create(client, CryptoKeyVersionName.of(projectId, locationId, keyRingId, keyId, versionId))
        }
    }
}
