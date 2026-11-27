package io.ethers.core

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/**
 * A [BigInteger] written as a JSON number, e.g. `20`. Use it as a field type in a `@Serializable` class where the
 * JSON-RPC API expects a number rather than a hex quantity.
 */
typealias NumericBigInteger =
    @Serializable(with = BigIntegerNumberSerializer::class)
    BigInteger

/**
 * A [BigDecimal] written as a JSON number, e.g. `12.5`. Use it as a field type in a `@Serializable` class.
 */
typealias NumericBigDecimal =
    @Serializable(with = BigDecimalNumberSerializer::class)
    BigDecimal

/**
 * Encodes a [BigInteger] as a JSON number with every digit kept, and decodes one back. JSON only.
 */
object BigIntegerNumberSerializer : KSerializer<BigInteger> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigIntegerNumber", PrimitiveKind.STRING)

    // written as an unquoted literal, so it keeps full precision while still being a JSON number
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigInteger) {
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toString()))
    }

    override fun deserialize(decoder: Decoder): BigInteger {
        return bigIntegerOf((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)
    }
}

/**
 * Encodes a [BigDecimal] as a plain JSON number, without exponent notation, and decodes one back. JSON only.
 */
object BigDecimalNumberSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimalNumber", PrimitiveKind.STRING)

    // exponent notation is avoided because Go's big-number decoding, used by geth, rejects it
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return bigDecimalOf((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)
    }
}
