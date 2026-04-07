package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.JsonAbiItem
import io.ethers.core.Jackson
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
    private val reader = Jackson.MAPPER.readerFor(JsonAbiItem::class.java)

    override fun read(abi: InputStream): JsonAbi {
        val abiItem = reader.readValue<JsonAbiItem>(abi)
        return JsonAbi(listOf(abiItem), null)
    }
}
