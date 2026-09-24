package io.ethers.solana.idl.reader

import io.ethers.solana.idl.IdlProgram
import java.io.InputStream
import java.net.URL

/**
 * Reader for a single program IDL format, producing an [IdlProgram].
 *
 * A reader signals that it cannot handle the input by throwing. [SolanaIdlReaderRegistry] catches the
 * exception and moves on to the next reader, so throwing is how a reader says "not my format".
 *
 * Readers only translate the format: [SolanaIdlReaderRegistry] validates what they return.
 * */
fun interface SolanaIdlReader {
    /**
     * Reads the IDL from the given [URL].
     *
     * @throws Exception if the URL does not contain an IDL that this reader can read.
     * */
    fun read(idl: URL): IdlProgram = idl.openStream().use(::read)

    /**
     * Reads the IDL from the given [String].
     *
     * @throws Exception if the string does not contain an IDL that this reader can read.
     * */
    fun read(idl: String): IdlProgram = read(idl.byteInputStream())

    /**
     * Reads the IDL from the given [InputStream].
     *
     * @throws Exception if the stream does not contain an IDL that this reader can read.
     * */
    fun read(idl: InputStream): IdlProgram
}
