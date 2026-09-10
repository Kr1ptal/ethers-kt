package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * What every Solana transaction carries, whether this library compiled it or a node returned it.
 *
 * The counterpart of the EVM `Transaction` interface: a read-only view with no encoding of its own,
 * so a response holding a version this library cannot construct still satisfies it. Transactions this
 * library compiled itself are [SolanaTransactionCompiled], which can also be written back to the wire.
 */
interface SolanaTransaction {
    val type: SolanaTxType
    val header: MessageHeader
    val accounts: List<SolanaAddress>
    val recentBlockhash: SolanaBlockhash
    val instructions: List<CompiledInstruction>
    val feePayer: SolanaAddress get() = accounts.first()
    val signers: List<SolanaAddress> get() = accounts.take(header.requiredSignatures)

    /**
     * Compute units this message may consume, or null when it leaves the runtime's default in place.
     *
     * Legacy and v0 state the ComputeBudget settings as instructions and v1 states them inline, so
     * these read the same either way. None of them is required: most messages set none.
     */
    val computeUnitLimit: Long?

    /**
     * Micro-lamports per compute unit, as legacy and v0 state priority.
     *
     * Null on v1, which states a total instead: a total is not exactly representable as an integer
     * price, so v1 reports [priorityFee] rather than a rounded price that would not reproduce it.
     */
    val computeUnitPrice: BigInteger?

    /**
     * Total priority fee in lamports, which v1 states directly and legacy and v0 only imply.
     *
     * Null when no priority is stated, and when a nonzero price has no compute unit limit to
     * multiply, since the runtime would apply a default limit this library cannot predict.
     */
    val priorityFee: BigInteger?

    /** Combined size of the accounts this message may load, or null for the runtime's default. */
    val loadedAccountsDataSizeLimit: Long?

    /** Heap space this message may use, or null for the runtime's default of 32 KiB. */
    val heapSize: Long?

    /**
     * Lookup tables this message loads addresses from, empty for the versions that cannot.
     *
     * These name a table and the slots taken from it, not the addresses themselves: resolving those
     * needs the table's contents, which [toRequest] takes as an argument.
     */
    val addressLookupTables: List<CompiledAddressLookupTable> get() = emptyList()

    /**
     * Every account this message names, resolved to an address and tagged with the signer and
     * writable flags its slot implies, in index order: `resolvedAccounts()[i]` is what index `i` in a
     * compiled instruction refers to.
     *
     * This is the read-side counterpart of [toRequest]: it answers who signs, what is written and
     * which program an index names, without rebuilding an editable request to do it.
     *
     * Throws [SolanaTransactionException] when the message cannot be resolved; [tryResolveAccounts]
     * reports the same failure as a value.
     */
    fun resolveAccounts(): List<AccountMeta> = tryResolveAccounts().unwrap()

    /** As [resolveAccounts], resolving loaded addresses against the supplied lookup tables. */
    fun resolveAccounts(tables: List<AddressLookupTableAccount>): List<AccountMeta> = tryResolveAccounts(tables).unwrap()

    /** As [resolveAccounts], returning the reason the message could not be resolved instead of throwing. */
    fun tryResolveAccounts(): Result<List<AccountMeta>, SolanaTransactionError> = tryResolveAccounts(emptyList())

    /** As [resolveAccounts], returning the reason the message could not be resolved instead of throwing. */
    fun tryResolveAccounts(tables: List<AddressLookupTableAccount>): Result<List<AccountMeta>, SolanaTransactionError> = resolveAccounts(this, tables, null)

    /**
     * Recover the request that compiles to this transaction, so a transaction that was built
     * elsewhere or read back from a node can be re-priced, re-blockhashed, extended, simulated or
     * filled. Signatures are not carried across, since a request is unsigned by definition.
     *
     * The inverse is semantic, not byte-for-byte: compiling the result picks lookup tables afresh and
     * orders accounts canonically, so a message compiled by another library, or against a different
     * set of tables, comes back with the same meaning but not the same bytes.
     *
     * Throws [SolanaTransactionException] when the message cannot be resolved; [tryToRequest] reports
     * the same failure as a value.
     */
    fun toRequest(): SolanaTransactionRequest = tryToRequest().unwrap()

    /** As [toRequest], resolving loaded addresses against the supplied lookup tables. */
    fun toRequest(tables: List<AddressLookupTableAccount>): SolanaTransactionRequest = tryToRequest(tables).unwrap()

    /** As [toRequest], returning the reason the message could not be resolved instead of throwing. */
    fun tryToRequest(): Result<SolanaTransactionRequest, SolanaTransactionError> = tryToRequest(emptyList())

    /** As [toRequest], returning the reason the message could not be resolved instead of throwing. */
    fun tryToRequest(tables: List<AddressLookupTableAccount>): Result<SolanaTransactionRequest, SolanaTransactionError> = decompile(this, tables, null)

    /**
     * Estimate base + priority fee in lamports. A nonzero price requires an explicit compute-unit limit;
     * runtime defaults depend on the invoked programs. The node's getFeeForMessage is authoritative.
     */
    fun estimateFee(lamportsPerSignature: BigInteger): BigInteger {
        requireU64(lamportsPerSignature)
        val priority = priorityFee
        val price = computeUnitPrice
        require(priority != null || price == null || price.signum() == 0) { "Specify a compute-unit limit or query getFeeForMessage" }
        return lamportsPerSignature.multiply(bigIntegerOf(header.requiredSignatures)).add(priority ?: bigIntegerOf(0))
    }
}
