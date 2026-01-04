package io.ethers.crypto.bip39

/**
 * English mnemonic word list from [BIP-0039](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki).
 * */
data object MnemonicWordListEnglish : MnemonicWordList {
    override val separator: Char = ' '
    override val words: List<String> = Bip39WordlistData.WORDS.asList()
}
