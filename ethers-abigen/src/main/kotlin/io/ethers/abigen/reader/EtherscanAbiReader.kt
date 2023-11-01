package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Jackson
import java.net.URL

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
    private val reader = Jackson.MAPPER.readerFor(JsonAbiItem::class.java)

    override fun read(abi: URL): JsonAbi {
        val abiItems = reader.readValues<JsonAbiItem>(abi).readAll()
        return JsonAbi(abiItems, null)
    }
}
