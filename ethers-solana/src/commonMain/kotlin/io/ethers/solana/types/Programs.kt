package io.ethers.solana.types

import kotlin.jvm.JvmField

object Programs {
    @JvmField val SYSTEM = PublicKey("11111111111111111111111111111111")
    @JvmField val TOKEN = PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    @JvmField val TOKEN_2022 = PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
    @JvmField val ASSOCIATED_TOKEN = PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
    @JvmField val COMPUTE_BUDGET = PublicKey("ComputeBudget111111111111111111111111111111")
    @JvmField val SYSVAR_RENT = PublicKey("SysvarRent111111111111111111111111111111111")
}
