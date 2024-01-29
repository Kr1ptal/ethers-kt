package io.ethers.core.types

import io.ethers.core.Jackson
import io.ethers.core.types.transaction.TxType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.math.BigInteger

class TxpoolTest : FunSpec({
    test("TxpoolContent deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "pending": {
                "0x0001EbE0141184db7Ff47Afc99c41c94f72a77C0": {
                  "0": {
                    "blockHash": null,
                    "blockNumber": null,
                    "from": "0x0001ebe0141184db7ff47afc99c41c94f72a77c0",
                    "gas": "0x5208",
                    "gasPrice": "0xef03be80",
                    "hash": "0xdb09221d970c74162d83954799659ef263b66e6614e2165188ef86f33f4a49c2",
                    "input": "0x",
                    "nonce": "0x0",
                    "to": "0xf3474e17e5f7069a2a3a85da77bcedff34183efd",
                    "transactionIndex": null,
                    "value": "0x2423f651a0c00",
                    "type": "0x0",
                    "chainId": "0x1",
                    "v": "0x26",
                    "r": "0x332e1ef2b90038b4f1d87c8920aacacd6963797f37f6b46de5b73b43798b9019",
                    "s": "0x43310658d48acecc5b57cd68136c2953da4114f4301354091ef3c69c7c9958a2"
                  }
                }
              },
              "queued": {
                "0x0096Ca31c87771a2Ed212D4b2E689e712Bd938F9": {
                  "1142": {
                    "blockHash": null,
                    "blockNumber": null,
                    "from": "0x0096ca31c87771a2ed212d4b2e689e712bd938f9",
                    "gas": "0x7d00",
                    "gasPrice": "0x1baf844c0",
                    "hash": "0x10150866249dabaf25268d50c29896b37a5ff18f0824784246c84d81f202af56",
                    "input": "0x095ea7b3000000000000000000000000000000000022d473030f116ddee9f6b43ac78ba3ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                    "nonce": "0x476",
                    "to": "0x111111111117dc0aa78b770fa6a738034120c302",
                    "transactionIndex": null,
                    "value": "0x0",
                    "type": "0x0",
                    "chainId": "0x1",
                    "v": "0x25",
                    "r": "0x91c41d37a3fa70dc81e2a4d9f625083dca375f094f22cc927b37a1b5e0bc14cd",
                    "s": "0x555f1f6509de7842bb22f318f6e44f135eb9d88b171b3173190f94f382a32242"
                  }
                }
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, TxpoolContent::class.java)

        val expectedResult = TxpoolContent(
            pending = mapOf(
                Address("0x0001EbE0141184db7Ff47Afc99c41c94f72a77C0") to mapOf(
                    0L to RPCTransaction(
                        blockHash = null,
                        blockNumber = -1L,
                        from = Address("0x0001ebe0141184db7ff47afc99c41c94f72a77c0"),
                        gas = 21_000L,
                        gasPrice = BigInteger("4010000000"),
                        hash = Hash("0xdb09221d970c74162d83954799659ef263b66e6614e2165188ef86f33f4a49c2"),
                        data = null,
                        nonce = 0L,
                        to = Address("0xf3474e17e5f7069a2a3a85da77bcedff34183efd"),
                        transactionIndex = -1L,
                        value = BigInteger("635790000000000"),
                        type = TxType.Legacy,
                        chainId = 1L,
                        v = 38L,
                        r = BigInteger("23149443838906753736590725708700793907547588851307035983763567886914160398361"),
                        s = BigInteger("30391580166589684358768626712521221244957680503061399873881978032155489032354"),
                        yParity = -1,
                        blobVersionedHashes = null,
                        blobFeeCap = null,
                        accessList = emptyList(),
                        gasFeeCap = BigInteger("4010000000"),
                        gasTipCap = BigInteger("4010000000"),
                    ),
                ),
            ),
            queued = mapOf(
                Address("0x0096Ca31c87771a2Ed212D4b2E689e712Bd938F9") to mapOf(
                    1142L to RPCTransaction(
                        blockHash = null,
                        blockNumber = -1L,
                        from = Address("0x0096ca31c87771a2ed212d4b2e689e712bd938f9"),
                        gas = 32_000L,
                        gasPrice = BigInteger("7431800000"),
                        hash = Hash("0x10150866249dabaf25268d50c29896b37a5ff18f0824784246c84d81f202af56"),
                        data = Bytes("0x095ea7b3000000000000000000000000000000000022d473030f116ddee9f6b43ac78ba3ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                        nonce = 1142L,
                        to = Address("0x111111111117dc0aa78b770fa6a738034120c302"),
                        transactionIndex = -1L,
                        value = BigInteger.ZERO,
                        type = TxType.Legacy,
                        chainId = 1L,
                        v = 37L,
                        r = BigInteger("65931866719980242200380868427802295846741631999272549801893218085394959832269"),
                        s = BigInteger("38614659278862282792125938012386074070351921231580079668664984986549756305986"),
                        yParity = -1,
                        blobVersionedHashes = null,
                        blobFeeCap = null,
                        accessList = emptyList(),
                        gasFeeCap = BigInteger("7431800000"),
                        gasTipCap = BigInteger("7431800000"),
                    ),
                ),
            ),
        )

        result shouldBe expectedResult
    }

    test("TxpoolStatus deserialization") {
        @Language("JSON")
        val jsonString = """{"pending":"0x37c3","queued":"0x2d4"}"""
        val result = Jackson.MAPPER.readValue(jsonString, TxpoolStatus::class.java)

        val expectedResult = TxpoolStatus(
            pending = 14275L,
            queued = 724L,
        )

        result shouldBe expectedResult
    }

    test("TxpoolContentFromAddress deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "pending": {
                "1": {
                  "blockHash": null,
                  "blockNumber": null,
                  "from": "0x76C9F62b2B94B83fae8e84DdbE50Ea3406b00289",
                  "gas": "0x43343",
                  "gasPrice": "0xC832254CA",
                  "hash": "0x9814f5c81452d1d12d271a574687e7e811127b9728a8c01151a21848cee9a2b1",
                  "input": "0xb6f9de95000000000000000000000000000000000000000000001d02eedfa5f459a2265b000000000000000000000000000000000000000000000000000000000000008000000000000000000000000076c9f62b2b94b83fae8e84ddbe50ea3406b0028900000000000000000000000000000000000000000000000000000000652e55620000000000000000000000000000000000000000000000000000000000000002000000000000000000000000c02aaa39b223fe8d0a0e5c4f27ead9083c756cc200000000000000000000000013787914364ecef133a7af2a2e62e470ba69425e",
                  "nonce": "0x883",
                  "to": "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D",
                  "transactionIndex": null,
                  "value": "0x51B660CDD58000",
                  "type": "0x0",
                  "chainId": "0x1",
                  "v": "0x25",
                  "r": "0x9717afb0b7b032dc13c3d7f434db1a831028bf9d57a0097582e4eadac7d9c2b8",
                  "s": "0x5ca39c5117ade41b1ef971fc31af1e261db6e60bdd08c59d9a6f4f1d4fdd79bb"
                }
              },
              "queued": {
                "2": {
                  "blockHash": null,
                  "blockNumber": null,
                  "from": "0xdf7363457c97f38b684d510ff63aa9247b7e1b3b",
                  "gas": "0x5cecc",
                  "gasPrice": "0x147d35700",
                  "hash": "0xa6a5497d0b796de6c0684cb71971a42d6e981d78fc29897c0ef2628a313633fc",
                  "input": "0x6ab15071000000000000000000000000000000000000000000000000959a29f29991d8000000000000000000000000000000000000000000000000000000000000000000",
                  "nonce": "0x2",
                  "to": "0xc7757805b983ee1b6272c1840c18e66837de858e",
                  "transactionIndex": null,
                  "value": "0x0",
                  "type": "0x0",
                  "chainId": "0x1",
                  "v": "0x25",
                  "r": "0x9717afb0b7b032dc13c3d7f434db1a831028bf9d57a0097582e4eadac7d9c2b8",
                  "s": "0x5ca39c5117ade41b1ef971fc31af1e261db6e60bdd08c59d9a6f4f1d4fdd79bb"
                }
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, TxpoolContentFromAddress::class.java)

        val expectedResult = TxpoolContentFromAddress(
            pending = mapOf(
                1L to RPCTransaction(
                    blockHash = null,
                    blockNumber = -1,
                    from = Address("0x76C9F62b2B94B83fae8e84DdbE50Ea3406b00289"),
                    gas = 275267,
                    gasPrice = BigInteger("53739672778"),
                    hash = Hash("0x9814f5c81452d1d12d271a574687e7e811127b9728a8c01151a21848cee9a2b1"),
                    data = Bytes("0xb6f9de95000000000000000000000000000000000000000000001d02eedfa5f459a2265b000000000000000000000000000000000000000000000000000000000000008000000000000000000000000076c9f62b2b94b83fae8e84ddbe50ea3406b0028900000000000000000000000000000000000000000000000000000000652e55620000000000000000000000000000000000000000000000000000000000000002000000000000000000000000c02aaa39b223fe8d0a0e5c4f27ead9083c756cc200000000000000000000000013787914364ecef133a7af2a2e62e470ba69425e"),
                    nonce = 2179,
                    to = Address("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"),
                    transactionIndex = -1,
                    value = BigInteger("23000000000000000"),
                    type = TxType.Legacy,
                    chainId = 1L,
                    accessList = emptyList(),
                    gasFeeCap = BigInteger("53739672778"),
                    gasTipCap = BigInteger("53739672778"),
                    v = 37,
                    r = BigInteger("68341090188469062555345098672849067048561261055363320484821091319517701325496"),
                    s = BigInteger("41901856999898881075816848749031346279839118815875108100645184885572994169275"),
                    yParity = -1,
                    blobVersionedHashes = null,
                    blobFeeCap = null,
                ),
            ),
            queued = mapOf(
                2L to RPCTransaction(
                    blockHash = null,
                    blockNumber = -1,
                    from = Address("0xdf7363457c97f38b684d510ff63aa9247b7e1b3b"),
                    gas = 380620,
                    gasPrice = BigInteger("5500000000"),
                    hash = Hash("0xa6a5497d0b796de6c0684cb71971a42d6e981d78fc29897c0ef2628a313633fc"),
                    data = Bytes("0x6ab15071000000000000000000000000000000000000000000000000959a29f29991d8000000000000000000000000000000000000000000000000000000000000000000"),
                    nonce = 2,
                    to = Address("0xc7757805b983ee1b6272c1840c18e66837de858e"),
                    transactionIndex = -1,
                    value = BigInteger.ZERO,
                    type = TxType.Legacy,
                    chainId = 1L,
                    accessList = emptyList(),
                    gasFeeCap = BigInteger("5500000000"),
                    gasTipCap = BigInteger("5500000000"),
                    v = 37,
                    r = BigInteger("68341090188469062555345098672849067048561261055363320484821091319517701325496"),
                    s = BigInteger("41901856999898881075816848749031346279839118815875108100645184885572994169275"),
                    yParity = -1,
                    blobVersionedHashes = null,
                    blobFeeCap = null,
                ),
            ),
        )

        result shouldBe expectedResult
    }

    test("TxpoolInspectResult deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "pending": {
                "0xfc59ed0430A1D53412e127E9dcCDBC54617E9201": {
                  "1": "0x95aD61b0a150d79219dCF64E1E6Cc01f0B64C4cE: 0 wei + 46551 gas × 10000000000 wei"
                },
                "0xfc9FA4ba0Aba00a51eC4B644DD1769cB13710a20": {
                  "2": "0xfc9FA4ba0Aba00a51eC4B644DD1769cB13710a20: 0 wei + 21000 gas × 28822143354 wei"
                },
                "0xfccd571cCE5F093e7054C6d5e0F3c95517425CfB": {
                  "20": "0x0BE1386395F2d160e2074E20b6F351dbffdf5180: 0 wei + 300000 gas × 6000000000 wei"
                }
              },
              "queued": {
                "0xf807136Fe59BbB2a99db6d4B0068d897522C518C": {
                  "8": "0x3b7157E5E732863170597790b4c005436572570F: 0 wei + 143188 gas × 55666382250 wei"
                },
                "0xf80Cd00c27cC3b5490596066B20506700c025A40": {
                  "12": "0xB517850510997a34b4DdC8c3797B4F83fAd510c4: 0 wei + 192899 gas × 4000000000 wei"
                },
                "0xf8E074B8c4Cb38fC71ADdB20450d2868646b0885": {
                  "2": "0xA0c68C638235ee32657e8f720a23ceC1bFc77C77: 0 wei + 335056 gas × 7000000000 wei"
                }
              }
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, TxpoolInspectResult::class.java)

        val expectedResult = TxpoolInspectResult(
            pending = mapOf(
                Address("0xfc59ed0430A1D53412e127E9dcCDBC54617E9201") to mapOf(
                    1L to "0x95aD61b0a150d79219dCF64E1E6Cc01f0B64C4cE: 0 wei + 46551 gas × 10000000000 wei",
                ),
                Address("0xfc9FA4ba0Aba00a51eC4B644DD1769cB13710a20") to mapOf(
                    2L to "0xfc9FA4ba0Aba00a51eC4B644DD1769cB13710a20: 0 wei + 21000 gas × 28822143354 wei",
                ),
                Address("0xfccd571cCE5F093e7054C6d5e0F3c95517425CfB") to mapOf(
                    20L to "0x0BE1386395F2d160e2074E20b6F351dbffdf5180: 0 wei + 300000 gas × 6000000000 wei",
                ),
            ),
            queued = mapOf(
                Address("0xf807136Fe59BbB2a99db6d4B0068d897522C518C") to mapOf(
                    8L to "0x3b7157E5E732863170597790b4c005436572570F: 0 wei + 143188 gas × 55666382250 wei",
                ),
                Address("0xf80Cd00c27cC3b5490596066B20506700c025A40") to mapOf(
                    12L to "0xB517850510997a34b4DdC8c3797B4F83fAd510c4: 0 wei + 192899 gas × 4000000000 wei",
                ),
                Address("0xf8E074B8c4Cb38fC71ADdB20450d2868646b0885") to mapOf(
                    2L to "0xA0c68C638235ee32657e8f720a23ceC1bFc77C77: 0 wei + 335056 gas × 7000000000 wei",
                ),
            ),
        )

        result shouldBe expectedResult
    }
})
