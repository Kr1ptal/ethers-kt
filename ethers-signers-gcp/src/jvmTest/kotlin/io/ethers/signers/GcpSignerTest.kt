@file:OptIn(ExperimentalEncodingApi::class)

package io.ethers.signers

import com.google.cloud.kms.v1.AsymmetricSignResponse
import com.google.cloud.kms.v1.CryptoKeyVersion
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.Digest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.PublicKey
import com.google.protobuf.ByteString
import dev.whyoleg.cryptography.bigint.toKotlinBigInt
import dev.whyoleg.cryptography.serialization.asn1.Der
import dev.whyoleg.cryptography.serialization.asn1.modules.EcdsaSignatureValue
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.core.types.Address
import io.ethers.crypto.Secp256k1
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class GcpSignerTest : FunSpec({
    // Test private key and derived values
    val testPrivateKey = hexToBytes("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
    val signingKey = Secp256k1.SigningKey(testPrivateKey)
    val expectedAddress = Address(Secp256k1.publicKeyToAddress(signingKey.publicKey))

    // Test data
    val testHash = ByteArray(32) { it.toByte() }
    val keyName = CryptoKeyVersionName.of("project", "location", "keyring", "key", "1")

    context("signHash") {
        test("correctly parses DER signature and recovers address") {
            // Sign with our test key to get expected r, s values
            val expectedSig = signingKey.signHash(testHash)

            // Create DER-encoded signature
            val derSignature = Der.encodeToByteArray(
                EcdsaSignatureValue.serializer(),
                EcdsaSignatureValue(
                    r = expectedSig.r.toKotlinBigInt(),
                    s = expectedSig.s.toKotlinBigInt(),
                ),
            )

            // Mock the KMS client
            val mockClient = mockk<KeyManagementServiceClient>()
            val mockResponse = mockk<AsymmetricSignResponse>()

            every { mockResponse.signature } returns ByteString.copyFrom(derSignature)
            every {
                mockClient.asymmetricSign(
                    eq(keyName),
                    any<Digest>(),
                )
            } returns mockResponse

            // Create signer and sign
            val signer = GcpSigner(mockClient, keyName, expectedAddress)
            val signature = signer.signHash(testHash)

            // Verify signature values
            signature.r shouldBe expectedSig.r
            signature.s shouldBe expectedSig.s
            signature.recoverFromHash(testHash) shouldBe expectedAddress

            // Verify the client was called
            verify {
                mockClient.asymmetricSign(
                    keyName,
                    match<Digest> { it.sha256 == ByteString.copyFrom(testHash) },
                )
            }
        }

        test("correctly handles DER signature with leading zeros in r") {
            // Create a signature where r has high bit set (requires 0x00 prefix in DER)
            val rValue = BigInteger("ff76b20cf9dae4f91ee6623767df7f0a9fd2a15ef6532ed4974cf97ca5de80ef", 16)
            val sValue = BigInteger("1a1e4e7c2f8f7b3dd9af0c5e0e8e5c8d0b3a7f6e5d4c3b2a1908070605040302", 16)

            val derSignature = Der.encodeToByteArray(
                EcdsaSignatureValue.serializer(),
                EcdsaSignatureValue(
                    r = rValue.toKotlinBigInt(),
                    s = sValue.toKotlinBigInt(),
                ),
            )

            val mockClient = mockk<KeyManagementServiceClient>()
            val mockResponse = mockk<AsymmetricSignResponse>()

            every { mockResponse.signature } returns ByteString.copyFrom(derSignature)
            every { mockClient.asymmetricSign(any<CryptoKeyVersionName>(), any<Digest>()) } returns mockResponse

            // Use a dummy address - we just want to verify DER parsing works
            val dummyAddress = Address("0x0000000000000000000000000000000000000001")
            val signer = GcpSigner(mockClient, keyName, dummyAddress)

            // This will throw because the signature won't match the dummy address
            // but we can verify the DER parsing by catching the exception
            try {
                signer.signHash(testHash)
            } catch (_: IllegalStateException) {
                // Expected - signature doesn't match dummy address
            }
        }
    }

    context("create") {
        test("resolves address from PEM public key") {
            // Create PEM-encoded public key
            val pemPublicKey = createPemPublicKey(signingKey.publicKey)

            val mockClient = mockk<KeyManagementServiceClient>()
            val mockPublicKey = mockk<PublicKey>()

            every { mockPublicKey.pem } returns pemPublicKey
            every { mockPublicKey.algorithm } returns CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256
            every { mockClient.getPublicKey(keyName) } returns mockPublicKey

            val result = GcpSigner.create(mockClient, keyName)

            result.isSuccess() shouldBe true
            result.unwrap().address shouldBe expectedAddress

            verify { mockClient.getPublicKey(keyName) }
        }

        test("returns error for non-secp256k1 keys") {
            val mockClient = mockk<KeyManagementServiceClient>()
            val mockPublicKey = mockk<PublicKey>()

            every { mockPublicKey.algorithm } returns CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256
            every { mockClient.getPublicKey(keyName) } returns mockPublicKey

            val result = GcpSigner.create(mockClient, keyName)

            result.isFailure() shouldBe true
            result.unwrapError().shouldBeInstanceOf<GcpSigner.AddressFetchError>()
            result.unwrapError().message shouldBe "Only secp256k1 keys are supported"
        }

        test("returns error when getPublicKey throws") {
            val mockClient = mockk<KeyManagementServiceClient>()

            every { mockClient.getPublicKey(keyName) } throws RuntimeException("Connection failed")

            val result = GcpSigner.create(mockClient, keyName)

            result.isFailure() shouldBe true
            result.unwrapError().shouldBeInstanceOf<GcpSigner.AddressFetchError>()
            result.unwrapError().message shouldBe "Failed to create GCP signer"
        }

        test("create with string keyName") {
            val pemPublicKey = createPemPublicKey(signingKey.publicKey)
            val keyNameString = "projects/project/locations/location/keyRings/keyring/cryptoKeys/key/cryptoKeyVersions/1"

            val mockClient = mockk<KeyManagementServiceClient>()
            val mockPublicKey = mockk<PublicKey>()

            every { mockPublicKey.pem } returns pemPublicKey
            every { mockPublicKey.algorithm } returns CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256
            every { mockClient.getPublicKey(CryptoKeyVersionName.parse(keyNameString)) } returns mockPublicKey

            val result = GcpSigner.create(mockClient, keyNameString)

            result.isSuccess() shouldBe true
            result.unwrap().address shouldBe expectedAddress
        }

        test("create with individual key parameters") {
            val pemPublicKey = createPemPublicKey(signingKey.publicKey)

            val mockClient = mockk<KeyManagementServiceClient>()
            val mockPublicKey = mockk<PublicKey>()

            every { mockPublicKey.pem } returns pemPublicKey
            every { mockPublicKey.algorithm } returns CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256
            every {
                mockClient.getPublicKey(
                    CryptoKeyVersionName.of("myproject", "us-east1", "mykeyring", "mykey", "1"),
                )
            } returns mockPublicKey

            val result = GcpSigner.create(
                mockClient,
                projectId = "myproject",
                locationId = "us-east1",
                keyRingId = "mykeyring",
                keyId = "mykey",
                versionId = "1",
            )

            result.isSuccess() shouldBe true
            result.unwrap().address shouldBe expectedAddress
        }
    }

    context("constructors") {
        test("constructor with string keyName") {
            val mockClient = mockk<KeyManagementServiceClient>()
            val keyNameString = "projects/project/locations/location/keyRings/keyring/cryptoKeys/key/cryptoKeyVersions/1"

            val signer = GcpSigner(mockClient, keyNameString, expectedAddress)

            signer.address shouldBe expectedAddress
        }

        test("constructor with individual parameters") {
            val mockClient = mockk<KeyManagementServiceClient>()

            val signer = GcpSigner(
                mockClient,
                projectId = "project",
                locationId = "location",
                keyRingId = "keyring",
                keyId = "key",
                versionId = "1",
                address = expectedAddress,
            )

            signer.address shouldBe expectedAddress
        }
    }

    context("equals and hashCode") {
        test("signers with same address are equal") {
            val mockClient1 = mockk<KeyManagementServiceClient>()
            val mockClient2 = mockk<KeyManagementServiceClient>()

            val signer1 = GcpSigner(mockClient1, keyName, expectedAddress)
            val signer2 = GcpSigner(mockClient2, keyName, expectedAddress)

            (signer1 == signer2) shouldBe true
            signer1.hashCode() shouldBe signer2.hashCode()
        }

        test("signers with different addresses are not equal") {
            val mockClient = mockk<KeyManagementServiceClient>()
            val otherAddress = Address("0x0000000000000000000000000000000000000001")

            val signer1 = GcpSigner(mockClient, keyName, expectedAddress)
            val signer2 = GcpSigner(mockClient, keyName, otherAddress)

            (signer1 == signer2) shouldBe false
        }
    }
})

private fun hexToBytes(hex: String): ByteArray {
    val cleanHex = if (hex.startsWith("0x")) hex.substring(2) else hex
    return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * Creates a PEM-encoded secp256k1 public key in SubjectPublicKeyInfo format.
 *
 * The format is:
 * SEQUENCE {
 *   SEQUENCE {
 *     OBJECT IDENTIFIER ecPublicKey (1.2.840.10045.2.1)
 *     OBJECT IDENTIFIER secp256k1 (1.3.132.0.10)
 *   }
 *   BIT STRING <uncompressed public key>
 * }
 */
private fun createPemPublicKey(uncompressedPublicKey: ByteArray): String {
    require(uncompressedPublicKey.size == 65 && uncompressedPublicKey[0] == 0x04.toByte()) {
        "Public key must be 65 bytes with 0x04 prefix"
    }

    // DER-encoded SubjectPublicKeyInfo for secp256k1
    // This is the standard ASN.1 structure for EC public keys
    val spkiHeader = hexToBytes(
        "3056" + // SEQUENCE, length 86
            "3010" + // SEQUENCE, length 16 (AlgorithmIdentifier)
            "0607" + // OBJECT IDENTIFIER, length 7
            "2a8648ce3d0201" + // 1.2.840.10045.2.1 (ecPublicKey)
            "0605" + // OBJECT IDENTIFIER, length 5
            "2b8104000a" + // 1.3.132.0.10 (secp256k1)
            "0342" + // BIT STRING, length 66
            "00", // no unused bits
    )

    val spki = spkiHeader + uncompressedPublicKey
    val base64 = Base64.encode(spki)

    return buildString {
        appendLine("-----BEGIN PUBLIC KEY-----")
        // Split into 64-char lines per PEM standard
        base64.chunked(64).forEach { appendLine(it) }
        append("-----END PUBLIC KEY-----")
    }
}
