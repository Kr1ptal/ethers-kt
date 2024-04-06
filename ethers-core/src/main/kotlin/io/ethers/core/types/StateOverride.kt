package io.ethers.core.types

/**
 * Represents a [Map] of [AccountOverride] per account [Address]. This class is a wrapper around a map, with additional
 * methods to merge and apply changes from another instance, and safe functionality to create new instances to prevent
 * footguns when working with [AccountOverride]s which are mutable.
 *
 * To create a new instance, use:
 * - no-arg constructor, to create an empty instance,
 * - [StateOverride.wrap], to wrap an existing map without copying the data,
 * - [StateOverride.copy], to create a new instance from an existing map, creating a new copy of all [AccountOverride]s,
 * */
class StateOverride private constructor(
    private val overrides: MutableMap<Address, AccountOverride>,
) : MutableMap<Address, AccountOverride> by overrides {

    @JvmOverloads
    constructor(initialSize: Int = 16) : this(HashMap(initialSize))

    /**
     * Merge **this** with [other], returning a new instance with the merged changes. The original instances are not
     * modified, and a new copy of each [AccountOverride] is created.
     *
     * No references to [AccountOverride]s from **this** and [other] are kept so changes to **this** and [other] after
     * this call will not affect the returned instance. Best used when both **this** and [other] will be used after this
     * call, independently of the merged changes.
     * */
    fun mergeChanges(other: Map<Address, AccountOverride>?): StateOverride {
        val merged = copy(overrides)

        if (other == null) {
            return merged
        }

        for ((address, override) in other) {
            val existing = merged.overrides[address]
            if (existing == null) {
                merged.overrides[address] = AccountOverride(override)
            } else {
                merged.overrides[address] = existing.mergeChanges(override)
            }
        }
        return merged
    }

    /**
     * Apply changes from [other] to **this** instance. The original instance is modified in place.
     *
     * No references to [AccountOverride]s from [other] are kept so changes to [other] after this call will not affect
     * **this*, and vice-versa. Best used when you want to accumulate changes in **this** instance, but [other] will
     * still be used after this call.
     * */
    fun applyChanges(other: Map<Address, AccountOverride>?) {
        if (other == null) {
            return
        }

        for ((address, override) in other) {
            val existing = overrides[address]
            if (existing == null) {
                overrides[address] = AccountOverride(override)
            } else {
                existing.applyChanges(override)
            }
        }
    }

    /**
     * Apply change from [override] for [address] to **this** instance. The original instance is modified in place.
     *
     * No reference to [override] is kept so changes to it after this call will not affect **this*, and vice-versa.
     * Best used when you want to accumulate changes in **this** instance, but [override] will still be used after
     * this call.
     * */
    fun applyChange(address: Address, override: AccountOverride?) {
        if (override == null) {
            return
        }

        val existing = this[address]
        if (existing == null) {
            this[address] = AccountOverride(override)
        } else {
            existing.applyChanges(override)
        }
    }

    /**
     * Take and apply changes from [other] to **this** instance. The original instance is modified in place.
     *
     * References to [AccountOverride]s from [other] will be kept, so changes to [other] after this call will lead to
     * undefined behaviour. Best used when [other] would be discarded after this call.
     * */
    fun takeChanges(other: Map<Address, AccountOverride>?) {
        if (other == null) {
            return
        }

        for ((address, override) in other) {
            val existing = overrides[address]
            if (existing == null) {
                overrides[address] = override
            } else {
                existing.applyChanges(override)
            }
        }
    }

    /**
     * Take and apply changes from [override] for [address] to **this** instance. The original instance is modified in
     * place.
     *
     * Reference to [override] will be kept, so changes to it after this call will lead to undefined behaviour. Best
     * used when [override] would be discarded after this call.
     * */
    fun takeChange(address: Address, override: AccountOverride?) {
        if (override == null) {
            return
        }

        val existing = this[address]
        if (existing == null) {
            this[address] = override
        } else {
            existing.applyChanges(override)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StateOverride

        return overrides == other.overrides
    }

    override fun hashCode(): Int {
        return overrides.hashCode()
    }

    override fun toString(): String {
        return "StateOverride(overrides=$overrides)"
    }

    companion object {
        /**
         * Wrap the given [overrides] in a new instance, without copying the data.
         * */
        @JvmStatic
        fun wrap(overrides: MutableMap<Address, AccountOverride>): StateOverride {
            return StateOverride(overrides)
        }

        /**
         * Create a new instance from the given [overrides], creating a new copy of each [AccountOverride].
         * */
        @JvmStatic
        fun copy(overrides: Map<Address, AccountOverride>): StateOverride {
            val state = HashMap<Address, AccountOverride>(overrides.size)
            for ((address, override) in overrides) {
                state[address] = AccountOverride(override)
            }
            return StateOverride(state)
        }

        /**
         * Wrap the given [overrides] in a [HashMap], without copying the data.
         * */
        @JvmSynthetic
        operator fun invoke(vararg overrides: Pair<Address, AccountOverride>): StateOverride {
            val state = HashMap<Address, AccountOverride>(overrides.size)
            for ((address, override) in overrides) {
                state[address] = override
            }
            return StateOverride(state)
        }

        @JvmSynthetic
        operator fun invoke(builder: StateOverride.() -> Unit): StateOverride {
            return StateOverride().apply(builder)
        }
    }
}

/**
 * Merge **this** with [other], returning a new instance with the merged changes. The original instances are not
 * modified, and a new copy of each [AccountOverride] is created. This is a convenience method to call
 * [StateOverride.mergeChanges] on/with nullable instances.
 *
 * No reference to [AccountOverride] from [other] is kept so changes to [other] after this call will not affect
 * **this*, and vice-versa.
 * */
fun StateOverride?.mergeChanges(other: StateOverride?): StateOverride? {
    return when {
        this == null && other == null -> null
        this == null && other != null -> StateOverride.copy(other)
        this != null && other == null -> StateOverride.copy(this)
        else -> this!!.mergeChanges(other!!)
    }
}
