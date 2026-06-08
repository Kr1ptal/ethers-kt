package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Kotlinx
import kotlinx.serialization.Serializable
import java.io.InputStream

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
    @Serializable
    private data class HardhatArtifact(
        val abi: List<JsonAbiItem>,
        val bytecode: String? = null,
    )

    override fun read(abi: InputStream): JsonAbi {
        val artifact = Kotlinx.DEFAULT.decodeFromString(HardhatArtifact.serializer(), abi.bufferedReader().readText())
        return JsonAbi(artifact.abi, artifact.bytecode)
    }
}
