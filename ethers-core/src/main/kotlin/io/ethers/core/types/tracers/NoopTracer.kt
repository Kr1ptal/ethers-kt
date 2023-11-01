package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser

/**
 * Perform no action.
 *
 * It's mostly useful for testing purposes.
 */
data object NoopTracer : Tracer<Unit> {
    override val name: String
        get() = "noopTracer"

    override fun encodeConfig(gen: JsonGenerator) {
        // no config
    }

    override fun decodeResult(parser: JsonParser) {
        // skip returned '{}' object
        parser.skipChildren()
    }
}
