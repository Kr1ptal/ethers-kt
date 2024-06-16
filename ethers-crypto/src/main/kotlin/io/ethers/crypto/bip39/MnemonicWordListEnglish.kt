package io.ethers.crypto.bip39

import org.bouncycastle.jcajce.provider.digest.SHA256
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

        val sha256 = SHA256.Digest()
        words.forEach { sha256.update(it.toByteArray()) }

        val digest = sha256.digest()
        val digestHex = BigInteger(1, digest).toString(16)
        if (digestHex != BIP39_ENGLISH_SHA256) {
            throw RuntimeException("Invalid SHA256 digest for english wordlist. Expected: '$BIP39_ENGLISH_SHA256', got: '$digestHex'")
        }
    }
}
