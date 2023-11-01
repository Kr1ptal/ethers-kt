package io.ethers.crypto

import org.bouncycastle.asn1.x9.X9IntegerConverter
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve
import java.math.BigInteger

/**
 * Reference: https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/crypto/ECKey.java
 * */
object Secp256k1 {
    private val CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1")
    private val CURVE = ECDomainParameters(CURVE_PARAMS.curve, CURVE_PARAMS.g, CURVE_PARAMS.n, CURVE_PARAMS.h)
    private val CURVE_HALF_ORDER: BigInteger = CURVE_PARAMS.n shr 1
    private val FIXED_POINT_MULTIPLIER = FixedPointCombMultiplier()
    private val X9_CONVERTER = X9IntegerConverter()
    private const val UNCOMPRESSED_KEY_FLAG = (0x04).toByte()

    /**
     * Hash provided [publicKey] using Keccak-256 algorithm, and return last 20 bytes, which is the address.
     * */
    fun publicKeyToAddress(publicKey: ByteArray): ByteArray {
        if (publicKey[0] != UNCOMPRESSED_KEY_FLAG) {
            throw IllegalArgumentException("Public key is compressed")
        }

        // skip first byte (0x04) which indicates uncompressed public key
        val keccak = Keccak.Digest256().apply { update(publicKey, 1, publicKey.size - 1) }
        val hash = keccak.digest()
        return hash.copyOfRange(hash.size - 20, hash.size)
    }

    /**
     * Recover public key from hash original unsigned message and provided signature.
     *
     * This can be convenient when you have a message and a signature and want to find out who signed it,
     * rather than requiring the user to provide the expected identity.
     *
     * @param hash of the original unsigned message
     * @param r x-coordinate of random point R
     * @param s signature proof
     * @param recId index from 0 to 3 which indicates which of the 4 possible keys is the correct one
     */
    fun recoverPublicKey(hash: ByteArray, r: BigInteger, s: BigInteger, recId: Long): ByteArray? {
        if (recId < 0) {
            throw IllegalArgumentException("Parameter 'recId' must be positive.")
        }
        if (r < BigInteger.ZERO) {
            throw IllegalArgumentException("Parameter 'r' must be positive.")
        }
        if (s < BigInteger.ZERO) {
            throw IllegalArgumentException("Parameter 's' must be positive.")
        }

        // 1.0 For j from 0 to h   (h == recId here and the loop is outside this function)
        //   1.1 Let x = r + jn
        val n: BigInteger = CURVE.n // Curve order.
        val i = BigInteger.valueOf(recId / 2)
        val x: BigInteger = r.add(i.multiply(n))

        //   1.2. Convert the integer x to an octet string X of length mlen using the conversion
        //        routine specified in Section 2.3.7, where mlen = ⌈(log2 p)/8⌉ or mlen = ⌈m/8⌉.
        //   1.3. Convert the octet string (16 set binary digits)||X to an elliptic curve point R
        //        using the conversion routine specified in Section 2.3.4. If this conversion
        //        routine outputs "invalid", then do another iteration of Step 1.
        //
        // More concisely, what these points mean is to use X as a compressed public key.
        val prime = SecP256K1Curve.q
        if (x >= prime) {
            // Cannot have point co-ordinates larger than this as everything takes place modulo Q.
            return null
        }

        // Compressed keys require you to know an extra bit of data about the y-coord as there are
        // two possibilities. So it's encoded in the recId.
        val R: ECPoint = decompressKey(x, recId and 1 == 1L)

        //   1.4. If nR != point at infinity, then do another iteration of Step 1 (callers
        //        responsibility).
        if (!R.multiply(n).isInfinity) {
            return null
        }

        //   1.5. Compute e from M using Steps 2 and 3 of ECDSA signature verification.
        val e = BigInteger(1, hash)

        //   1.6. For k from 1 to 2 do the following.   (loop is outside this function via
        //        iterating recId)
        //   1.6.1. Compute a candidate public key as:
        //               Q = mi(r) * (sR - eG)
        //
        // Where mi(x) is the modular multiplicative inverse. We transform this into the following:
        //               Q = (mi(r) * s ** R) + (mi(r) * -e ** G)
        // Where -e is the modular additive inverse of e, that is z such that z + e = 0 (mod n).
        // In the above equation ** is point multiplication and + is point addition (the EC group
        // operator).
        //
        // We can find the additive inverse by subtracting e from zero then taking the mod. For
        // example the additive inverse of 3 modulo 11 is 8 because 3 + 8 mod 11 = 0, and
        // -3 mod 11 = 8.
        val eInv = BigInteger.ZERO.subtract(e).mod(n)
        val rInv: BigInteger = r.modInverse(n)
        val srInv = rInv.multiply(s).mod(n)
        val eInvrInv = rInv.multiply(eInv).mod(n)
        val q = ECAlgorithms.sumOfTwoMultiplies(CURVE.g, eInvrInv, R, srInv)

        return q.getEncoded(false)
    }

    private fun decompressKey(xBN: BigInteger, yBit: Boolean): ECPoint {
        val compEnc = X9_CONVERTER.integerToBytes(xBN, 1 + X9_CONVERTER.getByteLength(CURVE.curve))
        compEnc[0] = (if (yBit) 0x03 else 0x02).toByte()

        return CURVE.curve.decodePoint(compEnc)
    }

    /**
     * Helper object to encapsulate ECDSA hash message signing logic.
     */
    class SigningKey(privateKey: BigInteger) {
        constructor(privateKey: ByteArray) : this(BigInteger(1, privateKey))

        private val privateKey = ECPrivateKeyParameters(privateKey, CURVE)

        val publicKey: ByteArray

        init {
            @Suppress("NAME_SHADOWING")
            var privateKey = privateKey
            if (privateKey.bitLength() > CURVE.n.bitLength()) {
                privateKey = privateKey.mod(CURVE.n)
            }

            val point = FIXED_POINT_MULTIPLIER.multiply(CURVE.g, privateKey)
            val encoded = point.getEncoded(false)
            if (encoded[0].toInt() != 0x04) {
                throw IllegalArgumentException("Invalid encoded point. Expected 0x04 prefix, got ${encoded[0]}")
            }

            this.publicKey = encoded
        }

        /**
         * Sign [hash] message and return its signature.
         */
        fun signHash(hash: ByteArray): Array<BigInteger> {
            val signer = ECDSASignerRecoverable(HMacDSAKCalculator(SHA256Digest()))
            signer.init(true, privateKey)

            // r, s, v
            val sig = signer.generateSignatureWithY(hash)
            var yIsEven = sig[2].and(BigInteger.ONE) == BigInteger.ZERO

            // first, canonicalize the "s", which should fall in the lower half of the curve, and invert the 'yIsEven'
            if (sig[1] > CURVE_HALF_ORDER) {
                sig[1] = CURVE.n.subtract(sig[1])
                yIsEven = !yIsEven
            }

            // second, select the correct recovery id ("v") for the signature. It will be 0 or 1 since
            // we canonicalized the "s" value above.
            // see for explanation: https://ethereum.stackexchange.com/a/118342
            val v = if (yIsEven) BigInteger.ZERO else BigInteger.ONE
            sig[2] = v

            return sig
        }
    }
}
