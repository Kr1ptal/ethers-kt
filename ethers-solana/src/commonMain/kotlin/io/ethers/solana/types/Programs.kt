package io.ethers.solana.types

import kotlin.jvm.JvmField

object Programs {
    @JvmField val MEMO = SolanaAddress("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")
    @JvmField val ED25519 = SolanaAddress("Ed25519SigVerify111111111111111111111111111")
    @JvmField val SECP256K1 = SolanaAddress("KeccakSecp256k11111111111111111111111111111")
    @JvmField val SYSTEM = SolanaAddress("11111111111111111111111111111111")
    @JvmField val TOKEN = SolanaAddress("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    @JvmField val TOKEN_2022 = SolanaAddress("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
    @JvmField val ASSOCIATED_TOKEN = SolanaAddress("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
    @JvmField val ADDRESS_LOOKUP_TABLE = SolanaAddress("AddressLookupTab1e1111111111111111111111111")
    @JvmField val COMPUTE_BUDGET = SolanaAddress("ComputeBudget111111111111111111111111111111")
    @JvmField val SYSVAR_RENT = SolanaAddress("SysvarRent111111111111111111111111111111111")

    /** Deprecated on chain but still required by the nonce instructions, which name it positionally. */
    @JvmField val SYSVAR_RECENT_BLOCKHASHES = SolanaAddress("SysvarRecentB1ockHashes11111111111111111111")
}
