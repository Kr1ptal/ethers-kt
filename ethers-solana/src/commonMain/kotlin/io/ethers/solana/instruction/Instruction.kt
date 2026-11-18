package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes

/**
 * A single program invocation.
 *
 * This is the only instruction type the transaction layer knows about: built-in instructions are
 * factory functions on the program objects in this package (e.g. [SystemProgram.transfer]), and
 * instructions for any other program are constructed directly.
 *
 * The class is open so typed instructions, such as the ones generated from a Solana IDL, can extend
 * it and add their decoded accounts and arguments, then be passed anywhere an [Instruction] is
 * accepted. The wire fields [programId], [keys] and [data] are final and read-only, so no subclass
 * can change them: message compilation can read [data] as many times as it needs - to validate the
 * encoded size and then to serialize - and see the same bytes. [keys] is not copied; pass an
 * immutable list.
 *
 * Equality is by wire content: two instructions are equal when their [programId], [keys] and [data]
 * are equal, whatever their concrete classes, so a subclass instance equals a plain [Instruction]
 * that encodes to the same bytes. [equals] and [hashCode] are final, which keeps that relation
 * symmetric; a data class subclass inherits them instead of generating its own. [toString] stays
 * open so subclasses can describe their typed fields.
 */
open class Instruction(
    val programId: SolanaAddress,
    val keys: List<AccountMeta>,
    val data: SolanaBytes,
) {
    /** Takes ownership of [data] rather than copying it, so do not mutate the array afterwards. */
    constructor(programId: SolanaAddress, keys: List<AccountMeta>, data: ByteArray) :
        this(programId, keys, SolanaBytes.fromBytes(data))

    /**
     * Returns a plain [Instruction] with the given fields replaced. Typed fields of a subclass are not
     * carried over, since the replaced wire fields may no longer match them.
     */
    fun copy(
        programId: SolanaAddress = this.programId,
        keys: List<AccountMeta> = this.keys,
        data: SolanaBytes = this.data,
    ): Instruction = Instruction(programId, keys, data)

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Instruction) return false
        return programId == other.programId && keys == other.keys && data == other.data
    }

    final override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + keys.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }

    override fun toString(): String = "Instruction(programId=$programId, keys=$keys, data=$data)"
}
