package io.ethers.core.types

import io.ethers.core.Jackson
import io.ethers.core.types.transaction.TxBlob
import io.ethers.core.types.transaction.TxType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class RPCTransactionTest : FunSpec({
    test("deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "blockHash": "0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560",
              "blockNumber": "0x117c277",
              "from": "0x1264f83b093abbf840ea80a361988d19c7f5a686",
              "gas": "0x6ddd0",
              "gasPrice": "0x1cd5d2c16",
              "maxFeePerGas": "0x26f45990a",
              "maxPriorityFeePerGas": "0x5f5e100",
              "hash": "0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8",
              "input": "0x69277b67",
              "nonce": "0x3e2e",
              "to": "0xb0bababe78a9be0810fadf99dd2ed31ed12568be",
              "transactionIndex": "0x1",
              "value": "0x2386f26fc10000",
              "type": "0x2",
              "accessList": [
                {
                  "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                  "storageKeys": [
                    "0x4c3ae740928519b30ceeb40195f24b54cf6e63b41705439a07ce51fae9e1ba6c",
                    "0x12231cd4c753cb5530a43a74c45106c24765e6f81dc8927d4f4be7e53315d5a8"
                  ]
                }
              ],
              "chainId": "0x1",
              "v": "0x26",
              "r": "0xd33daf514da958c63cb5b812447e580391609f1deb42eaa8717f94942cf9efc4",
              "s": "0x588c71ae3c52c6d3decfbaf3fd3764845d743525de3d293d5aa0a4e7f815eabc",
              "blobVersionedHashes": ["0xd33daf514da958c63cb5b812447e580391609f1deb42eaa8717f94942cf9efc4", "0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"],
              "maxFeePerBlobGas": "0x26f45990a",
              "test_tx": {
                "k1_tx": "v1_tx",
                "k2_tx": "v2_tx"
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, RPCTransaction::class.java)

        val expectedResult = RPCTransaction(
            blockHash = Hash("0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"),
            blockNumber = 18334327L,
            from = Address("0x1264f83b093abbf840ea80a361988d19c7f5a686"),
            gas = 450_000,
            gasPrice = BigInteger("7740402710"),
            gasFeeCap = BigInteger("10456766730"),
            gasTipCap = BigInteger("100000000"),
            hash = Hash("0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8"),
            data = Bytes("0x69277b67"),
            nonce = 15918L,
            to = Address("0xb0bababe78a9be0810fadf99dd2ed31ed12568be"),
            transactionIndex = 1L,
            value = BigInteger("10000000000000000"),
            type = TxType.DynamicFee,
            accessList = listOf(
                AccessList.Item(
                    Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                    listOf(
                        Hash("0x4c3ae740928519b30ceeb40195f24b54cf6e63b41705439a07ce51fae9e1ba6c"),
                        Hash("0x12231cd4c753cb5530a43a74c45106c24765e6f81dc8927d4f4be7e53315d5a8"),
                    ),
                ),
            ),
            chainId = 1L,
            v = 38L,
            r = BigInteger("95546998719565769459668967071015181532151001694673704956264532570756523618244"),
            s = BigInteger("40051673859117113248116558288385057013128832480810174174673686209253214644924"),
            yParity = -1,
            blobVersionedHashes = listOf(
                Hash("0xd33daf514da958c63cb5b812447e580391609f1deb42eaa8717f94942cf9efc4"),
                Hash("0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"),
            ),
            blobFeeCap = BigInteger("10456766730"),
            otherFields = mapOf(
                "test_tx" to Jackson.MAPPER.readTree("""{"k1_tx":"v1_tx","k2_tx":"v2_tx"}"""),
            ),
        )

        result shouldBe expectedResult
        result.blobGas shouldBe TxBlob.GAS_PER_BLOB * 2
    }
})
