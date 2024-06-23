package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import io.ethers.core.readAddress
import io.ethers.core.types.transaction.ChainId
import io.ethers.crypto.Hashing
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import kotlin.random.Random

/**
 * 20-byte address.
 * */
@JsonDeserialize(using = AddressDeserializer::class)
@JsonSerialize(using = AddressSerializer::class)
class Address(private val value: ByteArray) : RlpEncodable {
    constructor(value: CharSequence) : this(FastHex.decode(value))

    // cache of hex string for faster serialization if serializing the same instance multiple times
    private var stringCache: String? = null

    init {
        require(value.size == 20) { "Address must be 20 bytes long" }
    }

    /**
     * Return the internal byte array.
     *
     * If you need to modify the array, use [toByteArray] instead which returns a new copy of the array.
     *
     * IMPORTANT: Do not modify the returned array, it will lead to undefined behavior.
     * */
    fun asByteArray() = value

    /**
     * Return a copy of internal byte array.
     *
     * If you do not need to modify the array, use [asByteArray] instead which returns the internal array
     * without copying.
     * */
    fun toByteArray() = value.copyOf()

    /**
     * Get the address in checksum format, based on [EIP-55](https://eips.ethereum.org/EIPS/eip-55).
     *
     * @param chainId the chain id to use for the checksum calculation, according
     * to [EIP-1191](https://eips.ethereum.org/EIPS/eip-1191).
     * */
    @JvmOverloads
    fun toChecksumString(chainId: Long = -1): String {
        val validChainId = ChainId.isValid(chainId)
        val encodedOffset = if (validChainId) 2 else 0
        val encodedHex = FastHex.encodeAsBytes(value, withPrefix = validChainId)
        val hash = when (validChainId) {
            true -> Hashing.keccak256(chainId.toString().toByteArray() + encodedHex)
            false -> Hashing.keccak256(encodedHex)
        }

        val hashHex = FastHex.encodeAsBytes(hash, withPrefix = false)

        val ret = ByteArray(42)
        ret[0] = '0'.code.toByte()
        ret[1] = 'x'.code.toByte()

        for (i in 0..<40) {
            val nibble = encodedHex[i + encodedOffset]
            if (nibble.toInt().toChar() in 'a'..'f' && hashHex[i] >= '8'.code) {
                ret[i + 2] = (nibble.toInt() - 0x20).toByte()
            } else {
                ret[i + 2] = nibble
            }
        }
        return String(ret)
    }

    override fun rlpEncode(rlp: RlpEncoder) {
        rlp.encode(value)
    }

    override fun rlpSize() = RlpEncoder.sizeOf(value)

    infix fun equals(other: CharSequence): Boolean {
        return value.contentEquals(FastHex.decode(other))
    }

    infix fun equals(other: ByteArray): Boolean {
        return value.contentEquals(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Address

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return stringCache ?: FastHex.encodeWithPrefix(value).also { stringCache = it }
    }

    companion object : RlpDecodable<Address> {
        @JvmField
        val ZERO = Address(ByteArray(20))

        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): Address? {
            return rlp.decodeByteArray(::Address)
        }

        /**
         * Compute contract address as with *CREATE* opcode, based on [sender] and senders [nonce].
         */
        @JvmStatic
        fun computeCreate(sender: Address, nonce: Long): Address = with(RlpEncoder) {
            val fieldsSize = sizeOf(sender) + sizeOf(nonce)

            val rlp = RlpEncoder(sizeOfList(fieldsSize), isExactSize = true)
            rlp.encodeList(fieldsSize) {
                encode(sender)
                encode(nonce)
            }

            val hash = Hashing.keccak256(rlp.toByteArray())
            return Address(hash.copyOfRange(12, hash.size))
        }

        /**
         * Compute a deterministic contract address as with *CREATE2* opcode, based on [sender], [salt],
         * and [codeHash] of the contract being deployed.
         */
        @JvmStatic
        fun computeCreate2(sender: Address, salt: ByteArray, codeHash: ByteArray): Address {
            val data = ByteArray(85)
            data[0] = 0xff.toByte()
            System.arraycopy(sender.value, 0, data, 1, 20)
            System.arraycopy(salt, 0, data, 21, 32)
            System.arraycopy(codeHash, 0, data, 53, 32)
            val hash = Hashing.keccak256(data)
            return Address(hash.copyOfRange(12, hash.size))
        }

        /**
         * Generate and return random [Address].
         */
        @JvmStatic
        fun random(): Address {
            return Address(Random.nextBytes(ByteArray(20)))
        }
    }
}

private class AddressDeserializer : JsonDeserializer<Address>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Address {
        return p.readAddress()
    }
}

private class AddressSerializer : JsonSerializer<Address>() {
    override fun serialize(value: Address, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}
