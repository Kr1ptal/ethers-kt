package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import java.io.InputStream
import java.net.URL

fun interface JsonAbiReader {
    /**
     * Reads the ABI from the given [URL]. Returns null if the URL does not contain an ABI that this
     * reader can read.
     *
     * @return the ABI, or null if the URL does not contain an ABI that this reader can read.
     * */
    fun read(abi: URL): JsonAbi? = read(abi.openStream())

    /**
     * Reads the ABI from the given [String]. Returns null if the string does not contain an ABI that this
     * reader can read.
     *
     * @return the ABI, or null if the string does not contain an ABI that this reader can read.
     * */
    fun read(abi: String): JsonAbi? {
        return read(abi.byteInputStream())
    }

    /**
     * Reads the ABI from the given [InputStream]. Returns null if the stream does not contain an ABI that this
     * reader can read.
     *
     * @return the ABI, or null if the input stream does not contain an ABI that this reader can read.
     * */
    fun read(abi: InputStream): JsonAbi?
}
