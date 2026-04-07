package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.intellij.lang.annotations.Language
import kotlin.reflect.KClass

/**
 * Evaluate JS functions on the relevant EVM hooks.
 *
 * The result is returned as a raw JSON string, allowing callers to parse it as needed.
 */
data class JSTracer(
    @param:Language("JavaScript")
    val code: String,
    override val config: Map<String, Any?>,
) : Tracer<JSTracer.Result> {
    override val name: String
        get() = code

    override val resultType: KClass<Result>
        get() = Result::class

    /**
     * Result of a JS tracer, containing the raw JSON output as a string.
     *
     * @property json The raw JSON string returned by the tracer.
     */
    @JsonDeserialize(using = ResultDeserializer::class)
    data class Result(val json: String)

    private class ResultDeserializer : JsonDeserializer<Result>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Result {
            // Read the tree and convert back to string to get raw JSON
            val tree = p.readValueAsTree<JsonNode>()
            return Result(tree.toString())
        }
    }
}
