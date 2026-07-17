package io.ethers.core.types

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ethers.core.FastHex
import io.ethers.core.HexDecodingError
import io.ethers.core.types.transaction.ChainId
import io.ethers.crypto.Hashing
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.random.Random

/**
 * 20-byte address.
 * */
@Serializable(with = AddressSerializer::class)
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
        if (other == null || this::class != other::class) return false

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
            val arr = rlp.decodeByteArrayOrNull() ?: return null
            return when {
                arr.isEmpty() -> null
                arr.size != 20 -> rlp.error("Invalid address length: ${arr.size}")
                else -> Address(arr)
            }
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
            sender.value.copyInto(data, 1, 0, 20)
            salt.copyInto(data, 21, 0, 32)
            codeHash.copyInto(data, 53, 0, 32)
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

        /**
         * Create a new [Address] from hex string with validation.
         */
        @JvmStatic
        fun fromHex(hex: String): Result<Address, HexDecodingError> {
            if (!FastHex.isValidHex(hex)) {
                return Err(HexDecodingError("Invalid hex format: $hex"))
            }

            val bytes = FastHex.decodeUnsafe(hex)
            if (bytes.size != 20) {
                return Err(HexDecodingError("Address must be 20 bytes long, got ${bytes.size} bytes"))
            }

            return Ok(Address(bytes))
        }

        /**
         * Create a new [Address] from hex string without validation.
         */
        @JvmStatic
        fun fromHexUnsafe(hex: String): Address {
            return Address(FastHex.decodeUnsafe(hex))
        }
    }
}

object AddressSerializer : KSerializer<Address> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Address", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Address) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Address {
        val text = decoder.decodeString()
        if (text.isEmpty() || text == "0x" || text == "0X") return Address.ZERO
        val arr = FastHex.decode(text)
        return if (arr.isEmpty()) Address.ZERO else Address(arr)
    }
}
