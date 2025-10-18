package io.ethers.abigen.reader

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Jackson
import java.io.InputStream
import java.net.URL

/**
 * Reader for Hardhat ABI files. Hardhat ABI files are JSON files that contain an object with `abi` and `bytecode`
 * fields.
 *
 * Format:
 * ```json
 * {
 *     "abi": [
 *         { "type": "function", "name": "foo", ... },
 *         { "type": "function", "name": "bar", ... }
 *     ],
 *     "bytecode": "0x..."
 * }
 * */
object HardhatAbiReader : JsonAbiReader {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class HardhatArtifact(
        @param:JsonProperty("abi") val abi: List<JsonAbiItem>,
        @param:JsonProperty("bytecode") val bytecode: String,
    )

    override fun read(abi: InputStream): JsonAbi {
        val artifact = Jackson.MAPPER.readValue(abi, HardhatArtifact::class.java)
        return JsonAbi(artifact.abi, artifact.bytecode)
    }
}
