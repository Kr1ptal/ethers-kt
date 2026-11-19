package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.SuppliedRpcRequest
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.SolanaAccountConfig
import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Resolves the accounts of one instruction that were not provided, from the ways its IDL gives to derive them:
 * a fixed address, a program derived address, or relations to accounts whose data names them. Generated program
 * bindings build a resolver for each instruction; it can also be built by hand.
 *
 * Accounts are referred to by their index in [accounts]. Each account the caller [provided][Account.provided] is used
 * as given and never re-derived or checked. An optional account that was not provided stays absent. Every other
 * account takes the first of its [derivations][Account.derivations] that does not depend on an absent account, or
 * fails with [SolanaAccountResolutionError.MissingAccount] when none does.
 *
 * Derivations that read account data fetch the accounts they need with `getMultipleAccounts`, every account needed in
 * one round in one request, and repeat for as many rounds as the dependencies between accounts take. Each round reads
 * no older state than the round before it.
 *
 * Account data is only read from accounts that [programId] owns, as one of the account types the derivation expects.
 * Resolution fails with the first error found, in the order of [accounts].
 */
class SolanaAccountResolver(
    @JvmField val programId: SolanaAddress,
    @JvmField val accounts: List<Account>,
) {
    init {
        for (account in accounts) {
            for (derivation in account.derivations) {
                for (dependency in derivation.dependencies) {
                    require(dependency.account in accounts.indices) { "Account '${account.path}' depends on account ${dependency.account}, but there are ${accounts.size}" }
                }
            }
        }
    }

    /**
     * An account of the instruction at [path], `name` or `group.name`, with the address the caller [provided], if any,
     * and the [derivations] its IDL gives, in order of preference.
     */
    class Account @JvmOverloads constructor(
        @JvmField val path: String,
        @JvmField val provided: SolanaAddress?,
        @JvmField val optional: Boolean = false,
        @JvmField val derivations: List<SolanaAccountDerivation> = emptyList(),
    )

    /** Resolve the accounts, fetching those whose data a derivation reads at [commitment]. */
    @JvmOverloads
    fun resolve(api: SolanaApi, commitment: Commitment = api.defaultCommitment): RpcRequest<SolanaResolvedAccounts, SolanaAccountResolutionError> {
        return SuppliedRpcRequest { Resolution(api, commitment).run() }
    }

    internal inner class Resolution(private val api: SolanaApi, private val commitment: Commitment) {
        private val addresses = arrayOfNulls<SolanaAddress>(accounts.size)
        private val absent = BooleanArray(accounts.size)
        private val chosen = arrayOfNulls<SolanaAccountDerivation>(accounts.size)

        /** Fetched accounts, null where none exists, with the context each was read in. */
        private val fetched = HashMap<SolanaAddress, AccountInfo?>()
        private val contexts = HashMap<SolanaAddress, RpcContext>()
        private val decoded = HashMap<Pair<Int, SolanaAccountFactory<*>>, Any?>()
        private var minContextSlot: BigInteger? = null

        private fun resolved(i: Int) = addresses[i] != null || absent[i]

        suspend fun run(): Result<SolanaResolvedAccounts, SolanaAccountResolutionError> {
            accounts.forEachIndexed { i, account ->
                when {
                    account.provided != null -> addresses[i] = account.provided
                    account.optional -> absent[i] = true
                }
            }
            accounts.forEachIndexed { i, account ->
                if (resolved(i)) return@forEachIndexed
                chosen[i] = choose(account) ?: return failure(missing(account))
            }

            while (true) {
                var progress = false
                val needed = LinkedHashSet<SolanaAddress>()
                for (i in accounts.indices) {
                    if (resolved(i)) continue
                    val derivation = chosen[i]!!
                    if (derivation.dependencies.any { !resolved(it.account) }) continue
                    var ready = true
                    for (dependency in derivation.dependencies) {
                        if (dependency.types.isEmpty()) continue
                        val address = addresses[dependency.account]!!
                        if (address !in fetched) {
                            needed.add(address)
                            ready = false
                        }
                    }
                    if (!ready) continue
                    addresses[i] = evaluate(i, derivation).unwrapOrReturn { return failure(it) }
                    progress = true
                }
                if (accounts.indices.all(::resolved)) return success(SolanaResolvedAccounts(accounts.map { it.path }, addresses.toList()))
                if (needed.isNotEmpty()) {
                    fetch(needed.toList())?.let { return failure(it) }
                    progress = true
                }
                if (!progress) return failure(cycle())
            }
        }

        /** The first derivation of [account] that does not depend on an absent account, without relations to absent accounts. */
        private fun choose(account: Account): SolanaAccountDerivation? {
            for (derivation in account.derivations) {
                when (derivation) {
                    is SolanaAccountDerivation.Relations -> {
                        val present = derivation.sources.filter { !absent[it.account] }
                        if (present.isNotEmpty()) return if (present.size == derivation.sources.size) derivation else SolanaAccountDerivation.Relations(present)
                    }
                    else -> if (derivation.dependencies.none { absent[it.account] }) return derivation
                }
            }
            return null
        }

        private fun missing(account: Account): SolanaAccountResolutionError.MissingAccount {
            if (account.derivations.isEmpty()) {
                return SolanaAccountResolutionError.MissingAccount(account.path, "it was not provided, and the IDL gives no way to derive it")
            }
            val optional = account.derivations.flatMap { it.dependencies }.filter { absent[it.account] }.map { "'${accounts[it.account].path}'" }.distinct()
            return SolanaAccountResolutionError.MissingAccount(
                account.path,
                "it was not provided, and every way to derive it depends on an optional account that was not provided: ${optional.joinToString()}",
            )
        }

        private fun evaluate(i: Int, derivation: SolanaAccountDerivation): Result<SolanaAddress, SolanaAccountResolutionError> {
            val path = accounts[i].path
            return when (derivation) {
                is SolanaAccountDerivation.Fixed -> success(derivation.address)
                is SolanaAccountDerivation.Pda -> {
                    for (dependency in derivation.dependencies) {
                        if (dependency.types.isNotEmpty()) data(dependency.account, dependency.types, i).unwrapOrReturn { return failure(it) }
                    }
                    try {
                        success(derivation.derive(Inputs(this)))
                    } catch (e: IllegalArgumentException) {
                        failure(SolanaAccountResolutionError.InvalidSeeds(path, e.message ?: e.toString()))
                    }
                }
                is SolanaAccountDerivation.Relations -> {
                    val candidates = LinkedHashMap<String, SolanaAddress>()
                    for (source in derivation.sources) {
                        val (factory, value) = data(source.account, source.candidates.map { it.factory }, i).unwrapOrReturn { return failure(it) }
                        candidates[accounts[source.account].path] = source.candidates.first { it.factory === factory }.read(value)
                    }
                    val distinct = candidates.values.distinct()
                    if (distinct.size == 1) success(distinct[0]) else failure(SolanaAccountResolutionError.AmbiguousRelation(path, candidates))
                }
            }
        }

        /** The data of fetched account [j], as whichever of [types] its discriminator identifies, for account [requiredBy]. */
        private fun data(j: Int, types: List<SolanaAccountFactory<*>>, requiredBy: Int): Result<Pair<SolanaAccountFactory<*>, Any?>, SolanaAccountResolutionError> {
            val path = accounts[j].path
            val by = accounts[requiredBy].path
            val address = addresses[j]!!
            val info = fetched[address] ?: return failure(SolanaAccountResolutionError.AccountNotFound(path, address, contexts.getValue(address), by))
            if (info.owner != programId) return failure(SolanaAccountResolutionError.OwnerMismatch(path, address, info.owner, programId, by))
            val factory = types.firstOrNull { it.matches(info.data) }
                ?: return failure(SolanaAccountResolutionError.AccountTypeMismatch(path, address, types.map { it.name }, by))
            val key = j to factory
            if (key !in decoded) {
                decoded[key] = factory.decode(info.data).unwrapOrReturn {
                    return failure(SolanaAccountResolutionError.Undecodable(path, address, factory.name, it, by))
                }
            }
            return success(factory to decoded[key])
        }

        fun address(account: Int): SolanaAddress = checkNotNull(addresses[account]) { "Account '${accounts[account].path}' is not resolved" }

        fun <T> decodedData(account: Int, factory: SolanaAccountFactory<T>): T {
            val key = account to factory
            check(key in decoded) { "Account '${accounts[account].path}' has not been read as ${factory.name}" }
            @Suppress("UNCHECKED_CAST")
            return decoded[key] as T
        }

        private suspend fun fetch(needed: List<SolanaAddress>): SolanaAccountResolutionError? {
            for (chunk in needed.chunked(MAX_ACCOUNTS_PER_REQUEST)) {
                val config = SolanaAccountConfig(commitment = commitment, minContextSlot = minContextSlot)
                val response = api.getMultipleAccounts(chunk, config).send().unwrapOrReturn { return SolanaAccountResolutionError.Rpc(it) }
                chunk.forEachIndexed { k, address ->
                    fetched[address] = response.value.getOrNull(k)
                    contexts[address] = response.context
                }
                val slot = response.context.slot
                if (minContextSlot.let { it == null || slot > it }) minContextSlot = slot
            }
            return null
        }

        /** Every unresolved account waits on another unresolved one, so following those waits leads around a cycle. */
        private fun cycle(): SolanaAccountResolutionError.DependencyCycle {
            val visited = ArrayList<Int>()
            var current = accounts.indices.first { !resolved(it) }
            while (current !in visited) {
                visited.add(current)
                current = chosen[current]!!.dependencies.first { !resolved(it.account) }.account
            }
            val cycle = visited.subList(visited.indexOf(current), visited.size) + current
            return SolanaAccountResolutionError.DependencyCycle(cycle.map { accounts[it].path })
        }
    }

    /** What a [SolanaAccountDerivation.Pda] reads to derive an address: resolved addresses and fetched account data. */
    class Inputs internal constructor(private val resolution: SolanaAccountResolver.Resolution) {
        /** The address of [account], one of the derivation's dependencies. */
        fun address(account: Int): SolanaAddress = resolution.address(account)

        /** The data of [account] as [factory]'s type, one of the derivation's data dependencies. */
        fun <T> data(account: Int, factory: SolanaAccountFactory<T>): T = resolution.decodedData(account, factory)
    }

    companion object {
        /** The most accounts `getMultipleAccounts` takes in one request. */
        private const val MAX_ACCOUNTS_PER_REQUEST = 100
    }
}

/** A way the IDL gives to derive an account's address. */
sealed class SolanaAccountDerivation {
    /** The accounts this derivation reads. */
    abstract val dependencies: List<SolanaAccountDependency>

    /** A fixed address. */
    class Fixed(@JvmField val address: SolanaAddress) : SolanaAccountDerivation() {
        override val dependencies: List<SolanaAccountDependency> get() = emptyList()
    }

    /** A program derived address, which [derive] computes from the addresses and data of its [dependencies]. */
    class Pda(
        override val dependencies: List<SolanaAccountDependency>,
        @JvmField val derive: (SolanaAccountResolver.Inputs) -> SolanaAddress,
    ) : SolanaAccountDerivation()

    /** The address that a field of each of [sources]' data holds, which must all agree. */
    class Relations(@JvmField val sources: List<SolanaRelationSource>) : SolanaAccountDerivation() {
        init {
            require(sources.isNotEmpty()) { "Relations need at least one source" }
        }

        override val dependencies: List<SolanaAccountDependency> = sources.map { source -> SolanaAccountDependency(source.account, source.candidates.map { it.factory }) }
    }
}

/**
 * A dependency on [account]: on its address alone when [types] is empty, or otherwise on its data, which must be of
 * one of [types] and owned by the resolver's program.
 */
class SolanaAccountDependency(@JvmField val account: Int, @JvmField val types: List<SolanaAccountFactory<*>> = emptyList()) {
    companion object {
        /** A dependency on the address of [account]. */
        @JvmStatic
        fun address(account: Int) = SolanaAccountDependency(account)

        /** A dependency on the data of [account], as [type]. */
        @JvmStatic
        fun data(account: Int, type: SolanaAccountFactory<*>) = SolanaAccountDependency(account, listOf(type))
    }
}

/**
 * A relation source: [account], whose data is one of the [candidates]' types, and holds the address of the related
 * account in a field the candidate reads.
 */
class SolanaRelationSource(@JvmField val account: Int, @JvmField val candidates: List<SolanaRelationCandidate<*>>) {
    init {
        require(candidates.isNotEmpty()) { "A relation source needs at least one candidate type" }
    }
}

/** An account type a relation source can have, and the field of it that holds the related account's address. */
class SolanaRelationCandidate<T>(@JvmField val factory: SolanaAccountFactory<T>, private val field: (T) -> SolanaAddress) {
    @Suppress("UNCHECKED_CAST")
    internal fun read(value: Any?): SolanaAddress = field(value as T)
}

/** Resolved accounts, by their index in the resolver, with null for absent optional accounts. */
class SolanaResolvedAccounts internal constructor(private val paths: List<String>, private val addresses: List<SolanaAddress?>) {
    val size: Int get() = addresses.size

    /** The address of [account], or null when it is an absent optional account. */
    operator fun get(account: Int): SolanaAddress? = addresses[account]

    /** The address of [account], which must not be an absent optional account. */
    fun required(account: Int): SolanaAddress = checkNotNull(addresses[account]) { "Account '${paths[account]}' is absent" }

    /** The address of the account at [path], or null when it is absent or there is none. */
    fun byPath(path: String): SolanaAddress? = paths.indexOf(path).takeIf { it >= 0 }?.let { addresses[it] }

    override fun toString(): String = paths.indices.joinToString(prefix = "SolanaResolvedAccounts(", postfix = ")") { "${paths[it]}=${addresses[it]}" }
}
