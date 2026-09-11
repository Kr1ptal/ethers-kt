@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.U8Serializer
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/** A token balance in raw units, with the decimals needed to render it. */
@KeepGeneratedSerializer
@Serializable(with = TokenAmountSerializer::class)
data class TokenAmount(
    @Serializable(with = TokenQuantitySerializer::class) val amount: BigInteger,
    @Serializable(with = U8Serializer::class) val decimals: Int,
    val uiAmountString: String,
    @Serializable(with = DecimalSerializer::class) val uiAmount: BigDecimal? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object TokenAmountSerializer : ExtensibleJsonSerializer<TokenAmount>(TokenAmount.generatedSerializer(), { it.otherFields })

/** Raw token quantities arrive as decimal strings, not JSON numbers. */
object TokenQuantitySerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaTokenAmount", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigInteger) = encoder.encodeString(requireU64(value).toString())
    override fun deserialize(decoder: Decoder): BigInteger = requireU64(BigInteger(decoder.decodeString()))
}

object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaDecimal", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): BigDecimal {
        val value = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        require(!value.isString && value != JsonNull) { "Expected a decimal JSON number" }
        return BigDecimal(value.content)
    }
    override fun serialize(encoder: Encoder, value: BigDecimal) = (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toString()))
}
