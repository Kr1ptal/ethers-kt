package io.ethers.crypto.bip39

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import java.math.BigInteger

/**
 * English mnemonic word list from [BIP-0039](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki).
 * */
data object MnemonicWordListEnglish : MnemonicWordList {
    private const val BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db"

    override val separator: Char = ' '
    override val words: List<String>

    init {
        val stream = MnemonicWordListEnglish::class.java.getResourceAsStream("/bip39/wordlist_english.txt")
            ?: throw RuntimeException("English wordlist not found")

        this.words = stream.bufferedReader().readLines()

        val hashFunction = CryptographyProvider.Default.get(SHA256).hasher().createHashFunction()
        words.forEach { hashFunction.update(it.toByteArray()) }

        val digest = hashFunction.hashToByteArray()
        val digestHex = BigInteger(1, digest).toString(16)
        if (digestHex != BIP39_ENGLISH_SHA256) {
            throw RuntimeException("Invalid SHA256 digest for english wordlist. Expected: '$BIP39_ENGLISH_SHA256', got: '$digestHex'")
        }
    }
}
