package io.ethers.solana.types.transaction

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
    accounts.groupingBy { it }.eachCount().forEach { (address, count) ->
        if (count > 1) {
            return SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.DUPLICATE_ACCOUNT, "Account $address appears more than once")
        }
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
        (lookup.writableIndexes + lookup.readonlyIndexes).forEach {
            if (it !in 0..255) {
                return SolanaTransactionError.InvalidMessage(
                    SolanaTransactionError.Reason.LOOKUP_INDEX,
                    "Lookup table $table references address index $it outside 0..255",
                )
            }
        }
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
    val message = tx.serializeMessage()
    val signers = tx.signers
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
