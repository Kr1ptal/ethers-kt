package io.ethers.core.types

import io.ethers.core.Jackson
import io.ethers.core.types.transaction.TxType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class BlockTest : FunSpec({
    test("BlockWithHashes deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "difficulty": "0xa",
              "extraData": "0x626f62612d6275696c6465722e636f6d",
              "gasLimit": "0x1c9c364",
              "gasUsed": "0xb085d9",
              "hash": "0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560",
              "logsBloom": "0x4a70205e49ee334de13ff8b490a9483654e1f04222c7801806317d301faeb685404f79a80295e96813d05baa660e1b05965cfcbf3d113ccaa2a4a01a422e5f25de12d73812689a3f98f77f09f66af06300b6051c147c3d8e433a22758c324a4094902e8d6286a783fd01101775415ec5165763e5ea19c556ccf28e76374f4267a7dc724ba2e60ae70def135d2baa97971175114d19216258026c544c26dc2c005fe6f540b928a1d14babe2d492b26d4a58a2be8502898120cda8d2023008697d67d9046b823325e0c6808f482fb27dcd2980437a330430562aa648efe14e729428523cb64b0a84c84026d1bf6463c085a184ff5b1188b3f8730c194b3293161b",
              "mixHash": "0x6192bd2e8367a2b1d422fb7a00c8ef106afa8c00caa552b5efca32ec8a8aa399",
              "nonce": "0x2",
              "number": "0x117c277",
              "parentHash": "0x91e83f42e9fa6ca0c83c059c7bfa3eec339cdcf48ed14f2327d2af5006073067",
              "receiptsRoot": "0x9132ead936ccd38b4a8c3f53d8491603b0bcfc715d3515a55a8f1248e31532f4",
              "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
              "size": "0x103a3",
              "stateRoot": "0xd9988eb2accb57bbcad4b65f05e7a56defce642d37668681726e2adad96eb558",
              "timestamp": "0x6527e5fb",
              "totalDifficulty": "0xc70d815d562d3cfa955",
              "transactionsRoot": "0xec6a4c2b318b6ebdf068e256be0c86ebd40ca926406d109db629c61f24cbc969",
              "withdrawalsRoot": "0x1276b1e90b9f4a76b24e5fad4adce2a5e6ffef7351bfaa2e128955950a9027b0",
              "baseFeePerGas": "0x2e22eacf",
              "miner": "0x3b64216ad1a58f61538b4fa1b27327675ab7ed67",
              "blobGasUsed": "0x1276b0",
              "excessBlobGas": "0x01276b",
              "parentBeaconBlockRoot": "0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8",
              "transactions": [
                "0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8",
                "0x5c4ca94fca565ef1c983e3a3cd4bc6d6da1091a487f5d64b8a1bb3434a12d876",
                "0xd529a78947f2b708efbd4c7162dd6ad8bd85df4c85fcb973a7237dced67449a6"
              ],
              "uncles": [
                "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c",
                "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                "0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"
              ],
              "withdrawals": [
                {
                  "index": "0x13d3a66",
                  "validatorIndex": "0x92592",
                  "address": "0xe839a3e9efb32c6a56ab7128e51056585275506c",
                  "amount": "0x369db71"
                },
                {
                  "index": "0x13d3a67",
                  "validatorIndex": "0x92593",
                  "address": "0xe839a3e9efb32c6a56ab7128e51056585275506c",
                  "amount": "0xfcb09b"
                },
                {
                  "index": "0x13d3a68",
                  "validatorIndex": "0x92595",
                  "address": "0xe839a3e9efb32c6a56ab7128e51056585275506c",
                  "amount": "0xfe2ccd"
                }
              ],
              "test": {
                "k1": "v1",
                "k2": "v2"
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, BlockWithHashes::class.java)

        val expectedResult = BlockWithHashes(
            baseFeePerGas = BigInteger("774040271"),
            difficulty = BigInteger.TEN,
            extraData = Bytes("0x626f62612d6275696c6465722e636f6d"),
            gasLimit = 29_999_972L,
            gasUsed = 11_568_601L,
            hash = Hash("0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"),
            logsBloom = Bloom("0x4a70205e49ee334de13ff8b490a9483654e1f04222c7801806317d301faeb685404f79a80295e96813d05baa660e1b05965cfcbf3d113ccaa2a4a01a422e5f25de12d73812689a3f98f77f09f66af06300b6051c147c3d8e433a22758c324a4094902e8d6286a783fd01101775415ec5165763e5ea19c556ccf28e76374f4267a7dc724ba2e60ae70def135d2baa97971175114d19216258026c544c26dc2c005fe6f540b928a1d14babe2d492b26d4a58a2be8502898120cda8d2023008697d67d9046b823325e0c6808f482fb27dcd2980437a330430562aa648efe14e729428523cb64b0a84c84026d1bf6463c085a184ff5b1188b3f8730c194b3293161b"),
            miner = Address("0x3b64216ad1a58f61538b4fa1b27327675ab7ed67"),
            mixHash = Hash("0x6192bd2e8367a2b1d422fb7a00c8ef106afa8c00caa552b5efca32ec8a8aa399"),
            nonce = BigInteger.TWO,
            number = 18334327L,
            parentHash = Hash("0x91e83f42e9fa6ca0c83c059c7bfa3eec339cdcf48ed14f2327d2af5006073067"),
            receiptsRoot = Hash("0x9132ead936ccd38b4a8c3f53d8491603b0bcfc715d3515a55a8f1248e31532f4"),
            sha3Uncles = Hash("0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"),
            size = 66_467L,
            stateRoot = Hash("0xd9988eb2accb57bbcad4b65f05e7a56defce642d37668681726e2adad96eb558"),
            timestamp = 1697113595L,
            totalDifficulty = BigInteger("58750003716598352816469"),
            transactions = listOf(
                Hash("0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8"),
                Hash("0x5c4ca94fca565ef1c983e3a3cd4bc6d6da1091a487f5d64b8a1bb3434a12d876"),
                Hash("0xd529a78947f2b708efbd4c7162dd6ad8bd85df4c85fcb973a7237dced67449a6"),
            ),
            transactionsRoot = Hash("0xec6a4c2b318b6ebdf068e256be0c86ebd40ca926406d109db629c61f24cbc969"),
            uncles = listOf(
                Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"),
                Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
            ),
            withdrawals = listOf(
                Withdrawal(20789862, 599442, Address("0xe839a3e9efb32c6a56ab7128e51056585275506c"), 57269105L),
                Withdrawal(20789863, 599443, Address("0xe839a3e9efb32c6a56ab7128e51056585275506c"), 16560283L),
                Withdrawal(20789864, 599445, Address("0xe839a3e9efb32c6a56ab7128e51056585275506c"), 16657613L),
            ),
            withdrawalsRoot = Hash("0x1276b1e90b9f4a76b24e5fad4adce2a5e6ffef7351bfaa2e128955950a9027b0"),
            blobGasUsed = 1210032,
            excessBlobGas = 75627,
            parentBeaconBlockRoot = Hash("0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8"),
            otherFields = mapOf(
                "test" to Jackson.MAPPER.readTree("""{"k1":"v1","k2":"v2"}"""),
            ),
        )

        result shouldBe expectedResult
    }

    test("BlockWithTransactions deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "difficulty": "0xa",
              "extraData": "0x626f62612d6275696c6465722e636f6d",
              "gasLimit": "0x1c9c364",
              "gasUsed": "0xb085d9",
              "hash": "0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560",
              "logsBloom": "0x4a70205e49ee334de13ff8b490a9483654e1f04222c7801806317d301faeb685404f79a80295e96813d05baa660e1b05965cfcbf3d113ccaa2a4a01a422e5f25de12d73812689a3f98f77f09f66af06300b6051c147c3d8e433a22758c324a4094902e8d6286a783fd01101775415ec5165763e5ea19c556ccf28e76374f4267a7dc724ba2e60ae70def135d2baa97971175114d19216258026c544c26dc2c005fe6f540b928a1d14babe2d492b26d4a58a2be8502898120cda8d2023008697d67d9046b823325e0c6808f482fb27dcd2980437a330430562aa648efe14e729428523cb64b0a84c84026d1bf6463c085a184ff5b1188b3f8730c194b3293161b",
              "mixHash": "0x6192bd2e8367a2b1d422fb7a00c8ef106afa8c00caa552b5efca32ec8a8aa399",
              "nonce": "0x2",
              "number": "0x117c277",
              "parentHash": "0x91e83f42e9fa6ca0c83c059c7bfa3eec339cdcf48ed14f2327d2af5006073067",
              "receiptsRoot": "0x9132ead936ccd38b4a8c3f53d8491603b0bcfc715d3515a55a8f1248e31532f4",
              "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
              "size": "0x103a3",
              "stateRoot": "0xd9988eb2accb57bbcad4b65f05e7a56defce642d37668681726e2adad96eb558",
              "timestamp": "0x6527e5fb",
              "totalDifficulty": "0xc70d815d562d3cfa955",
              "transactionsRoot": "0xec6a4c2b318b6ebdf068e256be0c86ebd40ca926406d109db629c61f24cbc969",
              "withdrawalsRoot": "0x1276b1e90b9f4a76b24e5fad4adce2a5e6ffef7351bfaa2e128955950a9027b0",
              "baseFeePerGas": "0x2e22eacf",
              "miner": "0x3b64216ad1a58f61538b4fa1b27327675ab7ed67",
              "transactions": [
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
                  "test_tx": {
                    "k1_tx": "v1_tx",
                    "k2_tx": "v2_tx"
                  }
                }
              ],
              "uncles": [
                "0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c",
                "0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b",
                "0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"
              ],
              "withdrawals": [
                {
                  "index": "0x13d3a66",
                  "validatorIndex": "0x92592",
                  "address": "0xe839a3e9efb32c6a56ab7128e51056585275506c",
                  "amount": "0x369db71"
                },
                {
                  "index": "0x13d3a67",
                  "validatorIndex": "0x92593",
                  "address": "0xe839a3e9efb32c6a56ab7128e51056585275506c",
                  "amount": "0xfcb09b"
                },
                {
                  "index": "0x13d3a68",
                  "validatorIndex": "0x92595",
                  "address": "0xe839a3e9efb32c6a56ab7128e51056585275506c",
                  "amount": "0xfe2ccd"
                }
              ],
              "test": {
                "k1": "v1",
                "k2": "v2"
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, BlockWithTransactions::class.java)

        val expectedResult = BlockWithTransactions(
            baseFeePerGas = BigInteger("774040271"),
            difficulty = BigInteger.TEN,
            extraData = Bytes("0x626f62612d6275696c6465722e636f6d"),
            gasLimit = 29_999_972L,
            gasUsed = 11_568_601L,
            hash = Hash("0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"),
            logsBloom = Bloom("0x4a70205e49ee334de13ff8b490a9483654e1f04222c7801806317d301faeb685404f79a80295e96813d05baa660e1b05965cfcbf3d113ccaa2a4a01a422e5f25de12d73812689a3f98f77f09f66af06300b6051c147c3d8e433a22758c324a4094902e8d6286a783fd01101775415ec5165763e5ea19c556ccf28e76374f4267a7dc724ba2e60ae70def135d2baa97971175114d19216258026c544c26dc2c005fe6f540b928a1d14babe2d492b26d4a58a2be8502898120cda8d2023008697d67d9046b823325e0c6808f482fb27dcd2980437a330430562aa648efe14e729428523cb64b0a84c84026d1bf6463c085a184ff5b1188b3f8730c194b3293161b"),
            miner = Address("0x3b64216ad1a58f61538b4fa1b27327675ab7ed67"),
            mixHash = Hash("0x6192bd2e8367a2b1d422fb7a00c8ef106afa8c00caa552b5efca32ec8a8aa399"),
            nonce = BigInteger.TWO,
            number = 18334327L,
            parentHash = Hash("0x91e83f42e9fa6ca0c83c059c7bfa3eec339cdcf48ed14f2327d2af5006073067"),
            receiptsRoot = Hash("0x9132ead936ccd38b4a8c3f53d8491603b0bcfc715d3515a55a8f1248e31532f4"),
            sha3Uncles = Hash("0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"),
            size = 66_467L,
            stateRoot = Hash("0xd9988eb2accb57bbcad4b65f05e7a56defce642d37668681726e2adad96eb558"),
            timestamp = 1697113595L,
            totalDifficulty = BigInteger("58750003716598352816469"),
            transactions = listOf(
                RPCTransaction(
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
                    transactionIndex = 1,
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
                    authorizationList = null,
                    chainId = 1L,
                    v = 38L,
                    r = BigInteger("95546998719565769459668967071015181532151001694673704956264532570756523618244"),
                    s = BigInteger("40051673859117113248116558288385057013128832480810174174673686209253214644924"),
                    yParity = -1,
                    blobVersionedHashes = null,
                    blobFeeCap = null,
                    otherFields = mapOf(
                        "test_tx" to Jackson.MAPPER.readTree("""{"k1_tx":"v1_tx","k2_tx":"v2_tx"}"""),
                    ),
                ),
            ),
            transactionsRoot = Hash("0xec6a4c2b318b6ebdf068e256be0c86ebd40ca926406d109db629c61f24cbc969"),
            uncles = listOf(
                Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"),
                Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
            ),
            withdrawals = listOf(
                Withdrawal(20789862, 599442, Address("0xe839a3e9efb32c6a56ab7128e51056585275506c"), 57269105L),
                Withdrawal(20789863, 599443, Address("0xe839a3e9efb32c6a56ab7128e51056585275506c"), 16560283L),
                Withdrawal(20789864, 599445, Address("0xe839a3e9efb32c6a56ab7128e51056585275506c"), 16657613L),
            ),
            withdrawalsRoot = Hash("0x1276b1e90b9f4a76b24e5fad4adce2a5e6ffef7351bfaa2e128955950a9027b0"),
            blobGasUsed = -1L,
            excessBlobGas = -1L,
            parentBeaconBlockRoot = null,
            otherFields = mapOf(
                "test" to Jackson.MAPPER.readTree("""{"k1":"v1","k2":"v2"}"""),
            ),
        )

        result shouldBe expectedResult
    }
})
