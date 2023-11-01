package io.ethers.core.types

import io.ethers.core.Jackson
import io.ethers.crypto.Hashing
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
})
