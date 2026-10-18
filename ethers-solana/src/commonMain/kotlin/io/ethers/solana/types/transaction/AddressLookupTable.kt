package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.U8ListSerializer
import io.ethers.solana.utils.U64_MAX
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * An address lookup table's contents, as needed to compile a v0 message against it.
 *
 * [deactivationSlot] and [authority] are populated by [decode]; a table whose deactivation slot has
 * passed can no longer be used by a transaction.
 *
 * Not a data class: a generated `copy` would re-run the validation without declaring it, which a Swift caller
 * cannot catch. The hand-written members below match what a data class generates.
 */
class AddressLookupTableAccount
@JvmOverloads
@Throws(IllegalArgumentException::class)
constructor(
    val key: SolanaAddress,
    val addresses: List<SolanaAddress>,
    val deactivationSlot: BigInteger? = null,
    val authority: SolanaAddress? = null,
) {
    init {
        if (addresses.size > 256) throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.LOOKUP_TABLE, addresses.size, 0..256).toException()
    }

    @Throws(IllegalArgumentException::class)
    fun copy(
        key: SolanaAddress = this.key,
        addresses: List<SolanaAddress> = this.addresses,
        deactivationSlot: BigInteger? = this.deactivationSlot,
        authority: SolanaAddress? = this.authority,
    ): AddressLookupTableAccount = AddressLookupTableAccount(key, addresses, deactivationSlot, authority)

    operator fun component1(): SolanaAddress = key
    operator fun component2(): List<SolanaAddress> = addresses
    operator fun component3(): BigInteger? = deactivationSlot
    operator fun component4(): SolanaAddress? = authority

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AddressLookupTableAccount) return false
        return key == other.key && addresses == other.addresses && deactivationSlot == other.deactivationSlot && authority == other.authority
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + addresses.hashCode()
        result = 31 * result + (deactivationSlot?.hashCode() ?: 0)
        result = 31 * result + (authority?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "AddressLookupTableAccount(key=$key, addresses=$addresses, deactivationSlot=$deactivationSlot, authority=$authority)"

    companion object {
        /** Bytes preceding the addresses in an on-chain lookup table account. */
        const val META_SIZE: Int = 56

        private const val LOOKUP_TABLE_DISCRIMINANT = 1

        /**
         * Decode an on-chain lookup table account: a [META_SIZE]-byte header followed by 32-byte
         * addresses. A never-deactivated table stores `u64::MAX`, which becomes a null
         * [deactivationSlot].
         */
        @JvmStatic
        fun decode(key: SolanaAddress, data: ByteArray): Result<AddressLookupTableAccount, SolanaTransactionError> {
            if (data.size < META_SIZE || (data.size - META_SIZE) % 32 != 0) {
                return Result.failure(
                    SolanaTransactionError.MalformedBytes("A lookup table account holds $META_SIZE header bytes and whole addresses, got ${data.size}"),
                )
            }
            val decoder = SolanaMessageDecoder(data)
            val discriminant = decoder.readUnsignedLittleEndian(4).toLong()
            if (discriminant != LOOKUP_TABLE_DISCRIMINANT.toLong()) {
                return Result.failure(SolanaTransactionError.MalformedBytes("Account is not a lookup table, got discriminant $discriminant"))
            }
            val deactivationSlot = decoder.readUnsignedLittleEndian(8)
            decoder.readBytes(9) // last extended slot and its start index
            val authority = if (decoder.readByte() == 0) null.also { decoder.readBytes(32) } else SolanaAddress(decoder.readBytes(32))
            decoder.readBytes(2) // padding
            val count = (data.size - META_SIZE) / 32
            if (count > 256) return Result.failure(SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.LOOKUP_TABLE, count, 0..256))
            val addresses = List(count) { SolanaAddress(decoder.readBytes(32)) }
            return Result.success(
                AddressLookupTableAccount(key, addresses, deactivationSlot.takeIf { it != U64_MAX }, authority),
            )
        }
    }
}

/**
 * The addresses one lookup table contributes to a message, as indices into that table.
 *
 * Unknown JSON fields are not retained, unlike the RPC response types: this mirrors a fixed structure
 * of the message binary format, which cannot gain a field without changing that format.
 */
@Serializable
data class CompiledAddressLookupTable(
    @SerialName("accountKey") val key: SolanaAddress,
    @Serializable(with = U8ListSerializer::class) val writableIndexes: List<Int>,
    @Serializable(with = U8ListSerializer::class) val readonlyIndexes: List<Int>,
)
