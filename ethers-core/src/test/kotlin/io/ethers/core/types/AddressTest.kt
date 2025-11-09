package io.ethers.core.types

import io.ethers.crypto.Hashing
import io.ethers.json.jackson.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class AddressTest : FunSpec({
    // cases taken from: https://ethereum.stackexchange.com/a/761
    context("compute address using CREATE opcode") {
        data class CreateCase(val sender: String, val nonce: Long, val resultAddress: String) : WithDataTestName {
            override fun dataTestName(): String = toString()
        }

        withData(
            CreateCase(
                "0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0",
                0L,
                "0xcd234a471b72ba2f1ccf0a70fcaba648a5eecd8d",
            ),
            CreateCase(
                "0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0",
                1L,
                "0x343c43a37d37dff08ae8c4a11544c718abb4fcf8",
            ),
            CreateCase(
                "0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0",
                2L,
                "0xf778b86fa74e846c4f0a1fbd1335fe81c00a0c91",
            ),
        ) { (sender, nonce, resultAddress) ->
            Address.computeCreate(Address(sender), nonce) shouldBe Address(
                resultAddress,
            )
        }
    }

    // cases taken from: https://github.com/ethereum/EIPs/blob/master/EIPS/eip-1014.md#examples
    context("compute address using CREATE2 opcode") {
        data class Create2Case(val sender: String, val salt: String, val initCode: String, val resultAddress: String) :
            WithDataTestName {
            val codeHash = Hashing.keccak256(initCode.removePrefix("0x").hexToByteArray())

            override fun dataTestName(): String = toString()
        }

        withData(
            Create2Case(
                "0x0000000000000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x00",
                "0x4D1A2e2bB4F88F0250f26Ffff098B0b30B26BF38",
            ),
            Create2Case(
                "0xdeadbeef00000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x00",
                "0xB928f69Bb1D91Cd65274e3c79d8986362984fDA3",
            ),
            Create2Case(
                "0xdeadbeef00000000000000000000000000000000",
                "0x000000000000000000000000feed000000000000000000000000000000000000",
                "0x00",
                "0xD04116cDd17beBE565EB2422F2497E06cC1C9833",
            ),
            Create2Case(
                "0x00000000000000000000000000000000deadbeef",
                "0x00000000000000000000000000000000000000000000000000000000cafebabe",
                "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                "0x1d8bfDC5D46DC4f61D6b6115972536eBE6A8854C",
            ),
        ) { case ->
            Address.computeCreate2(
                Address(case.sender),
                case.salt.removePrefix("0x").hexToByteArray(),
                case.codeHash,
            ) shouldBe Address(case.resultAddress)
        }
    }

    test("serialization / deserialization") {
        val address = Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec")
        val jsonString = Jackson.MAPPER.writeValueAsString(address)
        val deserializedObject = Jackson.MAPPER.readValue(jsonString, Address::class.java)

        deserializedObject shouldBe address
    }

    // cases taken from: https://eips.ethereum.org/EIPS/eip-1191
    context("checksum") {
        context("without chain id") {
            withData(
                "0x27b1fdb04752bbc536007a920d24acb045561c26",
                "0x3599689E6292b81B2d85451025146515070129Bb",
                "0x42712D45473476b98452f434e72461577D686318",
                "0x52908400098527886E0F7030069857D2E4169EE7",
                "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
                "0x6549f4939460DE12611948b3f82b88C3C8975323",
                "0x66f9664f97F2b50F62D13eA064982f936dE76657",
                "0x8617E340B3D01FA5F11F306F4090FD50E238070D",
                "0x88021160C5C792225E4E5452585947470010289D",
                "0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb",
                "0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB",
                "0xde709f2102306220921060314715629080e2fb77",
                "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
            ) { expected ->
                Address(expected).toChecksumString() shouldBe expected
            }
        }

        context("with chain id 30") {
            withData(
                "0x27b1FdB04752BBc536007A920D24ACB045561c26",
                "0x3599689E6292B81B2D85451025146515070129Bb",
                "0x42712D45473476B98452f434E72461577d686318",
                "0x52908400098527886E0F7030069857D2E4169ee7",
                "0x5aaEB6053f3e94c9b9a09f33669435E7ef1bEAeD",
                "0x6549F4939460DE12611948B3F82B88C3C8975323",
                "0x66F9664f97f2B50F62d13EA064982F936de76657",
                "0x8617E340b3D01Fa5f11f306f4090fd50E238070D",
                "0x88021160c5C792225E4E5452585947470010289d",
                "0xD1220A0Cf47c7B9BE7a2e6ba89F429762E7B9adB",
                "0xDBF03B407c01E7CD3cBea99509D93F8Dddc8C6FB",
                "0xDe709F2102306220921060314715629080e2FB77",
                "0xFb6916095cA1Df60bb79ce92cE3EA74c37c5d359",
            ) { expected ->
                Address(expected).toChecksumString(30) shouldBe expected
            }
        }

        context("with chain id 31") {
            withData(
                "0x27B1FdB04752BbC536007a920D24acB045561C26",
                "0x3599689e6292b81b2D85451025146515070129Bb",
                "0x42712D45473476B98452F434E72461577D686318",
                "0x52908400098527886E0F7030069857D2e4169EE7",
                "0x5aAeb6053F3e94c9b9A09F33669435E7EF1BEaEd",
                "0x6549f4939460dE12611948b3f82b88C3c8975323",
                "0x66f9664F97F2b50f62d13eA064982F936DE76657",
                "0x8617e340b3D01fa5F11f306F4090Fd50e238070d",
                "0x88021160c5C792225E4E5452585947470010289d",
                "0xd1220a0CF47c7B9Be7A2E6Ba89f429762E7b9adB",
                "0xdbF03B407C01E7cd3cbEa99509D93f8dDDc8C6fB",
                "0xDE709F2102306220921060314715629080e2Fb77",
                "0xFb6916095CA1dF60bb79CE92ce3Ea74C37c5D359",
            ) { expected ->
                Address(expected).toChecksumString(31) shouldBe expected
            }
        }
    }
})
