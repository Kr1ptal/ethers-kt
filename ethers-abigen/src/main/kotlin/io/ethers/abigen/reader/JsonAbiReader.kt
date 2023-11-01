package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import java.net.URL

fun interface JsonAbiReader {
    /**
     * Reads the ABI from the given URL. Returns null if the URL does not contain an ABI that this
     * reader can read.
     *
     * @return the ABI, or null if the URL does not contain an ABI that this reader can read.
     * */
    fun read(abi: URL): JsonAbi?
}
