package io.ethers.core.types

import io.ethers.core.FastHex
import io.ethers.core.asAddress
import io.ethers.core.asHexBigInteger
import io.ethers.core.asHexLong
import io.ethers.core.getOrNull
import io.ethers.crypto.Hashing
import io.ethers.crypto.Secp256k1
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger

/**
 * EIP-7702 Authorization structure for SetCode transactions.
 * Each authorization allows a specific address to execute code on behalf of the authority.
 */
@Serializable(with = AuthorizationSerializer::class)
data class Authorization(
    val chainId: Long,
    val address: Address,
    val nonce: Long,
    val yParity: Long,
    val r: BigInteger,
    val s: BigInteger,
) : RlpEncodable {
    init {
        require(chainId == 0L || chainId > 0L) { "chainId must be 0 or positive" }
        require(nonce >= 0L) { "nonce must be non-negative" }
        require(yParity == 0L || yParity == 1L) { "yParity must be 0 or 1" }
        require(s <= SECP256K1N_HALF) { "s value must be less than or equal to secp256k1n/2" }
    }

    /**
     * Recover the authority address from this authorization signature.
     *
     * @return the recovered authority address, or null if recovery fails
     */
    fun recoverAuthority(): Address? {
        val message = getSignatureHash()
        val publicKey = Secp256k1.recoverPublicKey(message, r, s, yParity) ?: return null
        return Address(Secp256k1.publicKeyToAddress(publicKey))
    }

    /**
     * Verify that this authorization was signed by the given authority address.
     *
     * @param authority the expected authority address
     * @return true if the signature is valid for the given authority
     */
    fun verifyAuthority(authority: Address): Boolean {
        return recoverAuthority() == authority
    }

    /**
     * Get the hash that was signed for this authorization according to EIP-7702.
     *
     * @return the keccak256 hash of MAGIC || rlp([chain_id, address, nonce])
     */
    private fun getSignatureHash(): ByteArray {
        val rlpEncoder = RlpEncoder(getRlpDataSize())
        rlpEncoder.encodeList(getRlpDataSize()) {
            encode(chainId)
            encode(address)
            encode(nonce)
        }

        val rlpBytes = rlpEncoder.toByteArray()
        val magicAndRlp = ByteArray(MAGIC.size + rlpBytes.size)
        MAGIC.copyInto(magicAndRlp, 0, 0, MAGIC.size)
        rlpBytes.copyInto(magicAndRlp, MAGIC.size, 0, rlpBytes.size)

        return Hashing.keccak256(magicAndRlp)
    }

    private fun getRlpDataSize(): Int = with(RlpEncoder) {
        return sizeOf(chainId) + sizeOf(address) + sizeOf(nonce)
    }

    override fun rlpEncode(rlp: RlpEncoder) {
        rlp.encodeList(rlpListBodySize()) {
            encode(chainId)
            encode(address)
            encode(nonce)
            encode(yParity)
            encode(r)
            encode(s)
        }
    }

    override fun rlpSize(): Int = with(RlpEncoder) {
        return sizeOfList(rlpListBodySize())
    }

    private fun rlpListBodySize(): Int = with(RlpEncoder) {
        return@with sizeOf(chainId) + sizeOf(address) + sizeOf(nonce) + sizeOf(yParity) + sizeOf(r) + sizeOf(s)
    }

    companion object : RlpDecodable<Authorization> {
        /**
         * EIP-7702 MAGIC constant used in authorization signature hash calculation.
         */
        val MAGIC = "0x05".let { FastHex.decode(it) }

        /**
         * Half of the secp256k1 curve order, used to validate s values.
         */
        private val SECP256K1N_HALF = BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16)

        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): Authorization? {
            return rlp.decodeListOrNull {
                val chainId = rlp.decodeLongOrElse { return null }
                val address = rlp.decodeOrNull(Address) ?: return null
                val nonce = rlp.decodeLongOrElse { return null }
                val yParity = rlp.decodeLongOrElse { return null }
                val r = rlp.decodeBigIntegerOrNull() ?: return null
                val s = rlp.decodeBigIntegerOrNull() ?: return null

                Authorization(chainId, address, nonce, yParity, r, s)
            }
        }
    }
}

object AuthorizationSerializer : KSerializer<Authorization> {
    override val descriptor = buildClassSerialDescriptor("Authorization")

    override fun serialize(encoder: Encoder, value: Authorization) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("chainId", FastHex.encodeWithPrefix(value.chainId))
                put("address", value.address.toString())
                put("nonce", FastHex.encodeWithPrefix(value.nonce))
                put("yParity", FastHex.encodeWithPrefix(value.yParity))
                put("r", FastHex.encodeWithPrefix(value.r))
                put("s", FastHex.encodeWithPrefix(value.s))
            },
        )
    }

    override fun deserialize(decoder: Decoder): Authorization {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val chainId = obj["chainId"]!!.jsonPrimitive.asHexLong()
        val address = obj["address"]!!.jsonPrimitive.asAddress()
        val nonce = obj["nonce"]!!.jsonPrimitive.asHexLong()
        val yParity = obj["yParity"]!!.jsonPrimitive.asHexLong()
        val r = obj["r"]!!.jsonPrimitive.asHexBigInteger()
        val s = obj["s"]!!.jsonPrimitive.asHexBigInteger()

        return Authorization(chainId, address, nonce, yParity, r, s)
    }
}
