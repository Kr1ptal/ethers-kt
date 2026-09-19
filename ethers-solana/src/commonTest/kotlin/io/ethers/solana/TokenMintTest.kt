package io.ethers.solana

import io.ethers.solana.instruction.TokenAccount
import io.ethers.solana.instruction.TokenMint
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.io.encoding.Base64

class TokenMintTest : FunSpec({
    // EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v (USDC) on mainnet, owned by the Token program.
    // Both authorities are set; every expectation below is that of these exact bytes.
    val usdc = Base64.decode(
        "AQAAAJj+huiNm+Lqi8HMpIeLKYjCQPUrhCS/tA7Rot3LXhmbfZBJYmyEHAAGAQEAAABicKqKWcWUBbRShshncubNEm6bil06OFNtN/e0FOi2Zw==",
    )

    // B5WTLaRwaUQpKk7ir1wniNB6m5o8GgMrimhKMYan2R6B, the mint of the account in TokenAccountTest.
    // Its supply is fixed and it cannot freeze, so both options are empty.
    val noAuthorities = Base64.decode(
        "AAAAAAbFwc5jjSVn0mRosF65UdGijcxuEjSCtcZ1FJdw5ivySFwT26yMAwAGAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    )

    test("decodes a live mainnet mint with both authorities set") {
        usdc.size shouldBe TokenMint.SIZE
        val mint = TokenMint.decode(usdc).unwrap()
        mint.mintAuthority shouldBe SolanaAddress("BJE5MMbqXjVwjAF7oxwPYXnTXDyspzZyt4vwenNw5ruG")
        mint.freezeAuthority shouldBe SolanaAddress("7dGbd2QZcCKcTndnHcTL8q7SMVXAkp688NTQYwrRCrar")
        mint.supply shouldBe bigIntegerOf(8026900388221053)
        mint.decimals shouldBe 6
        mint.isInitialized shouldBe true
    }

    test("an empty option is null, and the fields after it are still read in place") {
        val mint = TokenMint.decode(noAuthorities).unwrap()
        mint.mintAuthority shouldBe null
        mint.freezeAuthority shouldBe null
        // the supply and decimals sit between the two options, so reading them proves the offsets hold
        mint.supply shouldBe bigIntegerOf(999098967874632)
        mint.decimals shouldBe 6
        mint.isInitialized shouldBe true
    }

    test("decimals turn a token account amount into the figure a wallet shows") {
        val account = TokenAccount.decode(TokenAccountTest.LIVE_ACCOUNT).unwrap()
        val mint = TokenMint.decode(noAuthorities).unwrap()
        account.mint shouldBe SolanaAddress("B5WTLaRwaUQpKk7ir1wniNB6m5o8GgMrimhKMYan2R6B")
        account.amount shouldBe bigIntegerOf(117530047828352)
        mint.decimals shouldBe 6
        // 117530047828352 base units at 6 decimals
        (account.amount.toString().dropLast(mint.decimals) + "." + account.amount.toString().takeLast(mint.decimals)) shouldBe "117530047.828352"
    }

    test("a Token-2022 mint padded past the account length decodes, and an account does not") {
        // real Token-2022 mints are padded to the account length and marked at that offset
        val extended = usdc + ByteArray(TokenAccount.SIZE - TokenMint.SIZE) + byteArrayOf(1) + ByteArray(24) { 3 }
        TokenMint.decode(extended).unwrap().decimals shouldBe 6
        val asAccount = usdc + ByteArray(TokenAccount.SIZE - TokenMint.SIZE) + byteArrayOf(2) + ByteArray(24)
        TokenMint.decode(asAccount).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
        // a 165-byte token account is neither the base length nor marked as a mint
        TokenMint.decode(TokenAccountTest.LIVE_ACCOUNT).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
    }

    test("a buffer that is neither length is reported rather than read") {
        TokenMint.decode(usdc.copyOf(81)).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
        TokenMint.decode(usdc + byteArrayOf(0)).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
        TokenMint.decode(ByteArray(0)).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
    }
})
