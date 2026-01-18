package io.ethers.core.types.tracers

import io.ethers.core.Jackson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

class JSTracerTest : FunSpec({
    val jsTracer = JSTracer(
        "<script></script>",
        hashMapOf(
            "config_1" to "string-param",
            "config_2" to 1L,
            "config_3" to true,
        ),
    )

    test("encode tracer") {
        Jackson.MAPPER.writeValueAsString(TracerConfig(jsTracer)) shouldEqualJson """
            {
              "tracer": "<script></script>",
              "tracerConfig": {
                "config_1": "string-param",
                "config_2": 1,
                "config_3": true
              }
            }
        """
    }

    test("decode result - returns raw JSON string") {
        @Language("JSON")
        val jsonString = """{"result_1": "value_1", "result_2": "value_2", "result_3": "value_3"}"""

        // JSTracer returns Result wrapper containing the raw JSON string
        val result = Jackson.MAPPER.readValue(jsonString, jsTracer.resultType.java)

        result shouldBe JSTracer.Result("""{"result_1":"value_1","result_2":"value_2","result_3":"value_3"}""")
    }
})
