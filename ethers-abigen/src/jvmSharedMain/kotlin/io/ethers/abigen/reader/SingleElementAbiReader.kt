package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Kotlinx
import java.io.InputStream

/**
 * Reader for single-element ABI files.
 *
 * Format:
 * ```json
 * { "type": "function", "name": "foo", ... }
 * ```
 *
 * */
object SingleElementAbiReader : JsonAbiReader {
    override fun read(abi: InputStream): JsonAbi {
        val abiItem = Kotlinx.DEFAULT.decodeFromString(JsonAbiItem.serializer(), abi.bufferedReader().readText())
        return JsonAbi(listOf(abiItem), null)
    }
}
