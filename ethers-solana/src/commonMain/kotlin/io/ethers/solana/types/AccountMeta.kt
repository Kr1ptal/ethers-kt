package io.ethers.solana.types

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

data class AccountMeta @JvmOverloads constructor(val publicKey: PublicKey, val signer: Boolean = false, val writable: Boolean = false) {
    companion object {
        @JvmStatic fun signer(publicKey: PublicKey): AccountMeta = AccountMeta(publicKey, signer = true)
        @JvmStatic fun writable(publicKey: PublicKey): AccountMeta = AccountMeta(publicKey, writable = true)
        @JvmStatic fun signerAndWritable(publicKey: PublicKey): AccountMeta = AccountMeta(publicKey, signer = true, writable = true)
    }
}
