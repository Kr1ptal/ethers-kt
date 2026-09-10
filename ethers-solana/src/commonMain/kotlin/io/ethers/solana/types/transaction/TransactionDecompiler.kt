package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.LoadedAddresses
import io.ethers.solana.types.SolanaAddress

/**
 * Resolve every account a message names into an address tagged with the signer and writable flags its
 * slot implies, in index order: the inline accounts, then the writable addresses loaded from lookup
 * tables, then the readonly ones. This is the order the runtime itself builds, so `resolved[i]` is
 * what index `i` in a compiled instruction refers to.
 *
 * [loaded] is the already-resolved lookup output when the caller has it, which a node's response
 * does; otherwise the addresses are read out of [tables]. Passing what the node resolved is the more
 * accurate of the two for a historical transaction, since a lookup table's contents can change after
 * the transaction was included.
 */
internal fun resolveAccountMetas(
    tx: SolanaTransaction,
    tables: List<AddressLookupTableAccount>,
    loaded: LoadedAddresses?,
): Result<List<AccountMeta>, SolanaTransactionError> {
    val version = tx.type
    if (version is SolanaTxType.Unsupported) return Result.failure(SolanaTransactionError.UnsupportedVersion(version.version))

    val static = tx.accounts
    if (static.isEmpty()) {
        return Result.failure(SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.ACCOUNT_INDEX, "Message has no accounts, so it names no fee payer"))
    }

    val lookups = tx.addressLookupTables
    val resolved = when {
        loaded != null -> loaded.also {
            val writable = lookups.sumOf { l -> l.writableIndexes.size }
            val readonly = lookups.sumOf { l -> l.readonlyIndexes.size }
            if (it.writable.size != writable || it.readonly.size != readonly) {
                return Result.failure(
                    SolanaTransactionError.InvalidMessage(
                        SolanaTransactionError.Reason.LOOKUP_INDEX,
                        "Resolved ${it.writable.size} writable and ${it.readonly.size} readonly addresses, but the message looks up $writable and $readonly",
                    ),
                )
            }
        }

        lookups.isEmpty() -> LoadedAddresses.NONE
        else -> resolveLookups(lookups, tables).unwrapOrReturn { return Result.failure(it) }
    }

    val header = tx.header
    val signedWritable = header.requiredSignatures - header.readonlySignedAccounts
    val unsignedWritableEnd = static.size - header.readonlyUnsignedAccounts

    val metas = ArrayList<AccountMeta>(static.size + resolved.writable.size + resolved.readonly.size)
    static.forEachIndexed { index, address ->
        val writable = index < signedWritable || (index >= header.requiredSignatures && index < unsignedWritableEnd)
        metas.add(AccountMeta(address, signer = index < header.requiredSignatures, writable = writable))
    }
    // a loaded address is never a signer: the header counts signatures over the inline accounts only
    resolved.writable.forEach { metas.add(AccountMeta(it, writable = true)) }
    resolved.readonly.forEach { metas.add(AccountMeta(it)) }
    return Result.success(metas)
}

/**
 * Turn a compiled message back into the request that produces it, resolving every account index into
 * an address through [resolveAccountMetas].
 */
internal fun decompile(
    tx: SolanaTransaction,
    tables: List<AddressLookupTableAccount>,
    loaded: LoadedAddresses?,
): Result<SolanaTransactionRequest, SolanaTransactionError> {
    val accounts = resolveAccountMetas(tx, tables, loaded).unwrapOrReturn { return Result.failure(it) }

    val instructions = ArrayList<Instruction>(tx.instructions.size)
    for (compiled in tx.instructions) {
        val programId = accounts.getOrNull(compiled.programIdIndex)?.publicKey
            ?: return Result.failure(outOfRange(compiled.programIdIndex, accounts.size))
        val keys = ArrayList<AccountMeta>(compiled.accounts.size)
        for (index in compiled.accounts) {
            keys.add(accounts.getOrNull(index) ?: return Result.failure(outOfRange(index, accounts.size)))
        }
        instructions.add(Instruction(programId, keys, compiled.data))
    }

    val request = SolanaTransactionRequest()
        .feePayer(accounts[0].publicKey)
        .blockhash(tx.recentBlockhash)
        .instructions(instructions)

    // legacy and v0 state the compute budget as instructions, which are carried across verbatim above;
    // restating them on the request would re-emit them in discriminant order and move them
    if (tx.type == SolanaTxType.V1) {
        request.computeUnitLimit(tx.computeUnitLimit)
            .priorityFee(tx.priorityFee)
            .loadedAccountsDataSizeLimit(tx.loadedAccountsDataSizeLimit)
            .heapSize(tx.heapSize)
    }

    return Result.success(request)
}

private fun outOfRange(index: Int, size: Int) = SolanaTransactionError.InvalidMessage(
    SolanaTransactionError.Reason.ACCOUNT_INDEX,
    "Instruction references account $index, but the message resolves only $size accounts",
)

private fun resolveLookups(
    lookups: List<CompiledAddressLookupTable>,
    tables: List<AddressLookupTableAccount>,
): Result<LoadedAddresses, SolanaTransactionError> {
    val byKey = tables.associateBy { it.key }
    val writable = mutableListOf<SolanaAddress>()
    val readonly = mutableListOf<SolanaAddress>()
    for (lookup in lookups) {
        val table = byKey[lookup.key] ?: return Result.failure(
            SolanaTransactionError.InvalidMessage(
                SolanaTransactionError.Reason.UNKNOWN_LOOKUP_TABLE,
                "Message loads addresses from lookup table ${lookup.key}, whose contents were not supplied",
            ),
        )
        for ((indexes, into) in listOf(lookup.writableIndexes to writable, lookup.readonlyIndexes to readonly)) {
            for (index in indexes) {
                val address = table.addresses.getOrNull(index) ?: return Result.failure(
                    SolanaTransactionError.InvalidMessage(
                        SolanaTransactionError.Reason.LOOKUP_INDEX,
                        "Lookup table ${lookup.key} holds ${table.addresses.size} addresses, so it has no slot $index",
                    ),
                )
                into.add(address)
            }
        }
    }
    return Result.success(LoadedAddresses(writable, readonly))
}
