package io.ethers.solana.codec

import io.ethers.core.Result
import io.ethers.solana.types.SolanaBytes
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Binary codec for a program value.
 *
 * [read] and [write] operate on a shared cursor, so a struct's codec reads its fields directly
 * through [BorshReader] without copying. [decode] and [encode] handle a whole payload: [decode]
 * requires every byte to be consumed, and reports invalid input as a [SolanaDecodeError] rather than
 * throwing.
 */
interface SolanaCodec<T> {
    /** Read one value at the cursor. Invalid input throws [SolanaDecodeException]. */
    fun read(reader: BorshReader): T

    /** Append [value] to [writer]. A value the layout cannot represent throws [IllegalArgumentException]. */
    fun write(writer: BorshWriter, value: T)

    /** Decode [data] as exactly one value. */
    fun decode(data: ByteArray): Result<T, SolanaDecodeError> = BorshReader.decode(data, 0, true, ::read)

    /** Decode [data] as exactly one value. */
    fun decode(data: SolanaBytes): Result<T, SolanaDecodeError> = decode(data.asByteArray())

    fun encode(value: T): ByteArray = BorshWriter().also { write(it, value) }.toByteArray()

    companion object {
        /** Zero bytes, for payloads without fields. */
        @JvmField
        val UNIT: SolanaCodec<Unit> = of({ }, { })

        /**
         * A codec for a value without a named type of its own, such as a `u64` instruction return or
         * a `Vec<Pubkey>` alias.
         */
        @JvmStatic
        fun <T> of(read: BorshReader.() -> T, write: BorshWriter.(T) -> Unit): SolanaCodec<T> = LambdaCodec(read, write)
    }
}

private class LambdaCodec<T>(
    private val reader: BorshReader.() -> T,
    private val writer: BorshWriter.(T) -> Unit,
) : SolanaCodec<T> {
    override fun read(reader: BorshReader): T = reader.reader()
    override fun write(writer: BorshWriter, value: T) = writer.writer(value)
}

/**
 * An explicit option, for the nested options a nullable type cannot represent: `Option<Option<T>>`
 * decodes `Some(None)` as `Some(None)` rather than as null.
 */
sealed class SolanaOption<out T> {
    data class Some<out T>(val value: T) : SolanaOption<T>()

    data object None : SolanaOption<Nothing>()

    /** The value, or null for [None]. Loses the nesting distinction this type exists to keep. */
    fun getOrNull(): T? = (this as? Some)?.value
}
