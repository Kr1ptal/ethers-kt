package io.ethers.solana.codec

import io.ethers.core.Result
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/** A decoded program event occurrence: its payload fields, plus where it was emitted. */
interface SolanaEvent {
    val context: SolanaEventContext
}

/**
 * Identifies and decodes one event type, whether it was emitted to the logs or through a
 * self-CPI. Callers extract the event data from its envelope first; the factory sees the
 * discriminator and payload only.
 */
interface SolanaEventFactory<T : SolanaEvent> {
    val name: String
    val discriminator: SolanaDiscriminator

    /** Read the payload fields at the cursor, which is positioned after the discriminator. */
    fun read(reader: BorshReader, context: SolanaEventContext): T

    /** Write the payload fields of [event], without the discriminator or its context. */
    fun write(writer: BorshWriter, event: T)

    fun matches(data: ByteArray): Boolean = discriminator.matches(data)

    fun matches(data: SolanaBytes): Boolean = discriminator.matches(data)

    /** Decode event [data], discriminator included, which must contain nothing after the payload. */
    fun decode(data: ByteArray, context: SolanaEventContext): Result<T, SolanaDecodeError> {
        return decodeDiscriminated(discriminator, data, "a $name event", true) { read(it, context) }
    }

    fun decode(data: SolanaBytes, context: SolanaEventContext): Result<T, SolanaDecodeError> = decode(data.asByteArray(), context)

    /** Encode the discriminator and payload fields of [event]. The context is not encoded. */
    fun encode(event: T): ByteArray = BorshWriter().apply {
        writeRaw(discriminator.asByteArray())
        write(this, event)
    }.toByteArray()
}

/**
 * Where and in which execution an event was emitted.
 *
 * [programId] is the program that emitted the event, which a caller decoding standalone bytes
 * supplies. Nothing is filled in that the source did not provide: see [SolanaExecutionContext].
 */
data class SolanaEventContext @JvmOverloads constructor(
    val programId: SolanaAddress,
    val source: SolanaEventSource = SolanaEventSource.Standalone,
    val execution: SolanaExecutionContext = SolanaExecutionContext.UNKNOWN,
)

/** Position of an event within its transaction. */
sealed interface SolanaEventSource {
    /** Decoded from bytes supplied directly, with no transaction position. */
    data object Standalone : SolanaEventSource

    /**
     * Emitted to the program logs by the top-level instruction [instructionIndex], at [logIndex] in
     * the transaction's log messages, while the emitting program ran at [stackHeight] (1 for a
     * top-level invocation).
     */
    data class Log(val instructionIndex: Int, val logIndex: Int, val stackHeight: Int) : SolanaEventSource

    /**
     * Emitted as the self-CPI [innerIndex] among the inner instructions of top-level instruction
     * [instructionIndex]. [stackHeight] is null when the RPC node did not report it.
     */
    data class Cpi(val instructionIndex: Int, val innerIndex: Int, val stackHeight: Int?) : SolanaEventSource
}

/**
 * Transaction details known when a program value was observed. Each field is null or
 * [SolanaExecutionStatus.UNKNOWN] when its source does not report it: a simulation has no
 * signature, and bytes decoded standalone have no execution at all.
 *
 * [incomplete] marks data that may be missing entries, such as logs the node truncated.
 */
data class SolanaExecutionContext @JvmOverloads constructor(
    val signature: SolanaSignature? = null,
    val slot: Long? = null,
    val status: SolanaExecutionStatus = SolanaExecutionStatus.UNKNOWN,
    val incomplete: Boolean = false,
) {
    companion object {
        @JvmField
        val UNKNOWN = SolanaExecutionContext()
    }
}

/**
 * Whether the execution that produced a value succeeded. Values observed in a [FAILED] execution
 * were produced, but none of their state changes were committed.
 */
enum class SolanaExecutionStatus {
    SUCCEEDED,
    FAILED,
    UNKNOWN,
}
