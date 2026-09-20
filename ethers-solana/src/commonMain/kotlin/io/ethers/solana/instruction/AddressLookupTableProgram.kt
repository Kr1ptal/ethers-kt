package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.ProgramDerivedAddress
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.littleEndianInto
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Instruction builders for the lookup tables used by v0 transactions. No RPC calls are performed. */
object AddressLookupTableProgram {
    @JvmField val ID = Programs.ADDRESS_LOOKUP_TABLE

    /** The authority and a recent slot determine the table address; the payer is not a seed. */
    @JvmStatic
    fun deriveLookupTableAddress(authority: SolanaAddress, recentSlot: BigInteger): ProgramDerivedAddress = SolanaAddress.findProgramAddress(listOf(authority.asByteArray(), littleEndian(requireU64(recentSlot), 8)), ID)

    @JvmStatic
    fun deriveLookupTableAddress(authority: SolanaAddress, recentSlot: Long): ProgramDerivedAddress = deriveLookupTableAddress(authority, bigIntegerOf(recentSlot))

    /** Build table creation and return its derived address. Only the payer must sign creation. */
    @JvmStatic
    fun createLookupTable(authority: SolanaAddress, payer: SolanaAddress, recentSlot: BigInteger): LookupTableCreation {
        val derived = deriveLookupTableAddress(authority, recentSlot)
        val data = ByteArray(13)
        littleEndianInto(data, 4, recentSlot, 8)
        data[12] = derived.bump.toByte()
        val instruction = Instruction(
            ID,
            listOf(AccountMeta.writable(derived.address), AccountMeta(authority), AccountMeta.signerAndWritable(payer), AccountMeta(Programs.SYSTEM)),
            data,
        )
        return LookupTableCreation(derived.address, instruction)
    }

    @JvmStatic
    fun createLookupTable(authority: SolanaAddress, payer: SolanaAddress, recentSlot: Long): LookupTableCreation = createLookupTable(authority, payer, bigIntegerOf(recentSlot))

    /**
     * Append addresses, optionally funding additional rent from [payer]. Without a payer the table
     * must already hold sufficient lamports. Split large batches to fit the transaction size limit;
     * the program enforces total capacity and newly added addresses require a later slot to be used.
     */
    @JvmStatic
    @JvmOverloads
    fun extendLookupTable(table: SolanaAddress, authority: SolanaAddress, addresses: List<SolanaAddress>, payer: SolanaAddress? = null): Instruction {
        require(addresses.size in 1..256) { "An extension must contain between 1 and 256 addresses" }
        val data = ByteArray(12 + addresses.size * 32)
        data[0] = 2
        littleEndianInto(data, 4, addresses.size.toLong(), 8)
        addresses.forEachIndexed { index, address -> address.asByteArray().copyInto(data, 12 + index * 32) }
        val accounts = listOf(AccountMeta.writable(table), AccountMeta.signer(authority)) +
            if (payer == null) emptyList() else listOf(AccountMeta.signerAndWritable(payer), AccountMeta(Programs.SYSTEM))
        return Instruction(ID, accounts, data)
    }

    /** Permanently make a nonempty table immutable; a frozen table cannot be extended or closed. */
    @JvmStatic
    fun freezeLookupTable(table: SolanaAddress, authority: SolanaAddress): Instruction = Instruction(ID, listOf(AccountMeta.writable(table), AccountMeta.signer(authority)), byteArrayOf(1, 0, 0, 0))

    /** Start deactivation. The table remains usable during the on-chain cooldown. */
    @JvmStatic
    fun deactivateLookupTable(table: SolanaAddress, authority: SolanaAddress): Instruction = Instruction(ID, listOf(AccountMeta.writable(table), AccountMeta.signer(authority)), byteArrayOf(3, 0, 0, 0))

    /** Reclaim rent after deactivation has completed; the program checks the cooldown. */
    @JvmStatic
    fun closeLookupTable(table: SolanaAddress, authority: SolanaAddress, recipient: SolanaAddress): Instruction = Instruction(ID, listOf(AccountMeta.writable(table), AccountMeta.signer(authority), AccountMeta.writable(recipient)), byteArrayOf(4, 0, 0, 0))
}

/** A creation instruction and the address it will initialize once executed. */
data class LookupTableCreation(val address: SolanaAddress, val instruction: Instruction)
