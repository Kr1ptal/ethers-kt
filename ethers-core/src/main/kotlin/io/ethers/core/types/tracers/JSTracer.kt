package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import org.intellij.lang.annotations.Language

/**
 * Evaluate JS functions on the relevant EVM hooks.
 */
data class JSTracer(
    @param:Language("JavaScript")
    val code: String,
    val config: Map<String, Any>,
) : Tracer<JsonNode> {
    override val name: String
        get() = code

    override fun encodeConfig(gen: JsonGenerator) {
        config.forEach { (key, value) -> gen.writeObjectField(key, value) }
    }

    override fun decodeResult(parser: JsonParser): JsonNode {
        return parser.readValueAsTree()
    }
}
