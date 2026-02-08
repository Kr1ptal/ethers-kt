package io.ethers.abigen.reader

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Jackson
import java.io.InputStream

/**
 * Reader for Foundry ABI files. Foundry ABI files are JSON files that contain an object with `abi` and `bytecode`
 * fields, where the `bytecode` field is an object with an `object` field which contains the actual bytecode.
 *
 * Format:
 * ```json
 * {
 *     "abi": [
 *         { "type": "function", "name": "foo", ... },
 *         { "type": "function", "name": "bar", ... }
 *     ],
 *     "bytecode": { "object": "0x...", ... }
 * }
 * */
object FoundryAbiReader : JsonAbiReader {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Artifact(
        @param:JsonProperty("abi") val abi: List<JsonAbiItem>,
        @param:JsonProperty("bytecode") val bytecode: Bytecode?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Bytecode(
            @param:JsonProperty("object") val `object`: String,
        )
    }

    override fun read(abi: InputStream): JsonAbi {
        val artifact = Jackson.MAPPER.readValue(abi, Artifact::class.java)
        return JsonAbi(artifact.abi, artifact.bytecode?.`object`)
    }
}
