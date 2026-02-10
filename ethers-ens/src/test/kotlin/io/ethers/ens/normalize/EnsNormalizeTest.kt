package io.ethers.ens.normalize

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EnsNormalizeTest : FunSpec({
    val mapper = ObjectMapper()

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
        EnsNormalize.normalize("RaFFY\uD83D\uDEB4\u200D\u2642\uFE0F.eTh") shouldBe "raffy\uD83D\uDEB4\u200D\u2642.eth"
    }

    test("normalize throws on invalid label extension") {
        val result = runCatching { EnsNormalize.normalize("AB--") }
        (result.exceptionOrNull() is InvalidLabelException) shouldBe true
    }

    test("normalize throws on leading combining mark after stop") {
        val result = runCatching { EnsNormalize.normalize("..\u0300") }
        (result.exceptionOrNull() is InvalidLabelException) shouldBe true
    }

    test("normalize throws on illegal mixture") {
        val result = runCatching { EnsNormalize.normalize("\u03BF\u043E") }
        (result.exceptionOrNull() is InvalidLabelException) shouldBe true
    }

    test("NFC/NFD string round-trips") {
        val nf = EnsNormalize.NF
        StringUtils.implode(nf.NFC(0x65, 0x300)) shouldBe "\u00E8"
        StringUtils.implode(nf.NFD(0xE8)) shouldBe "\u0065\u0300"
    }

    test("NF validation tests") {
        val nf = EnsNormalize.NF
        val root = mapper.readTree(
            EnsNormalizeTest::class.java.getResourceAsStream("/io/ethers/ens/normalize/nf-tests.json"),
        )

        var errors = 0
        val fieldNames = root.fieldNames()
        while (fieldNames.hasNext()) {
            val section = root.get(fieldNames.next())
            for (test in section) {
                val input = test[0].asText()
                val expectedNfd = test[1].asText()
                val expectedNfc = test[2].asText()

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
        val root = mapper.readTree(
            EnsNormalizeTest::class.java.getResourceAsStream("/io/ethers/ens/normalize/tests.json"),
        )

        var total = 0
        var errors = 0
        val errorDetails = mutableListOf<String>()
        for (node: JsonNode in root) {
            val nameNode = node.get("name") ?: continue
            val name = nameNode.asText()
            val expectedNorm = node.get("norm")?.asText() ?: name
            val shouldError = node.get("error")?.asBoolean() ?: false
            total++

            try {
                val norm = EnsNormalize.normalize(name)
                if (shouldError) {
                    errors++
                    if (errorDetails.size < 20) {
                        val hex = StringUtils.toHexSequence(StringUtils.explode(name))
                        errorDetails.add("Expected error but got norm for [$hex] comment=${node.get("comment")?.asText()}")
                    }
                } else if (norm != expectedNorm) {
                    errors++
                    if (errorDetails.size < 20) {
                        val hexName = StringUtils.toHexSequence(StringUtils.explode(name))
                        val hexExpected = StringUtils.toHexSequence(StringUtils.explode(expectedNorm))
                        val hexGot = StringUtils.toHexSequence(StringUtils.explode(norm))
                        errorDetails.add("Wrong norm for [$hexName] expected=[$hexExpected] got=[$hexGot] comment=${node.get("comment")?.asText()}")
                    }
                }
            } catch (e: InvalidLabelException) {
                if (!shouldError) {
                    errors++
                    if (errorDetails.size < 20) {
                        val hex = StringUtils.toHexSequence(StringUtils.explode(name))
                        errorDetails.add("Unexpected error for [$hex] comment=${node.get("comment")?.asText()} err=${e.message}")
                    }
                }
            }
        }
        if (errors > 0) {
            error("ENSIP-15 validation: $errors errors out of $total tests\n${errorDetails.joinToString("\n")}")
        }
    }
})
