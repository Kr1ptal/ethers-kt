package io.ethers.core.types

import io.ethers.core.types.transaction.TxType
import io.ethers.json.jackson.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class TransactionReceiptTest : FunSpec({
    test("deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "blockHash": "0x1d1bf1f362575491216d32c4bfe0fd7f4cb74281803d72f8b17260e365247329",
              "blockNumber": "0x117dd3c",
              "contractAddress": null,
              "cumulativeGasUsed": "0x7a063",
              "effectiveGasPrice": "0x166a124ba",
              "from": "0x0eeeeb7ae10c10f82f34f796b9ccf6066cce1421",
              "gasUsed": "0x65331",
              "logs": [
                {
                  "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                  "topics": [
                    "0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65",
                    "0x000000000000000000000000b0bababe78a9be0810fadf99dd2ed31ed12568be"
                  ],
                  "data": "0x0000000000000000000000000000000000000000000000000110c4f5fd7772ba",
                  "blockNumber": "0x117c277",
                  "transactionHash": "0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8",
                  "transactionIndex": "0xa0",
                  "blockHash": "0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560",
                  "logIndex": "0x10",
                  "removed": true
                }
              ],
              "logsBloom": "0x002000000100008010000000800000000000000000000000000000000400000000000100000000000000180000000000020080000800380001400002000090000000000000000028080200080000002400000000000010002000040080000000000400000000400000000000000000410000000000000000000000100008000000000210200000000000000000000000002000010100000840a00040080000000000000000002080000040400000020000000000000002000020040000080000000000020000000000000100000000000000000000008010000000000000000000202020000000000000108000004000000000a0000000400000000000001000",
              "status": "0x1",
              "to": "0x881d40237659c251811cec9c364ef91dc08d300c",
              "transactionHash": "0xce15f8ce74845b0d254fcbfda722ba89976ca6e09936d6761a648a6492b82e9b",
              "transactionIndex": "0x1",
              "type": "0x2",
              "root": "0x5f5755290000000000000000000000000000000000000000000000000000000000000080",
              "test_tx": {
                "k1_tx": "v1_tx",
                "k2_tx": "v2_tx"
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, TransactionReceipt::class.java)

        val expectedResult = TransactionReceipt(
            blockHash = Hash("0x1d1bf1f362575491216d32c4bfe0fd7f4cb74281803d72f8b17260e365247329"),
            blockNumber = 18341180L,
            contractAddress = null,
            cumulativeGasUsed = 499811L,
            effectiveGasPrice = BigInteger("6016804026"),
            from = Address("0x0eeeeb7ae10c10f82f34f796b9ccf6066cce1421"),
            gasUsed = 414513L,
            logs = listOf(
                Log(
                    address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                    topics = listOf(
                        Hash("0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65"),
                        Hash("0x000000000000000000000000b0bababe78a9be0810fadf99dd2ed31ed12568be"),
                    ),
                    data = Bytes("0x0000000000000000000000000000000000000000000000000110c4f5fd7772ba"),
                    blockNumber = 18334327L,
                    blockTimestamp = -1L,
                    transactionHash = Hash("0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8"),
                    transactionIndex = 160,
                    blockHash = Hash("0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"),
                    logIndex = 16,
                    removed = true,
                ),
            ),
            logsBloom = Bloom("0x002000000100008010000000800000000000000000000000000000000400000000000100000000000000180000000000020080000800380001400002000090000000000000000028080200080000002400000000000010002000040080000000000400000000400000000000000000410000000000000000000000100008000000000210200000000000000000000000002000010100000840a00040080000000000000000002080000040400000020000000000000002000020040000080000000000020000000000000100000000000000000000008010000000000000000000202020000000000000108000004000000000a0000000400000000000001000"),
            status = 1,
            to = Address("0x881d40237659c251811cec9c364ef91dc08d300c"),
            transactionHash = Hash("0xce15f8ce74845b0d254fcbfda722ba89976ca6e09936d6761a648a6492b82e9b"),
            transactionIndex = 1,
            type = TxType.DynamicFee,
            root = Bytes("0x5f5755290000000000000000000000000000000000000000000000000000000000000080"),
            otherFields = mapOf(
                "test_tx" to Jackson.MAPPER.readTree("""{"k1_tx":"v1_tx","k2_tx":"v2_tx"}"""),
            ),
        )

        result shouldBe expectedResult
        result.isSuccessful shouldBe true
    }
})
