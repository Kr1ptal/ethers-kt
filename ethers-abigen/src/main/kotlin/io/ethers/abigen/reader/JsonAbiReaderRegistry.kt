package io.ethers.abigen.reader

import io.ethers.abigen.JsonAbi
import io.ethers.abigen.reader.JsonAbiReaderRegistry.appendReader
import io.ethers.abigen.reader.JsonAbiReaderRegistry.prependReader
import io.ethers.abigen.reader.JsonAbiReaderRegistry.tryReadAbi
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
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
     * Read the ABI from the given URL. Returns null if the URL does not contain an ABI that any of the readers can read.
     *
     * See [tryReadAbi] for a version that returns a [Result] with all errors instead.
     *
     * @return the [JsonAbi], or null if the URL does not contain an ABI that any of the readers can read.
     * */
    fun readAbi(abi: URL): JsonAbi? {
        for (i in readers.indices) {
            try {
                val jsonAbi = readers[i].read(abi)
                if (jsonAbi != null) {
                    return jsonAbi
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Read the ABI from the given URL. On failure, it returns a [AbiReadError] containing a list of all exceptions
     * that were thrown by the readers.
     *
     * @return the resulting [JsonAbi], or an [AbiReadError] if reading failed.
     * */
    fun tryReadAbi(abi: URL): Result<JsonAbi, AbiReadError> {
        var causes: MutableList<Exception>? = null
        for (i in readers.indices) {
            try {
                val jsonAbi = readers[i].read(abi)
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
    class AbiReadError(val url: URL, val causes: List<Exception>) : Result.Error {
        override fun doThrow(): Nothing {
            throw RuntimeException("Failed to read ABI: $url").also { parent ->
                causes.forEach { parent.addSuppressed(it) }
            }
        }
    }
}
