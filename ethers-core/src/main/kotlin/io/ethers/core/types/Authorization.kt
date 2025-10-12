package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import io.ethers.core.forEachObjectField
import io.ethers.core.handleUnknownField
import io.ethers.core.readAddress
import io.ethers.core.readHexBigInteger
import io.ethers.core.readHexLong
import io.ethers.crypto.Hashing
import io.ethers.crypto.Secp256k1
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import java.math.BigInteger

/**
 * EIP-7702 Authorization structure for SetCode transactions.
 * Each authorization allows a specific address to execute code on behalf of the authority.
 */
@JsonSerialize(using = AuthorizationSerializer::class)
@JsonDeserialize(using = AuthorizationDeserializer::class)
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

        val magicAndRlp = ByteArray(MAGIC.size + rlpEncoder.toByteArray().size)
        System.arraycopy(MAGIC, 0, magicAndRlp, 0, MAGIC.size)
        System.arraycopy(rlpEncoder.toByteArray(), 0, magicAndRlp, MAGIC.size, rlpEncoder.toByteArray().size)

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
            return rlp.decodeList {
                val chainId = rlp.decodeLongOrElse { return null }
                val address = rlp.decode(Address) ?: return null
                val nonce = rlp.decodeLongOrElse { return null }
                val yParity = rlp.decodeLongOrElse { return null }
                val r = rlp.decodeBigIntegerOrElse { return null }
                val s = rlp.decodeBigIntegerOrElse { return null }

                Authorization(chainId, address, nonce, yParity, r, s)
            }
        }
    }
}

private class AuthorizationSerializer : JsonSerializer<Authorization>() {
    override fun serialize(value: Authorization, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("chainId", FastHex.encodeWithPrefix(value.chainId))
        gen.writeStringField("address", value.address.toString())
        gen.writeStringField("nonce", FastHex.encodeWithPrefix(value.nonce))
        gen.writeStringField("yParity", FastHex.encodeWithPrefix(value.yParity))
        gen.writeStringField("r", FastHex.encodeWithPrefix(value.r))
        gen.writeStringField("s", FastHex.encodeWithPrefix(value.s))
        gen.writeEndObject()
    }
}

private class AuthorizationDeserializer : JsonDeserializer<Authorization>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Authorization {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var chainId = 0L
        lateinit var address: Address
        var nonce = 0L
        var yParity = 0L
        lateinit var r: BigInteger
        lateinit var s: BigInteger

        p.forEachObjectField { field ->
            when (field) {
                "chainId" -> chainId = p.readHexLong()
                "address" -> address = p.readAddress()
                "nonce" -> nonce = p.readHexLong()
                "yParity" -> yParity = p.readHexLong()
                "r" -> r = p.readHexBigInteger()
                "s" -> s = p.readHexBigInteger()
                else -> p.handleUnknownField()
            }
        }

        return Authorization(chainId, address, nonce, yParity, r, s)
    }
}
