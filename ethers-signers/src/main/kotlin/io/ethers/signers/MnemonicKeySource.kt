package io.ethers.signers

import io.ethers.crypto.bip32.ExtendedSigningKey
import io.ethers.crypto.bip32.HDPath
import io.ethers.crypto.bip39.MnemonicCode

/**
 * Key source that uses a mnemonic phrase and derivation path to derive [PrivateKeySigner]s.
 * */
class MnemonicKeySource @JvmOverloads constructor(
    mnemonic: MnemonicCode,
    passphrase: String = "",
    val path: HDPath = HDPath.ETHEREUM,
) {
    private val parentKey = ExtendedSigningKey.fromSeed(mnemonic.getSeed(passphrase)).derivePath(path)

    @JvmOverloads
    constructor(mnemonic: String, passphrase: String = "", path: HDPath = HDPath.ETHEREUM) : this(MnemonicCode(mnemonic), passphrase, path)

    @JvmOverloads
    constructor(mnemonic: List<String>, passphrase: String = "", path: HDPath = HDPath.ETHEREUM) : this(MnemonicCode(mnemonic), passphrase, path)

    /**
     * Get a signer for the account at provided [index].
     * */
    fun getAccount(index: Int): PrivateKeySigner {
        return PrivateKeySigner(parentKey.deriveChild(index).signingKey)
    }
}
