package io.ethers.solana.types.transaction

import io.ethers.solana.serialization.U8List
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature

/** Structural invariants shared by every message version, reported as a value rather than thrown. */
internal fun messageError(
    header: MessageHeader,
    accounts: List<SolanaAddress>,
    instructions: List<MessageInstruction>,
    lookups: List<CompiledAddressLookupTable>,
): SolanaTransactionError? {
    if (accounts.size !in 1..256) return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, accounts.size, 1..256)
    firstDuplicate(accounts)?.let { address ->
        return SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.DUPLICATE_ACCOUNT, "Account $address appears more than once")
    }
    if (header.requiredSignatures !in 1..minOf(127, accounts.size)) {
        return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.SIGNERS, header.requiredSignatures, 1..minOf(127, accounts.size))
    }
    if (header.readonlySignedAccounts !in 0 until header.requiredSignatures) {
        return SolanaTransactionError.InvalidMessage(
            SolanaTransactionError.Reason.FEE_PAYER_READONLY,
            "Fee payer must be writable, but ${header.readonlySignedAccounts} of ${header.requiredSignatures} signers are readonly",
        )
    }
    if (header.readonlyUnsignedAccounts !in 0..(accounts.size - header.requiredSignatures)) {
        return SolanaTransactionError.CountOutOfRange(
            SolanaTransactionError.Limit.READONLY_ACCOUNTS,
            header.readonlyUnsignedAccounts,
            0..(accounts.size - header.requiredSignatures),
        )
    }
    val totalAccounts = accounts.size + lookups.sumOf { it.writableIndexes.size + it.readonlyIndexes.size }
    if (totalAccounts > 256) return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, totalAccounts, 1..256)
    lookups.forEachIndexed { table, lookup ->
        lookupIndexError(table, lookup.writableIndexes)?.let { return it }
        lookupIndexError(table, lookup.readonlyIndexes)?.let { return it }
    }
    instructions.forEachIndexed { index, instruction ->
        if (instruction.programIdIndex !in 1 until accounts.size) {
            return SolanaTransactionError.InvalidMessage(
                SolanaTransactionError.Reason.ACCOUNT_INDEX,
                "Instruction $index names program index ${instruction.programIdIndex} outside 1 until ${accounts.size}",
            )
        }
        instruction.accounts.forEach {
            if (it !in 0 until totalAccounts) {
                return SolanaTransactionError.InvalidMessage(
                    SolanaTransactionError.Reason.ACCOUNT_INDEX,
                    "Instruction $index references account index $it outside 0 until $totalAccounts",
                )
            }
        }
    }
    return null
}

private fun lookupIndexError(table: Int, indexes: List<Int>): SolanaTransactionError? {
    // decoded indices are single bytes, so they cannot fall outside the range
    if (indexes is U8List) return null
    indexes.forEach {
        if (it !in 0..255) {
            return SolanaTransactionError.InvalidMessage(
                SolanaTransactionError.Reason.LOOKUP_INDEX,
                "Lookup table $table references address index $it outside 0..255",
            )
        }
    }
    return null
}

/**
 * The repeated address whose first appearance comes earliest, or null if every address is distinct.
 * Probes an open-addressing table of account indices, which needs one small array rather than a
 * hash map entry and a boxed count per account.
 */
private fun firstDuplicate(accounts: List<SolanaAddress>): SolanaAddress? {
    var capacity = 16
    while (capacity < accounts.size * 2) capacity = capacity shl 1
    val mask = capacity - 1
    // account index + 1, so that zero marks an empty slot
    val table = IntArray(capacity)
    var earliest = -1
    for (i in accounts.indices) {
        val address = accounts[i]
        var slot = address.hashCode() and mask
        while (true) {
            val entry = table[slot]
            if (entry == 0) {
                table[slot] = i + 1
                break
            }
            if (accounts[entry - 1] == address) {
                if (earliest < 0 || entry - 1 < earliest) earliest = entry - 1
                break
            }
            slot = (slot + 1) and mask
        }
    }
    return if (earliest < 0) null else accounts[earliest]
}

internal fun signatureError(
    tx: SolanaTransactionUnsigned,
    signatures: List<SolanaSignature?>,
): SolanaTransactionError? {
    if (signatures.size != tx.header.requiredSignatures) {
        return SolanaTransactionError.InvalidMessage(
            SolanaTransactionError.Reason.SIGNATURE_COUNT,
            "Envelope carries ${signatures.size} signatures, but the message requires ${tx.header.requiredSignatures}",
        )
    }
    val message = tx.messageBytes()
    // signers lead the account list, so index it rather than slicing out a copy
    val signers = tx.accounts
    signatures.forEachIndexed { index, signature ->
        if (signature != null && !signers[index].verify(signature, message)) {
            return SolanaTransactionError.InvalidSignature(index, signers[index])
        }
    }
    return null
}

internal fun validateSignatures(tx: SolanaTransactionUnsigned, signatures: List<SolanaSignature?>) {
    signatureError(tx, signatures)?.let { throw it.toException() }
}
