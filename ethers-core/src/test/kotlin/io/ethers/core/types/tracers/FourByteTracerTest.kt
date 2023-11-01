package io.ethers.core.types.tracers

import io.ethers.core.Jackson
import io.ethers.core.Jackson.createAndInitParser
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

class FourByteTracerTest : FunSpec({
    val fourByteTracer = FourByteTracer

    test("encode tracer") {
        Jackson.MAPPER.writeValueAsString(TracerConfig(fourByteTracer)) shouldEqualJson """
            {
              "tracer": "4byteTracer",
              "tracerConfig": {}
            }
        """
    }

    test("decodeResult") {
        @Language("JSON")
        val jsonString = """
            {
              "0x0902f1ac-0": 1,
              "0x022c0d9f-160": 1,
              "0x3593564c-640": 1,
              "0xd0e30db0-0": 1,
              "0xa9059cbb-64": 2,
              "0x70a08231-32": 5
            }
        """.trimIndent()

        val jsonParser = Jackson.MAPPER.createAndInitParser(jsonString)
        val result = FourByteTracer.decodeResult(jsonParser)

        result shouldBe hashMapOf(
            "0x022c0d9f-160" to 1,
            "0x0902f1ac-0" to 1,
            "0x3593564c-640" to 1,
            "0x70a08231-32" to 5,
            "0xa9059cbb-64" to 2,
            "0xd0e30db0-0" to 1,
        )
    }
})
