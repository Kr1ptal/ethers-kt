package io.ethers.crypto.bip32

/**
 * Implementation of [BIP-0032](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki) derivation paths.
 * */
class HDPath(private val indices: Array<Int>) {
    /**
     * Returns depth of current derivation path.
     * */
    val depth: Int
        get() = indices.size

    /**
     * Create a copy of this path, adding a new depth with optionally hardened [index].
     * */
    @JvmOverloads
    fun extend(index: Int, hardened: Boolean = false): HDPath {
        return HDPath(indices + if (hardened) index or BIP32_HARDENED_OFFSET else index)
    }

    /**
     * Get index at provided [depth].
     * */
    fun indexAtDepth(depth: Int): Int = indices[depth]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as HDPath

        return indices.contentEquals(other.indices)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + indices.contentHashCode()
        return result
    }

    /**
     * Returns a string representation of this derivation path.
     * */
    override fun toString(): String {
        return indices.joinToString(prefix = "m/", separator = "/") {
            if (isHardened(it)) {
                "${it and (BIP32_HARDENED_OFFSET.inv())}'"
            } else {
                it.toString()
            }
        }
    }

    companion object {
        /**
         * BIP32 hardened key offset.
         * */
        private const val BIP32_HARDENED_OFFSET = 0x8000_0000.toInt()

        /**
         * Default derivation path for Ethereum: m/44'/60'/0'/0.
         *
         * Based on [BIP-0044](https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki) format.
         * */
        val ETHEREUM = parse("m/44'/60'/0'/0")

        /**
         * Parse derivation path from string.
         * */
        fun parse(path: String): HDPath {
            if (path.isEmpty() || path[0] != 'm') {
                throw IllegalArgumentException("Invalid derivation path: $path")
            }

            if (path == "m") {
                return HDPath(emptyArray())
            }

            val indices = path.removePrefix("m").removePrefix("/").split("/").map {
                if (it.endsWith("'")) {
                    it.dropLast(1).toInt() or BIP32_HARDENED_OFFSET
                } else {
                    it.toInt()
                }
            }

            return HDPath(indices.toTypedArray())
        }

        fun isHardened(index: Int): Boolean {
            return index and BIP32_HARDENED_OFFSET != 0
        }
    }
}
