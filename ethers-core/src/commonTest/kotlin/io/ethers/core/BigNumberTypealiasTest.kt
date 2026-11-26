package io.ethers.core

import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

@Serializable
private data class Amounts(
    val wei: HexBigInteger,
    val percentile: NumericBigInteger,
    val price: NumericBigDecimal,
    val optional: HexBigInteger? = null,
    val many: List<NumericBigInteger> = emptyList(),
)

class BigNumberTypealiasTest : FunSpec({
    val amounts = Amounts(
        wei = bigIntegerOf(20L),
        percentile = bigIntegerOf("123456789012345678901234567890"),
        price = bigDecimalOf("12.50"),
        optional = bigIntegerOf(255L),
        many = listOf(bigIntegerOf(1L), bigIntegerOf(2L)),
    )
    val json = """{"wei":"0x14","percentile":123456789012345678901234567890,"price":12.50,"optional":"0xff","many":[1,2]}"""

    test("the field type picks the encoding, with no setup") {
        Kotlinx.DEFAULT.encodeToString(Amounts.serializer(), amounts) shouldBe json
    }

    test("fields decode back to the same values") {
        Kotlinx.DEFAULT.decodeFromString(Amounts.serializer(), json) shouldBe amounts
    }
})
