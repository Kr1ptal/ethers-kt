package io.ethers.solana.codec

import io.ethers.core.Result
import io.ethers.solana.types.SolanaBytes
import kotlin.jvm.JvmField

/**
 * Instruction data layout: [discriminator] followed by [args].
 *
 * Builders encode through [encodeData] and decoders read through [decodeData], so both share one
 * definition of the layout. Account roles are not part of the data and are bound separately.
 */
open class SolanaInstructionDescriptor<A>(
    @JvmField val name: String,
    @JvmField val discriminator: SolanaDiscriminator,
    @JvmField val args: SolanaCodec<A>,
) {
    fun matches(data: ByteArray): Boolean = discriminator.matches(data)

    fun matches(data: SolanaBytes): Boolean = discriminator.matches(data)

    /** Encode instruction data for [args]. */
    fun encodeData(args: A): ByteArray = BorshWriter().apply {
        writeRaw(discriminator.asByteArray())
        this@SolanaInstructionDescriptor.args.write(this, args)
    }.toByteArray()

    /** Decode the arguments of instruction [data], which must contain nothing after them. */
    fun decodeData(data: ByteArray): Result<A, SolanaDecodeError> = decodeDiscriminated(discriminator, data, "a $name instruction", true, args::read)

    fun decodeData(data: SolanaBytes): Result<A, SolanaDecodeError> = decodeData(data.asByteArray())

    override fun toString(): String = "SolanaInstructionDescriptor($name)"

    /**
     * An instruction that declares a return value.
     *
     * Return data carries no discriminator and names only the program that set it, so the caller
     * must already know which instruction produced it before calling [decodeReturn].
     */
    class Returning<A, R>(
        name: String,
        discriminator: SolanaDiscriminator,
        args: SolanaCodec<A>,
        @JvmField val returns: SolanaCodec<R>,
    ) : SolanaInstructionDescriptor<A>(name, discriminator, args) {
        /** Decode the return [data] this instruction set, which must contain exactly one value. */
        fun decodeReturn(data: ByteArray): Result<R, SolanaDecodeError> = returns.decode(data)

        fun decodeReturn(data: SolanaBytes): Result<R, SolanaDecodeError> = returns.decode(data)

        override fun toString(): String = "SolanaInstructionDescriptor.Returning($name)"
    }
}
