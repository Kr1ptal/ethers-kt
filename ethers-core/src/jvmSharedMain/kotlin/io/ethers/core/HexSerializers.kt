package io.ethers.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.github.artificialpb.bignum.BigInteger

/**
 * Field-level [KSerializer] that encodes a [Long] as a 0x-prefixed hex string and decodes
 * a 0x-prefixed hex string back to a [Long]. Use with `@Serializable(with = HexLongSerializer::class)`
 * on data class fields representing Ethereum JSON-RPC quantities (block numbers, gas, timestamps, ...).
 */
object HexLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HexLong", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString(FastHex.encodeWithPrefix(value))
    }

    override fun deserialize(decoder: Decoder): Long {
        return decoder.decodeString().toHexLong()
    }
}

/**
 * Field-level [KSerializer] that encodes/decodes an [Int] as a 0x-prefixed hex string.
 */
object HexIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HexInt", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeString(FastHex.encodeWithPrefix(value))
    }

    override fun deserialize(decoder: Decoder): Int {
        return decoder.decodeString().toHexLong().toInt()
    }
}

/**
 * Field-level [KSerializer] that encodes/decodes an unsigned [BigInteger] as a 0x-prefixed hex string.
 */
object HexBigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HexBigInteger", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigInteger) {
        encoder.encodeString(FastHex.encodeWithPrefix(value))
    }

    override fun deserialize(decoder: Decoder): BigInteger {
        val text = decoder.decodeString()
        if (text.isEmpty() || text == "0x" || text == "0X") return BigInteger.ZERO
        val bytes = FastHex.decode(text)
        if (bytes.isEmpty()) return BigInteger.ZERO
        return BigInteger(1, bytes)
    }
}

private fun String.toHexLong(): Long {
    if (isEmpty() || this == "0x" || this == "0X") return 0L
    val bytes = FastHex.decode(this)
    var value = 0L
    for (b in bytes) {
        value = (value shl 8) + (b.toInt() and 0xff)
    }
    return value
}
