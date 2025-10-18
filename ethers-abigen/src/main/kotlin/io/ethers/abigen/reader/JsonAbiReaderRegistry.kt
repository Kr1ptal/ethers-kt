package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.reader.JsonAbiReaderRegistry.appendReader
import io.ethers.abigen.reader.JsonAbiReaderRegistry.prependReader
import io.ethers.abigen.reader.JsonAbiReaderRegistry.tryReadAbi
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL

/**
 * Registry of [JsonAbiReader]s. This registry is used to read ABIs from URLs. By default, it contains [FoundryAbiReader],
 * [HardhatAbiReader], and [EtherscanAbiReader].
 *
 * Custom readers can be added by calling [prependReader] or [appendReader]. The readers are called in order, and the
 * first reader that can read the ABI is used.
 * */
object JsonAbiReaderRegistry {
    private val readers = ArrayList<JsonAbiReader>().apply {
        add(FoundryAbiReader)
        add(HardhatAbiReader)
        add(EtherscanAbiReader)
        add(SingleElementAbiReader)
    }

    /**
     * Add [JsonAbiReader] to the beginning of the list of readers.
     * */
    fun prependReader(reader: JsonAbiReader) {
        synchronized(readers) {
            readers.add(0, reader)
        }
    }

    /**
     * Add [JsonAbiReader] to the end of the list of readers.
     * */
    fun appendReader(reader: JsonAbiReader) {
        synchronized(readers) {
            readers.add(reader)
        }
    }

    /**
     * Read the ABI from the given [String]. Returns null if the source does not contain an ABI that any of the readers can read.
     *
     * See [tryReadAbi] for a version that returns a [Result] with all errors instead.
     *
     * @return the [JsonAbi], or null if the source does not contain an ABI that any of the readers can read.
     * */
    fun readAbi(abi: String): JsonAbi? {
        return readAbi(abi.byteInputStream())
    }

    /**
     * Read the ABI from the given [URL]. Returns null if the source does not contain an ABI that any of the readers can read.
     *
     * See [tryReadAbi] for a version that returns a [Result] with all errors instead.
     *
     * @return the [JsonAbi], or null if the source does not contain an ABI that any of the readers can read.
     * */
    fun readAbi(abi: URL): JsonAbi? {
        return readAbi(abi.openStream())
    }

    /**
     * Read the ABI from the given [InputStream]. Returns null if the source does not contain an ABI that any of the readers can read.
     *
     * See [tryReadAbi] for a version that returns a [Result] with all errors instead.
     *
     * @return the [JsonAbi], or null if the source does not contain an ABI that any of the readers can read.
     * */
    fun readAbi(abi: InputStream): JsonAbi? {
        val array = abi.readAllBytes()
        for (i in readers.indices) {
            try {
                val jsonAbi = readers[i].read(ByteArrayInputStream(array))
                if (jsonAbi != null) {
                    return jsonAbi
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Read the ABI from the given [String]. On failure, it returns a [AbiReadError] containing a list of all exceptions
     * that were thrown by the readers.
     *
     * @return the resulting [JsonAbi], or an [AbiReadError] if reading failed.
     * */
    fun tryReadAbi(abi: String): Result<JsonAbi, AbiReadError> {
        return tryReadAbi(abi.byteInputStream()).mapError { AbiReadError(abi, it.causes) }
    }

    /**
     * Read the ABI from the given [URL]. On failure, it returns a [AbiReadError] containing a list of all exceptions
     * that were thrown by the readers.
     *
     * @return the resulting [JsonAbi], or an [AbiReadError] if reading failed.
     * */
    fun tryReadAbi(abi: URL): Result<JsonAbi, AbiReadError> {
        return tryReadAbi(abi.openStream()).mapError { AbiReadError(abi, it.causes) }
    }

    /**
     * Read the ABI from the given [String]. On failure, it returns a [AbiReadError] containing a list of all exceptions
     * that were thrown by the readers.
     *
     * @return the resulting [JsonAbi], or an [AbiReadError] if reading failed.
     * */
    fun tryReadAbi(abi: InputStream): Result<JsonAbi, AbiReadError> {
        val array = abi.readAllBytes()
        var causes: MutableList<Exception>? = null
        for (i in readers.indices) {
            try {
                val jsonAbi = readers[i].read(ByteArrayInputStream(array))
                if (jsonAbi != null) {
                    return success(jsonAbi)
                }
            } catch (e: Exception) {
                if (causes == null) {
                    causes = ArrayList()
                }
                causes.add(e)
            }
        }

        return failure(AbiReadError(abi, causes ?: emptyList()))
    }

    /**
     * Error returned when reading ABI fails. It contains a list of all exceptions that were thrown by the readers.
     * */
    data class AbiReadError(val source: Source, val causes: List<Exception>) : Result.Error {
        constructor(url: URL, causes: List<Exception>) : this(URLSource(url), causes)
        constructor(inputStream: InputStream, causes: List<Exception>) : this(InputStreamSource(inputStream), causes)
        constructor(string: String, causes: List<Exception>) : this(StringSource(string), causes)

        override fun doThrow(): Nothing {
            throw RuntimeException("Failed to read ABI: $source").also { parent ->
                causes.forEach { parent.addSuppressed(it) }
            }
        }

        sealed interface Source
        data class URLSource(val url: URL) : Source
        data class InputStreamSource(val inputStream: InputStream) : Source
        data class StringSource(val string: String) : Source
    }
}
