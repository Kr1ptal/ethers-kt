package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Kotlinx
import kotlinx.serialization.builtins.ListSerializer
import java.io.InputStream

/**
 * Reader for Etherscan ABI files. Etherscan ABI files are JSON files that contain a single array of ABI items.
 *
 * Format:
 * ```json
 * [
 *     { "type": "function", "name": "foo", ... },
 *     { "type": "function", "name": "bar", ... }
 * ]
 * ```
 *
 * */
object EtherscanAbiReader : JsonAbiReader {
    override fun read(abi: InputStream): JsonAbi {
        val abiItems = Kotlinx.DEFAULT.decodeFromString(ListSerializer(JsonAbiItem.serializer()), abi.bufferedReader().readText())
        return JsonAbi(abiItems, null)
    }
}
