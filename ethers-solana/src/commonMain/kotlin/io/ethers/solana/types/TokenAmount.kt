@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import io.ethers.core.json.JsonElement as RawJson

/** A token balance in raw units, with the decimals needed to render it. */
@KeepGeneratedSerializer
@Serializable(with = TokenAmountSerializer::class)
data class TokenAmount(
    @Serializable(with = TokenQuantitySerializer::class) val amount: BigInteger,
    @Serializable(with = U8Serializer::class) val decimals: Int,
    val uiAmountString: String,
    @Serializable(with = DecimalSerializer::class) val uiAmount: BigDecimal? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
)

object TokenAmountSerializer : ExtensibleJsonSerializer<TokenAmount>(TokenAmount.generatedSerializer(), { it.otherFields })
