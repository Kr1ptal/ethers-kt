package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import java.io.InputStream
import java.net.URL

/**
 * Reader for a single ABI file format.
 *
 * A reader signals that it cannot handle the input by throwing - the built-in readers delegate format detection to
 * the JSON deserializer and let its exception propagate. [JsonAbiReaderRegistry] catches it and moves on to the next
 * reader, so throwing is how a reader says "not my format".
 *
 * This is why these functions are not named `readOrNull`: they never return null, they throw.
 * */
fun interface JsonAbiReader {
    /**
     * Reads the ABI from the given [URL].
     *
     * @return the ABI.
     * @throws Exception if the URL does not contain an ABI that this reader can read.
     * */
    fun read(abi: URL): JsonAbi = read(abi.openStream())

    /**
     * Reads the ABI from the given [String].
     *
     * @return the ABI.
     * @throws Exception if the string does not contain an ABI that this reader can read.
     * */
    fun read(abi: String): JsonAbi {
        return read(abi.byteInputStream())
    }

    /**
     * Reads the ABI from the given [InputStream].
     *
     * @return the ABI.
     * @throws Exception if the stream does not contain an ABI that this reader can read.
     * */
    fun read(abi: InputStream): JsonAbi
}
