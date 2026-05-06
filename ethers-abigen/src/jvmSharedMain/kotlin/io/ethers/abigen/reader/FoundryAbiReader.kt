package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Kotlinx
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    @Serializable
    private data class Artifact(
        val abi: List<JsonAbiItem>,
        val bytecode: Bytecode? = null,
    ) {
        @Serializable
        data class Bytecode(
            @SerialName("object") val bytecodeHex: String,
        )
    }

    override fun read(abi: InputStream): JsonAbi {
        val artifact = Kotlinx.DEFAULT.decodeFromString(Artifact.serializer(), abi.bufferedReader().readText())
        return JsonAbi(artifact.abi, artifact.bytecode?.bytecodeHex)
    }
}
