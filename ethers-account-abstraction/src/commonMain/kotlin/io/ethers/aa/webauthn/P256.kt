package io.ethers.aa.webauthn

import io.ethers.aa.internal.bytesToUint
import io.ethers.aa.internal.concatBytes
import io.ethers.aa.internal.uintToBytes
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
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
    fun toBytes(): Bytes = Bytes(concatBytes(uintToBytes(x, 32), uintToBytes(y, 32)))

    /**
     * The 65-byte uncompressed SEC1 encoding, `0x04 || x || y`.
     */
    fun toUncompressed(): ByteArray = concatBytes(byteArrayOf(0x04), uintToBytes(x, 32), uintToBytes(y, 32))

    companion object {
        // DER prefix of a SubjectPublicKeyInfo of an uncompressed P-256 key: SEQUENCE { SEQUENCE { id-ecPublicKey,
        // prime256v1 }, BIT STRING { 0x00 } }, followed by the 65-byte uncompressed point
        private val SPKI_PREFIX = byteArrayOf(
            0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
            0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
        )

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
            return P256PublicKey(bytesToUint(bytes, offset, offset + 32), bytesToUint(bytes, offset + 32, offset + 64))
        }

        /**
         * Parse a DER-encoded SubjectPublicKeyInfo of a P-256 key, the format Android's Credential Manager and
         * browsers (`AuthenticatorAttestationResponse.getPublicKey()`) return for a new passkey.
         */
        @JvmStatic
        fun fromSpki(der: ByteArray): P256PublicKey {
            require(der.size == SPKI_PREFIX.size + 65) { "Not a P-256 SubjectPublicKeyInfo: ${der.size} bytes" }
            for (i in SPKI_PREFIX.indices) {
                require(der[i] == SPKI_PREFIX[i]) { "Not a P-256 SubjectPublicKeyInfo" }
            }
            return fromBytes(der.copyOfRange(SPKI_PREFIX.size, der.size))
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
    fun toBytes(): Bytes = Bytes(concatBytes(uintToBytes(r, 32), uintToBytes(s, 32)))

    companion object {
        /**
         * Parse the 64-byte `r || s` encoding.
         */
        @JvmStatic
        fun fromBytes(bytes: ByteArray): P256Signature {
            require(bytes.size == 64) { "Expected a 64-byte P-256 signature, got ${bytes.size} bytes" }
            return P256Signature(bytesToUint(bytes, 0, 32), bytesToUint(bytes, 32, 64))
        }

        /**
         * Parse a DER-encoded `SEQUENCE { INTEGER r, INTEGER s }`, the format authenticators sign in.
         */
        @JvmStatic
        fun fromDer(der: ByteArray): P256Signature {
            val reader = DerReader(der)
            reader.expectTag(0x30)
            val seqLength = reader.readLength()
            require(seqLength == der.size - reader.position) { "Invalid DER sequence length" }
            val r = reader.readInteger()
            val s = reader.readInteger()
            require(reader.position == der.size) { "Trailing bytes after DER signature" }
            require(r.signum() > 0 && r < P256.N && s.signum() > 0 && s < P256.N) { "Signature values out of range" }
            return P256Signature(r, s)
        }
    }

    private class DerReader(private val data: ByteArray) {
        var position = 0

        fun expectTag(tag: Int) {
            require(position < data.size && data[position].toInt() and 0xFF == tag) { "Expected DER tag $tag" }
            position++
        }

        fun readLength(): Int {
            require(position < data.size) { "Truncated DER" }
            val first = data[position++].toInt() and 0xFF
            if (first < 0x80) return first
            require(first == 0x81) { "Unsupported DER length" }
            require(position < data.size) { "Truncated DER" }
            return data[position++].toInt() and 0xFF
        }

        fun readInteger(): BigInteger {
            expectTag(0x02)
            val length = readLength()
            require(length in 1..33 && position + length <= data.size) { "Invalid DER integer" }
            val value = BigInteger(1, data.copyOfRange(position, position + length))
            position += length
            return value
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
