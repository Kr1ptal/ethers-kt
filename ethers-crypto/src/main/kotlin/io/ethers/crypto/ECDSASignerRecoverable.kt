package io.ethers.crypto

import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.CryptoServicePurpose
import org.bouncycastle.crypto.CryptoServicesRegistrar
import org.bouncycastle.crypto.DSAExt
import org.bouncycastle.crypto.constraints.ConstraintUtils
import org.bouncycastle.crypto.constraints.DefaultServiceProperties
import org.bouncycastle.crypto.params.ECKeyParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.ParametersWithRandom
import org.bouncycastle.crypto.signers.DSAKCalculator
import org.bouncycastle.crypto.signers.RandomDSAKCalculator
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECConstants
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECFieldElement
import org.bouncycastle.math.ec.ECMultiplier
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.SecureRandom

/**
 * EC-DSA as described in X9.62
 *
 * IMPORTANT: This class has been modified from the original source code to allow returning the 'y' value. Code
 * should be kept in sync with the original source code.
 *
 * Original: [org.bouncycastle.crypto.signers.ECDSASigner]
 */
internal class ECDSASignerRecoverable : ECConstants, DSAExt {
    private val kCalculator: DSAKCalculator
    private var key: ECKeyParameters? = null
    private var random: SecureRandom? = null

    /**
     * Default configuration, random K values.
     */
    constructor() {
        kCalculator = RandomDSAKCalculator()
    }

    /**
     * Configuration with an alternate, possibly deterministic calculator of K.
     *
     * @param kCalculator a K value calculator.
     */
    constructor(kCalculator: DSAKCalculator) {
        this.kCalculator = kCalculator
    }

    override fun init(forSigning: Boolean, param: CipherParameters) {
        var providedRandom: SecureRandom? = null
        if (forSigning) {
            if (param is ParametersWithRandom) {
                val rParam = param
                key = rParam.parameters as ECPrivateKeyParameters
                providedRandom = rParam.random
            } else {
                key = param as ECPrivateKeyParameters
            }
        } else {
            key = param as ECPublicKeyParameters
        }

        // inlined the following method since it's in package-private class:
        // Utils.getDefaultProperties("ECDSA", key, forSigning)
        val props = DefaultServiceProperties(
            "ECDSA",
            ConstraintUtils.bitsOfSecurityFor(key!!.parameters.curve),
            key,
            if (forSigning) CryptoServicePurpose.SIGNING else CryptoServicePurpose.VERIFYING,
        )

        CryptoServicesRegistrar.checkConstraints(props)
        random = initSecureRandom(forSigning && !kCalculator.isDeterministic, providedRandom)
    }

    override fun getOrder(): BigInteger {
        return key!!.parameters.n
    }

    // 5.3 pg 28

    /**
     * generate a signature for the given message using the key we were
     * initialised with. For conventional DSA the message should be a SHA-1
     * hash of the message of interest.
     *
     * @param message the message that will be verified later.
     */
    override fun generateSignature(message: ByteArray): Array<BigInteger> {
        return generateSignatureInternal(message, false)
    }

    fun generateSignatureWithY(message: ByteArray): Array<BigInteger> {
        return generateSignatureInternal(message, true)
    }

    private fun generateSignatureInternal(message: ByteArray, includeY: Boolean): Array<BigInteger> {
        val ec = key!!.parameters
        val n = ec.n
        val e = calculateE(n, message)
        val d = (key as ECPrivateKeyParameters?)!!.d
        if (kCalculator.isDeterministic) {
            kCalculator.init(n, d, message)
        } else {
            kCalculator.init(n, random)
        }
        var r: BigInteger
        var s: BigInteger
        val basePointMultiplier = createBasePointMultiplier()

        // ################## CUSTOM CODE START ##################
        var p: ECPoint?
        // ################## CUSTOM CODE END ####################

        // 5.3.2
        do // generate s
        {
            var k: BigInteger?
            do // generate r
            {
                k = kCalculator.nextK()
                p = basePointMultiplier.multiply(ec.g, k).normalize()

                // 5.3.3
                r = p.affineXCoord.toBigInteger().mod(n)
            } while (r == ECConstants.ZERO)
            s = BigIntegers.modOddInverse(n, k).multiply(e.add(d.multiply(r))).mod(n)
        } while (s == ECConstants.ZERO)

        // ################## CUSTOM CODE START ##################
        if (includeY) {
            return arrayOf(r, s, p!!.affineYCoord.toBigInteger())
        }
        // ################## CUSTOM CODE END ####################

        return arrayOf(r, s)
    }

    // 5.4 pg 29

    /**
     * return true if the value r and s represent a DSA signature for
     * the passed in message (for standard DSA the message should be
     * a SHA-1 hash of the real message to be verified).
     */
    override fun verifySignature(
        message: ByteArray,
        r: BigInteger,
        s: BigInteger,
    ): Boolean {
        var r = r
        val ec = key!!.parameters
        val n = ec.n
        val e = calculateE(n, message)

        // r in the range [1,n-1]
        if (r.compareTo(ECConstants.ONE) < 0 || r.compareTo(n) >= 0) {
            return false
        }

        // s in the range [1,n-1]
        if (s.compareTo(ECConstants.ONE) < 0 || s.compareTo(n) >= 0) {
            return false
        }
        val c = BigIntegers.modOddInverseVar(n, s)
        val u1 = e.multiply(c).mod(n)
        val u2 = r.multiply(c).mod(n)
        val G = ec.g
        val Q = (key as ECPublicKeyParameters?)!!.q
        val point = ECAlgorithms.sumOfTwoMultiplies(G, u1, Q, u2)

        // components must be bogus.
        if (point.isInfinity) {
            return false
        }

        /*
         * If possible, avoid normalizing the point (to save a modular inversion in the curve field).
         *
         * There are ~cofactor elements of the curve field that reduce (modulo the group order) to 'r'.
         * If the cofactor is known and small, we generate those possible field values and project each
         * of them to the same "denominator" (depending on the particular projective coordinates in use)
         * as the calculated point.X. If any of the projected values matches point.X, then we have:
         *     (point.X / Denominator mod p) mod n == r
         * as required, and verification succeeds.
         *
         * Based on an original idea by Gregory Maxwell (https://github.com/gmaxwell), as implemented in
         * the libsecp256k1 project (https://github.com/bitcoin/secp256k1).
         */
        val curve = point.curve
        if (curve != null) {
            val cofactor = curve.cofactor
            if (cofactor != null && cofactor.compareTo(ECConstants.EIGHT) <= 0) {
                val D = getDenominator(curve.coordinateSystem, point)
                if (D != null && !D.isZero) {
                    val X = point.xCoord
                    while (curve.isValidFieldElement(r)) {
                        val R = curve.fromBigInteger(r).multiply(D)
                        if (R == X) {
                            return true
                        }
                        r = r.add(n)
                    }
                    return false
                }
            }
        }
        val v = point.normalize().affineXCoord.toBigInteger().mod(n)
        return v == r
    }

    protected fun calculateE(n: BigInteger, message: ByteArray): BigInteger {
        val log2n = n.bitLength()
        val messageBitLength = message.size * 8
        var e = BigInteger(1, message)
        if (log2n < messageBitLength) {
            e = e.shiftRight(messageBitLength - log2n)
        }
        return e
    }

    protected fun createBasePointMultiplier(): ECMultiplier {
        return MULTIPLIER
    }

    protected fun getDenominator(coordinateSystem: Int, p: ECPoint): ECFieldElement? {
        return when (coordinateSystem) {
            ECCurve.COORD_HOMOGENEOUS, ECCurve.COORD_LAMBDA_PROJECTIVE, ECCurve.COORD_SKEWED -> p.getZCoord(0)
            ECCurve.COORD_JACOBIAN, ECCurve.COORD_JACOBIAN_CHUDNOVSKY, ECCurve.COORD_JACOBIAN_MODIFIED -> p.getZCoord(0)
                .square()

            else -> null
        }
    }

    protected fun initSecureRandom(needed: Boolean, provided: SecureRandom?): SecureRandom? {
        return if (needed) CryptoServicesRegistrar.getSecureRandom(provided) else null
    }
}

private val MULTIPLIER = FixedPointCombMultiplier()
