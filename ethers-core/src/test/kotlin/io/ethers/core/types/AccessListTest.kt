package io.ethers.core.types

import io.ethers.json.jackson.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

class AccessListTest : FunSpec({
    test("AccessList.Item serialization / deserialization") {
        val item = AccessList.Item(
            Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
            listOf(
                Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
            ),
        )

        val jsonString = Jackson.MAPPER.writeValueAsString(item)
        val deserializedObject = Jackson.MAPPER.readValue(jsonString, AccessList.Item::class.java)

        deserializedObject shouldBe item
    }

    test("CreateAccessList deserialization") {
        @Language("JSON")
        val jsonString = """
            {
              "accessList": [
                {
                  "address": "0x38d914b3705c279bf012443dbc093ffdffe523aa",
                  "storageKeys": [
                    "0xfcae5870fd1469a2792ddb98f331ceaea337bd9e2c1781aed74d1e92f0ff3da9"
                  ]
                },
                {
                  "address": "0xb9d2b746e5a983c5f14f314433d211fb8d3810f8",
                  "storageKeys": [
                    "0x0000000000000000000000000000000000000000000000000000000000000008"
                  ]
                },
                {
                  "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                  "storageKeys": [
                    "0xfb19a963956c9cb662dd3ae48988c4b90766df71ea130109840abe0a1b23dba8",
                    "0x990d941a4864e726ea59cb016bfeb69b70c0e1f9c6d122781ee98d42055ee12d"
                  ]
                }
              ],
              "error": "execution reverted",
              "gasUsed": "0xc291"
            }
        """.trimIndent()
        val result = Jackson.MAPPER.readValue(jsonString, CreateAccessList::class.java)

        val expectedResult = CreateAccessList(
            accessList = listOf(
                AccessList.Item(
                    Address("0x38d914b3705c279bf012443dbc093ffdffe523aa"),
                    listOf(
                        Hash("0xfcae5870fd1469a2792ddb98f331ceaea337bd9e2c1781aed74d1e92f0ff3da9"),
                    ),
                ),
                AccessList.Item(
                    Address("0xb9d2b746e5a983c5f14f314433d211fb8d3810f8"),
                    listOf(
                        Hash("0x0000000000000000000000000000000000000000000000000000000000000008"),
                    ),
                ),
                AccessList.Item(
                    Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                    listOf(
                        Hash("0xfb19a963956c9cb662dd3ae48988c4b90766df71ea130109840abe0a1b23dba8"),
                        Hash("0x990d941a4864e726ea59cb016bfeb69b70c0e1f9c6d122781ee98d42055ee12d"),
                    ),
                ),
            ),
            error = "execution reverted",
            gasUsed = 49809,
        )

        result shouldBe expectedResult
    }
})
