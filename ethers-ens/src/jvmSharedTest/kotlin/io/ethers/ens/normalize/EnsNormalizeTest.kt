package io.ethers.ens.normalize

import io.ethers.core.Kotlinx
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EnsNormalizeTest : FunSpec({
    test("codepoint coding round-trip") {
        val sb = StringBuilder()
        var errors = 0
        for (cp in 0 until 0x110000) {
            sb.setLength(0)
            StringUtils.appendCodepoint(sb, cp)
            StringUtils.appendCodepoint(sb, cp)
            val cps = StringUtils.explode(sb.toString())
            if (cps.size != 2 || cps[0] != cp || cps[1] != cp) {
                errors++
            }
        }
        errors shouldBe 0
    }

    test("normalize basic examples") {
        EnsNormalize.normalize("RaFFY🚴‍♂️.eTh") shouldBe "raffy🚴‍♂.eth"
    }

    test("normalize throws on invalid label extension") {
        val result = runCatching { EnsNormalize.normalize("AB--") }
        (result.exceptionOrNull() is InvalidLabelException) shouldBe true
    }

    test("normalize throws on leading combining mark after stop") {
        val result = runCatching { EnsNormalize.normalize("..̀") }
        (result.exceptionOrNull() is InvalidLabelException) shouldBe true
    }

    test("normalize throws on illegal mixture") {
        val result = runCatching { EnsNormalize.normalize("οо") }
        (result.exceptionOrNull() is InvalidLabelException) shouldBe true
    }

    test("NFC/NFD string round-trips") {
        val nf = EnsNormalize.NF
        StringUtils.implode(nf.NFC(0x65, 0x300)) shouldBe "è"
        StringUtils.implode(nf.NFD(0xE8)) shouldBe "è"
    }

    test("NF validation tests") {
        val nf = EnsNormalize.NF
        val root = Kotlinx.DEFAULT.parseToJsonElement(
            EnsNormalizeTest::class.java.getResourceAsStream("/io/ethers/ens/normalize/nf-tests.json")!!
                .bufferedReader().readText(),
        ).jsonObject

        var errors = 0
        for ((_, section) in root) {
            for (test in section.jsonArray) {
                val arr = test.jsonArray
                val input = arr[0].jsonPrimitive.content
                val expectedNfd = arr[1].jsonPrimitive.content
                val expectedNfc = arr[2].jsonPrimitive.content

                val inputCps = StringUtils.explode(input)
                val nfd = StringUtils.implode(nf.NFD(*inputCps))
                val nfc = StringUtils.implode(nf.NFC(*inputCps))

                if (nfd != expectedNfd) errors++
                if (nfc != expectedNfc) errors++
            }
        }
        errors shouldBe 0
    }

    test("ENSIP-15 validation tests") {
        val root = Kotlinx.DEFAULT.parseToJsonElement(
            EnsNormalizeTest::class.java.getResourceAsStream("/io/ethers/ens/normalize/tests.json")!!
                .bufferedReader().readText(),
        ).jsonArray

        var total = 0
        var errors = 0
        val errorDetails = mutableListOf<String>()
        for (node in root) {
            val obj = node.jsonObject
            val nameNode = obj["name"] ?: continue
            val name = nameNode.jsonPrimitive.content
            val expectedNorm = obj["norm"]?.jsonPrimitive?.content ?: name
            val shouldError = obj["error"]?.jsonPrimitive?.boolean ?: false
            total++

            try {
                val norm = EnsNormalize.normalize(name)
                if (shouldError) {
                    errors++
                    if (errorDetails.size < 20) {
                        val hex = StringUtils.toHexSequence(StringUtils.explode(name))
                        errorDetails.add("Expected error but got norm for [$hex] comment=${obj["comment"]?.jsonPrimitive?.content}")
                    }
                } else if (norm != expectedNorm) {
                    errors++
                    if (errorDetails.size < 20) {
                        val hexName = StringUtils.toHexSequence(StringUtils.explode(name))
                        val hexExpected = StringUtils.toHexSequence(StringUtils.explode(expectedNorm))
                        val hexGot = StringUtils.toHexSequence(StringUtils.explode(norm))
                        errorDetails.add("Wrong norm for [$hexName] expected=[$hexExpected] got=[$hexGot] comment=${obj["comment"]?.jsonPrimitive?.content}")
                    }
                }
            } catch (e: InvalidLabelException) {
                if (!shouldError) {
                    errors++
                    if (errorDetails.size < 20) {
                        val hex = StringUtils.toHexSequence(StringUtils.explode(name))
                        errorDetails.add("Unexpected error for [$hex] comment=${obj["comment"]?.jsonPrimitive?.content} err=${e.message}")
                    }
                }
            }
        }
        if (errors > 0) {
            error("ENSIP-15 validation: $errors errors out of $total tests\n${errorDetails.joinToString("\n")}")
        }
    }
})
