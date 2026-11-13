package io.ethers.solana.codec

import io.ethers.core.Result
import io.ethers.crypto.Base58
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.types.SolanaBytes
import kotlin.jvm.JvmField

/**
 * Identifies and decodes one program account type: [discriminator] followed by a [codec] payload.
 *
 * Account data may be allocated larger than its serialized value, so bytes after the payload are
 * ignored. The factory knows nothing about the owning program or where the data came from: callers
 * that fetched the account check its owner before decoding.
 */
class SolanaAccountFactory<T>(
    @JvmField val name: String,
    @JvmField val discriminator: SolanaDiscriminator,
    @JvmField val codec: SolanaCodec<T>,
) : SolanaAccountDecoder<T> {
    fun matches(data: ByteArray): Boolean = discriminator.matches(data)

    fun matches(data: SolanaBytes): Boolean = discriminator.matches(data)

    /** Decode account [data], ignoring allocation padding after the value. */
    override fun decode(data: ByteArray): Result<T, SolanaDecodeError> = decodeDiscriminated(discriminator, data, "a $name account", false, codec::read)

    fun decode(data: SolanaBytes): Result<T, SolanaDecodeError> = decode(data.asByteArray())

    /** Encode [value] with its discriminator and no padding. */
    fun encode(value: T): ByteArray = BorshWriter().apply {
        writeRaw(discriminator.asByteArray())
        codec.write(this, value)
    }.toByteArray()

    /** A `getProgramAccounts` or program subscription filter matching accounts of this type. */
    fun discriminatorFilter(): AccountFilter = AccountFilter.Memcmp(0, Base58.encode(discriminator.asByteArray()))

    /**
     * A filter matching accounts of this type whose payload holds the bytes [write] produces at [offset],
     * counted from the end of the discriminator. Only meaningful where every value before [offset] has a
     * fixed size.
     */
    fun fieldFilter(offset: Int, write: BorshWriter.() -> Unit): AccountFilter {
        require(offset >= 0) { "Offset must not be negative" }
        return AccountFilter.Memcmp((discriminator.size + offset).toLong(), Base58.encode(BorshWriter().apply(write).toByteArray()))
    }

    override fun toString(): String = "SolanaAccountFactory($name)"
}
