package io.ethers.aa

import io.ethers.core.types.Address
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Version of the ERC-4337 EntryPoint contract. Each version defines its own [UserOperation] layout on-chain and its
 * own way of computing the user operation hash.
 *
 * @see <a href="https://github.com/eth-infinitism/account-abstraction/releases">EntryPoint releases</a>
 */
enum class EntryPointVersion(val label: String) {
    /** Unpacked on-chain user operation, with `initCode` and `paymasterAndData` concatenations. */
    V0_6("0.6"),

    /** Packed on-chain user operation, with separate paymaster gas limits. */
    V0_7("0.7"),

    /** Like [V0_7], plus EIP-712 user operation hashes and EIP-7702 accounts. */
    V0_8("0.8"),

    /** Like [V0_8], plus paymaster signatures that are excluded from the user operation hash. */
    V0_9("0.9"),
    ;

    /** Whether the on-chain user operation is the packed `PackedUserOperation` struct (v0.7 and later). */
    val usesPackedUserOperation: Boolean
        get() = this >= V0_7

    /** Whether the user operation hash is an EIP-712 typed data hash (v0.8 and later). */
    val usesTypedDataHash: Boolean
        get() = this >= V0_8

    /** Whether the EntryPoint supports EIP-7702 accounts via the `0x7702` init code marker (v0.8 and later). */
    val supportsEip7702: Boolean
        get() = this >= V0_8

    /** Whether `paymasterAndData` may carry a paymaster signature that is not part of the hash (v0.9 and later). */
    val supportsPaymasterSignature: Boolean
        get() = this >= V0_9

    override fun toString(): String = label

    companion object {
        /**
         * Find the version by its [label], e.g. `"0.7"`, or return null if unknown.
         */
        @JvmStatic
        fun fromLabel(label: String): EntryPointVersion? = entries.firstOrNull { it.label == label }
    }
}

/**
 * A deployed ERC-4337 EntryPoint contract at [address], implementing [version].
 *
 * The canonical deployments ([V0_6], [V0_7], [V0_8], [V0_9]) share the same address on every chain. A custom
 * deployment can be described by constructing this class directly.
 */
data class EntryPoint(val address: Address, val version: EntryPointVersion) {
    companion object {
        @JvmField
        val V0_6 = EntryPoint(Address("0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"), EntryPointVersion.V0_6)

        @JvmField
        val V0_7 = EntryPoint(Address("0x0000000071727De22E5E9d8BAf0edAc6f37da032"), EntryPointVersion.V0_7)

        @JvmField
        val V0_8 = EntryPoint(Address("0x4337084D9E255Ff0702461CF8895CE9E3b5Ff108"), EntryPointVersion.V0_8)

        @JvmField
        val V0_9 = EntryPoint(Address("0x433709009B8330FDa32311DF1C2AFA402eD8D009"), EntryPointVersion.V0_9)

        /**
         * All canonical EntryPoint deployments, oldest first.
         */
        @JvmField
        val CANONICAL: List<EntryPoint> = listOf(V0_6, V0_7, V0_8, V0_9)

        /**
         * Return the canonical EntryPoint deployed at [address], or null if [address] is not a canonical deployment.
         */
        @JvmStatic
        fun canonical(address: Address): EntryPoint? = CANONICAL.firstOrNull { it.address == address }

        /**
         * Return the canonical EntryPoint of [version].
         */
        @JvmStatic
        fun canonical(version: EntryPointVersion): EntryPoint = when (version) {
            EntryPointVersion.V0_6 -> V0_6
            EntryPointVersion.V0_7 -> V0_7
            EntryPointVersion.V0_8 -> V0_8
            EntryPointVersion.V0_9 -> V0_9
        }
    }
}
