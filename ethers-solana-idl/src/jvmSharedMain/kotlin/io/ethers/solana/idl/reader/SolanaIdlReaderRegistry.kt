package io.ethers.solana.idl.reader

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlValidator
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry of [SolanaIdlReader]s. By default, it contains [AnchorIdlReader], then [AnchorLegacyIdlReader].
 *
 * Custom readers can be added by calling [prependReader] or [appendReader]. The readers are called in order, and the
 * first reader that can read the IDL is used. Its result is then checked with [IdlValidator]: an IDL that was read but
 * fails validation is reported as [IdlReadError.Invalid], without trying the remaining readers.
 * */
object SolanaIdlReaderRegistry {
    private val readers = CopyOnWriteArrayList(listOf(AnchorIdlReader, AnchorLegacyIdlReader))

    /**
     * Add [SolanaIdlReader] to the beginning of the list of readers.
     * */
    @JvmStatic
    fun prependReader(reader: SolanaIdlReader) {
        readers.add(0, reader)
    }

    /**
     * Add [SolanaIdlReader] to the end of the list of readers.
     * */
    @JvmStatic
    fun appendReader(reader: SolanaIdlReader) {
        readers.add(reader)
    }

    /**
     * Read and validate the IDL from the given [String]. Returns null if no reader can read it or it fails validation.
     *
     * See [tryReadIdl] for a version that returns a [Result] with the reason instead.
     * */
    @JvmStatic
    fun readIdlOrNull(idl: String): IdlProgram? = tryReadIdl(idl).unwrapOrNull()

    /**
     * Read and validate the IDL from the given [URL]. Returns null if no reader can read it or it fails validation.
     *
     * See [tryReadIdl] for a version that returns a [Result] with the reason instead.
     * */
    @JvmStatic
    fun readIdlOrNull(idl: URL): IdlProgram? = tryReadIdl(idl).unwrapOrNull()

    /**
     * Read and validate the IDL from the given [InputStream]. Returns null if no reader can read it or it fails
     * validation.
     *
     * See [tryReadIdl] for a version that returns a [Result] with the reason instead.
     * */
    @JvmStatic
    fun readIdlOrNull(idl: InputStream): IdlProgram? = tryReadIdl(idl).unwrapOrNull()

    /**
     * Read and validate the IDL from the given [String].
     *
     * @return the [IdlProgram], or an [IdlReadError] if no reader could read it or it failed validation.
     * */
    @JvmStatic
    fun tryReadIdl(idl: String): Result<IdlProgram, IdlReadError> {
        return read(idl.encodeToByteArray(), IdlReadError.StringSource(idl))
    }

    /**
     * Read and validate the IDL from the given [URL].
     *
     * @return the [IdlProgram], or an [IdlReadError] if no reader could read it or it failed validation.
     * */
    @JvmStatic
    fun tryReadIdl(idl: URL): Result<IdlProgram, IdlReadError> {
        return read(idl.openStream().use { it.readAllBytes() }, IdlReadError.URLSource(idl))
    }

    /**
     * Read and validate the IDL from the given [InputStream].
     *
     * @return the [IdlProgram], or an [IdlReadError] if no reader could read it or it failed validation.
     * */
    @JvmStatic
    fun tryReadIdl(idl: InputStream): Result<IdlProgram, IdlReadError> {
        return read(idl.readAllBytes(), IdlReadError.InputStreamSource(idl))
    }

    private fun read(bytes: ByteArray, source: IdlReadError.Source): Result<IdlProgram, IdlReadError> {
        var causes: MutableList<Exception>? = null
        for (reader in readers) {
            val program = try {
                reader.read(ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                if (causes == null) causes = ArrayList()
                causes.add(e)
                continue
            }
            return try {
                IdlValidator.validate(program)
                success(program)
            } catch (e: IllegalArgumentException) {
                failure(IdlReadError.Invalid(source, program, e))
            }
        }
        return failure(IdlReadError.Unreadable(source, causes ?: emptyList()))
    }

    /**
     * Error returned when reading an IDL fails.
     * */
    sealed class IdlReadError : ThrowableError {
        abstract val source: Source

        /**
         * No reader could read the IDL. [causes] holds each reader's exception in order.
         * */
        data class Unreadable(override val source: Source, val causes: List<Exception>) : IdlReadError() {
            override val message get() = "Failed to read IDL: $source"

            // overridden to attach every reader's failure as a suppressed exception, which a single cause cannot express
            override fun toException(): RuntimeException {
                return ThrowableError.Exception(this).also { parent ->
                    causes.forEach { parent.addSuppressed(it) }
                }
            }
        }

        /** A reader read the IDL as [program], but it failed validation; [cause] describes the problem. */
        data class Invalid(override val source: Source, val program: IdlProgram, override val cause: IllegalArgumentException) :
            IdlReadError() {
            override val message get() = "Invalid IDL $source: ${cause.message}"
        }

        sealed interface Source
        data class URLSource(val url: URL) : Source
        data class InputStreamSource(val inputStream: InputStream) : Source
        data class StringSource(val string: String) : Source
    }
}
