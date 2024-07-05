@file:OptIn(ExperimentalEncodingApi::class)

package io.ethers.signers

import com.google.cloud.kms.v1.CryptoKeyVersion
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.Digest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import io.ethers.core.types.Address
import io.ethers.core.types.Signature
import io.ethers.crypto.Secp256k1
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class GcpSigner(private val client: KeyManagementServiceClient, private val keyName: CryptoKeyVersionName) : Signer {
    override val address: Address

    constructor(client: KeyManagementServiceClient, keyName: String) : this(client, CryptoKeyVersionName.parse(keyName))

    constructor(
        client: KeyManagementServiceClient,
        projectId: String, locationId: String, keyRingId: String, keyId: String, versionId: String,
    ) : this(client, CryptoKeyVersionName.of(projectId, locationId, keyRingId, keyId, versionId))

    init {
        val publicKey = client.getPublicKey(keyName)
        if (publicKey.algorithm != CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256) {
            throw IllegalArgumentException("Only secp256k1 keys are supported")
        }

        val pubKeyBase64 = publicKey.pem
            .split("\n")
            .filter { !it.startsWith("-----") && it.isNotEmpty() }
            .joinToString("")

        val keyInfo = SubjectPublicKeyInfo.getInstance(Base64.decode(pubKeyBase64))
        val parsedKey = KeyFactorySpi.ECDSA().generatePublic(keyInfo) as BCECPublicKey
        val pubKeyUncompressed = parsedKey.q.normalize().getEncoded(false)

        address = Address(Secp256k1.publicKeyToAddress(pubKeyUncompressed))
    }

    override fun signHash(hash: ByteArray): Signature {
        val response = client.asymmetricSign(
            keyName,
            Digest.newBuilder().setSha256(ByteString.copyFrom(hash)).build()
        )

        val derSequence = (DERSequence.fromByteArray(response.signature.toByteArray()) as DLSequence)
        val r = (derSequence.getObjectAt(0) as ASN1Integer).value
        val s = (derSequence.getObjectAt(1) as ASN1Integer).value
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
}
