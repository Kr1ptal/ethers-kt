package io.ethers.solana.codec

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Cursor over an instruction's account metas, assigning them to account roles in order.
 *
 * A role's required privileges must be present; extra privileges are accepted, as merging accounts
 * across a transaction can elevate them. Without [checkSigners], signer privileges are not required:
 * an inner instruction's accounts carry the transaction's flags, and a program can sign for an
 * account through `invoke_signed` without the transaction marking it a signer. An optional account
 * that was not passed is represented by [programId] in its slot. Accounts after the last role are
 * left for [remaining].
 *
 * Accounts that do not fit throw [SolanaDecodeException] carrying [SolanaDecodeError.InvalidAccounts];
 * [decode] converts it back into a [Result].
 */
class SolanaAccountsReader @JvmOverloads constructor(
    private val accounts: List<AccountMeta>,
    private val programId: SolanaAddress,
    private val checkSigners: Boolean = true,
) {
    private var index = 0

    private fun next(name: String): AccountMeta {
        if (index >= accounts.size) fail("Missing account '$name' at position $index")
        return accounts[index++]
    }

    private fun checkPrivileges(meta: AccountMeta, name: String, signer: Boolean, writable: Boolean) {
        if (checkSigners && signer && !meta.signer) fail("Account '$name' at position ${index - 1} must be a signer")
        if (writable && !meta.writable) fail("Account '$name' at position ${index - 1} must be writable")
    }

    /** The address in the next slot, which must have the given privileges. */
    fun required(name: String, signer: Boolean, writable: Boolean): SolanaAddress {
        val meta = next(name)
        checkPrivileges(meta, name, signer, writable)
        return meta.publicKey
    }

    /** The address in the next slot, or null when the slot holds the program id placeholder. */
    fun optional(name: String, signer: Boolean, writable: Boolean): SolanaAddress? {
        val meta = next(name)
        if (meta.publicKey == programId) return null
        checkPrivileges(meta, name, signer, writable)
        return meta.publicKey
    }

    /** The accounts after the last role read so far. */
    fun remaining(): List<AccountMeta> = accounts.subList(index, accounts.size)

    fun fail(message: String): Nothing = throw SolanaDecodeException(SolanaDecodeError.InvalidAccounts(accounts, message))

    companion object {
        /** Run [read] over the accounts of [instruction], converting a thrown [SolanaDecodeException] into a failure. */
        @JvmStatic
        @JvmOverloads
        fun <T> decode(instruction: Instruction, checkSigners: Boolean = true, read: (SolanaAccountsReader) -> T): Result<T, SolanaDecodeError> {
            return try {
                success(read(SolanaAccountsReader(instruction.keys, instruction.programId, checkSigners)))
            } catch (e: SolanaDecodeException) {
                failure(e.error)
            }
        }
    }
}
