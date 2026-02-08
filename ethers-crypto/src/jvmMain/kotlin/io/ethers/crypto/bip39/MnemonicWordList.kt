package io.ethers.crypto.bip39

/**
 * Mnemonic word list.
 * */
interface MnemonicWordList {
    val separator: Char
    val words: List<String>

    operator fun get(index: Int) = words[index]

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}
