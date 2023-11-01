package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.readMapOf

/**
 * Search for 4byte-identifiers, and collect them for post-processing.
 * The methods identifiers are collected along with the size of the supplied data, so
 * a reversed signature can be matched against the size of the data.
 *
 * Example:
 *
 * > debug.traceTransaction( "0x214e597e35da083692f5386141e69f47e973b2c56e7a8073b1ea08fd7571e9de", {tracer: "4byteTracer"})
 *	{
 *	  0x27dc297e-128: 1,
 *	  0x38cc4831-0: 2,
 *	  0x524f3889-96: 1,
 *	  0xadf59f99-288: 1,
 *	  0xc281d19e-0: 1
 *	}
 */
data object FourByteTracer : Tracer<Map<String, Int>> {
    override val name: String
        get() = "4byteTracer"

    override fun encodeConfig(gen: JsonGenerator) {
        // no config
    }

    override fun decodeResult(parser: JsonParser): Map<String, Int> {
        return parser.readMapOf({ it }) { intValue }
    }
}
