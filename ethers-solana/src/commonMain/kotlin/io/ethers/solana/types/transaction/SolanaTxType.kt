package io.ethers.solana.types.transaction

import kotlin.jvm.JvmStatic

/** Transaction versions readable from RPC, including versions this library cannot construct or sign. */
sealed class SolanaTxType {
    /** Numeric message version, or null for the unversioned legacy format. */
    abstract val version: Int?
    val isSupported: Boolean get() = this !is Unsupported

    data object Legacy : SolanaTxType() {
        override val version: Int? get() = null
    }

    data object V0 : SolanaTxType() {
        override val version: Int get() = 0
    }

    data class Unsupported(override val version: Int) : SolanaTxType() {
        init {
            require(version > 0) { "Unsupported versions must be positive; version 0 is supported" }
        }
    }

    companion object {
        @JvmStatic
        fun fromVersion(version: Int): SolanaTxType = if (version == 0) V0 else Unsupported(version)
    }
}
