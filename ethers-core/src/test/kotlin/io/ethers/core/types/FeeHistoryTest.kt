package io.ethers.core.types

import io.ethers.json.jackson.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class FeeHistoryTest : FunSpec({
    test("FeeHistory deserialization - new format") {
        @Language("JSON")
        val jsonString = """
            {
              "oldestBlock": "0x1184abf",
              "reward": [
                ["0x989680", "0x36d9c71", "0x5f5e100"],
                ["0x545104", "0x2faf080", "0x5f5e100"],
                ["0x0", "0x5e69ec0", "0x5f5e100"]
              ],
              "baseFeePerGas": [
                "0x1cc814a5d",
                "0x1cdca1bee",
                "0x1c6179e07"
              ],
              "gasUsedRatio": [
                0.5111568446588093,
                0.4333269044384441,
                0.3938401
              ],
              "baseFeePerBlobGas": ["0x1", "0x2"],
              "blobGasUsedRatio": [0.5],
              "unknownFieldIsSkipped":  {
                "field1": "value1",
                "field2": "value2"
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, FeeHistory::class.java)

        val expectedResult = FeeHistory(
            oldestBlock = 18369215,
            baseFeePerGas = listOf(
                BigInteger("7725992541"),
                BigInteger("7747541998"),
                BigInteger("7618403847"),
            ),
            gasUsedRatio = listOf(
                0.5111568446588093,
                0.4333269044384441,
                0.3938401,
            ),
            rewards = listOf(
                listOf(
                    BigInteger("10000000"),
                    BigInteger("57515121"),
                    BigInteger("100000000"),
                ),
                listOf(
                    BigInteger("5525764"),
                    BigInteger("50000000"),
                    BigInteger("100000000"),
                ),
                listOf(
                    BigInteger("0"),
                    BigInteger("99000000"),
                    BigInteger("100000000"),
                ),
            ),
            baseFeePerBlobGas = listOf(
                BigInteger("1"),
                BigInteger("2"),
            ),
            blobGasUsedRatio = listOf(0.5),
        )

        result shouldBe expectedResult
    }

    // oldestBlock is a decimal number in the old format
    test("FeeHistory deserialization - old format") {
        @Language("JSON")
        val jsonString = """
            {
              "oldestBlock": 18369215,
              "reward": [
                ["0x989680", "0x36d9c71", "0x5f5e100"],
                ["0x545104", "0x2faf080", "0x5f5e100"],
                ["0x0", "0x5e69ec0", "0x5f5e100"]
              ],
              "baseFeePerGas": [
                "0x1cc814a5d",
                "0x1cdca1bee",
                "0x1c6179e07"
              ],
              "gasUsedRatio": [
                0.5111568446588093,
                0.4333269044384441,
                0.3938401
              ]
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, FeeHistory::class.java)

        val expectedResult = FeeHistory(
            oldestBlock = 18369215,
            baseFeePerGas = listOf(
                BigInteger("7725992541"),
                BigInteger("7747541998"),
                BigInteger("7618403847"),
            ),
            gasUsedRatio = listOf(
                0.5111568446588093,
                0.4333269044384441,
                0.3938401,
            ),
            rewards = listOf(
                listOf(
                    BigInteger("10000000"),
                    BigInteger("57515121"),
                    BigInteger("100000000"),
                ),
                listOf(
                    BigInteger("5525764"),
                    BigInteger("50000000"),
                    BigInteger("100000000"),
                ),
                listOf(
                    BigInteger("0"),
                    BigInteger("99000000"),
                    BigInteger("100000000"),
                ),
            ),
            baseFeePerBlobGas = null,
            blobGasUsedRatio = null,
        )

        result shouldBe expectedResult
    }
})
