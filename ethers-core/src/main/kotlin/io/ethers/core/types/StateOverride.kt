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
     * No reference to [AccountOverride] from [other] is kept so changes to [other] after this call will not affect
     * **this*, and vice-versa.
     * */
    fun mergeChanges(other: Map<Address, AccountOverride>): StateOverride {
        val merged = copy(overrides)

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
     * No reference to [AccountOverride] from [other] is kept so changes to [other] after this call will not affect
     * **this*, and vice-versa.
     * */
    fun applyChanges(other: Map<Address, AccountOverride>) {
        for ((address, override) in other) {
            val existing = overrides[address]
            if (existing == null) {
                overrides[address] = AccountOverride(override)
            } else {
                existing.applyChanges(override)
            }
        }
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
    }
}
