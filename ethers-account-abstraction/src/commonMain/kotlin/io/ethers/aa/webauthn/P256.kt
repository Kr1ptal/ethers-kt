package io.ethers.aa.webauthn

import dev.whyoleg.cryptography.serialization.asn1.Der
import dev.whyoleg.cryptography.serialization.asn1.ObjectIdentifier
import dev.whyoleg.cryptography.serialization.asn1.modules.DssSignatureValue
import dev.whyoleg.cryptography.serialization.asn1.modules.EcAlgorithmIdentifier
import dev.whyoleg.cryptography.serialization.asn1.modules.SubjectPublicKeyInfo
import dev.whyoleg.cryptography.serialization.asn1.modules.secp256r1
import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A P-256 (secp256r1) public key, the key type of passkeys (WebAuthn `ES256`, COSE algorithm -7).
 */
data class P256PublicKey(val x: BigInteger, val y: BigInteger) {
    init {
        require(x.signum() >= 0 && x < P256.P && y.signum() >= 0 && y < P256.P) { "Coordinates out of range" }
    }

    /**
     * The 64-byte `x || y` encoding, used by passkey-enabled smart accounts to identify the owner.
     */
    fun toBytes(): Bytes = AbiCodec.encodePacked(listOf(AbiType.UInt(256), AbiType.UInt(256)), listOf(x, y))

    /**
     * The 65-byte uncompressed SEC1 encoding, `0x04 || x || y`.
     */
    fun toUncompressed(): ByteArray {
        return AbiCodec.encodePacked(listOf(AbiType.UInt(8), AbiType.UInt(256), AbiType.UInt(256)), listOf(UNCOMPRESSED_PREFIX, x, y))
            .asByteArray()
    }

    companion object {
        private val UNCOMPRESSED_PREFIX = bigIntegerOf(0x04)

        /**
         * Parse the 64-byte `x || y` or 65-byte uncompressed `0x04 || x || y` encoding.
         */
        @JvmStatic
        fun fromBytes(bytes: ByteArray): P256PublicKey {
            val offset = when {
                bytes.size == 64 -> 0
                bytes.size == 65 && bytes[0].toInt() == 0x04 -> 1
                else -> throw IllegalArgumentException("Expected a 64-byte or uncompressed 65-byte P-256 key, got ${bytes.size} bytes")
            }
            val (x, y) = AbiCodec.decode(listOf(AbiType.UInt(256), AbiType.UInt(256)), bytes.copyOfRange(offset, offset + 64))
            return P256PublicKey(x, y)
        }

        /**
         * Parse a DER-encoded SubjectPublicKeyInfo of a P-256 key, the format Android's Credential Manager and
         * browsers (`AuthenticatorAttestationResponse.getPublicKey()`) return for a new passkey.
         */
        @JvmStatic
        fun fromSpki(der: ByteArray): P256PublicKey {
            val spki = try {
                Der.decodeFromByteArray(SubjectPublicKeyInfo.serializer(), der)
            } catch (e: Exception) {
                throw IllegalArgumentException("Not a SubjectPublicKeyInfo", e)
            }
            val curve = (spki.algorithm as? EcAlgorithmIdentifier)?.parameters?.namedCurve
            require(curve == ObjectIdentifier.secp256r1) { "Not a P-256 key: ${spki.algorithm}" }
            return fromBytes(spki.subjectPublicKey.byteArray)
        }
    }
}

/**
 * A P-256 ECDSA signature.
 */
data class P256Signature(val r: BigInteger, val s: BigInteger) {
    /**
     * Whether `s` is in the lower half of the curve order. On-chain P-256 verifiers, like the RIP-7212 precompile
     * users and most smart account libraries, reject high-s signatures to prevent malleability.
     */
    val isLowS: Boolean
        get() = s <= P256.HALF_N

    /**
     * Return the equivalent signature with a low `s`, see [isLowS].
     */
    fun normalized(): P256Signature = if (isLowS) this else P256Signature(r, P256.N.subtract(s))

    /**
     * The 64-byte `r || s` encoding.
     */
    fun toBytes(): Bytes = AbiCodec.encodePacked(listOf(AbiType.UInt(256), AbiType.UInt(256)), listOf(r, s))

    companion object {
        /**
         * Parse the 64-byte `r || s` encoding.
         */
        @JvmStatic
        fun fromBytes(bytes: ByteArray): P256Signature {
            require(bytes.size == 64) { "Expected a 64-byte P-256 signature, got ${bytes.size} bytes" }
            val (r, s) = AbiCodec.decode(listOf(AbiType.UInt(256), AbiType.UInt(256)), bytes)
            return P256Signature(r, s)
        }

        /**
         * Parse a DER-encoded `SEQUENCE { INTEGER r, INTEGER s }`, the format authenticators sign in.
         */
        @JvmStatic
        fun fromDer(der: ByteArray): P256Signature {
            val value = try {
                Der.decodeFromByteArray(DssSignatureValue.serializer(), der)
            } catch (e: Exception) {
                throw IllegalArgumentException("Not a DER-encoded ECDSA signature", e)
            }
            // the decoder ignores trailing bytes, which a strict DER signature must not have
            require(Der.encodeToByteArray(DssSignatureValue.serializer(), value).size == der.size) { "Trailing bytes after DER signature" }
            require(value.r.sign > 0 && value.s.sign > 0) { "Signature values out of range" }

            val r = BigInteger(1, value.r.magnitudeToByteArray())
            val s = BigInteger(1, value.s.magnitudeToByteArray())
            require(r < P256.N && s < P256.N) { "Signature values out of range" }
            return P256Signature(r, s)
        }
    }
}

internal object P256 {
    @JvmField
    val P = BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951")

    @JvmField
    val N = BigInteger("115792089210356248762697446949407573529996955224135760342422259061068512044369")

    @JvmField
    val HALF_N: BigInteger = N.shiftRight(1)
}
