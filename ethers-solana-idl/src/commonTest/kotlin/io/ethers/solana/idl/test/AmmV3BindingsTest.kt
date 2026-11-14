package io.ethers.solana.idl.test

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

private val POOL = SolanaAddress("114Yw2VLpsDKVp1py7EwUmrfQqdc41Adi4AueP2bZ86")

/** The account data of [POOL], a zero-copy PoolState, from mainnet at slot 453316338. */
private val POOL_DATA = Base64.decode(
    "9+3j9dfD3kb/gW5mYww7tyTcWeSfbMQwbmA6aqzKBvo+NOK0CtWXnY0xlcphJdbMsUYwJ6JNq2mS+pjlWlMWTxu/UCEo6mAp0Wr/" +
        "Ih48s7+pZFaD0Bd+v/bCrOjStcYbE5VEbXe6Zt/Exvp6877brTo9ZfNqq8l0MbG75MLS9uDkfKYCA0UvXWGPP4WcDUoekuSP019B" +
        "IKdbyJK8AHIHPltwrp43rv4BrKDbDIk6xSO6DeVcAFAHEuhAU/Yi073Q86/VjKZ3XTqNgJ5kzf8RFyD5A599w2y7tr1gbmtMlb8w" +
        "H0KjOxUSEjsGBgEAfviB7aR0ZAcAAAAAAAAAACZVA89u9dAGAAAAAAAAAAC35P7/AAAAAMyzVJcLAAAAAAAAAAAAAABaihoCAAAA" +
        "AAAAAAAAAAAARqw9DAAAAAC5OAIAAAAAAHbBv6uQDwAAAAAAAAAAAADFMBrTAgAAAAAAAAAAAAAAL7Qs0wIAAAAAAAAAAAAAANjQ" +
        "vUWQDwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAxlcphJdbM" +
        "sUYwJ6JNq2mS+pjlWlMWTxu/UCEo6mAp0QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAMZXKYSXWzLFGMCeiTatpkvqY5VpTFk8bv1AhKOpgKdEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADGVymEl1syxRjAnok2raZL6mOVaUxZPG79QISjqYCnRAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAC7avVQAAAABdrJJOAAAAAByN" +
        "DwAAAAAAnEIOAAAAAADAjhQEAAAAAJK9AAAAAAAAAAAAAAAAAAB5AwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
)

class AmmV3BindingsTest : FunSpec({
    test("a packed zero-copy account decodes and re-encodes byte for byte") {
        val pool = AmmV3.PoolState.ACCOUNT.decode(POOL_DATA).unwrap()
        pool.bump shouldBe SolanaBytes.fromHex("ff")
        pool.ammConfig shouldBe SolanaAddress("9iFER3bpjf1PTTCQCfTRu17EJgvsxo9pVyA9QWwEuX4x")
        pool.tokenMint0 shouldBe SolanaAddress("8CfriN5SgC8rFKMUrjdSa8J9xqLCLWUiKW1Uri9iZJPM")
        pool.tokenMint1 shouldBe SolanaAddress("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
        pool.mintDecimals0 shouldBe 6
        pool.tickSpacing shouldBe 1
        pool.liquidity shouldBe BigInteger("532678907644737662")
        pool.sqrtPriceX64 shouldBe BigInteger("491162215651693862")
        pool.tickCurrent shouldBe -72521
        AmmV3.PoolState.ACCOUNT.encode(pool) shouldBe POOL_DATA
    }

    test("the pool's own addresses derive from its seeds") {
        val pool = AmmV3.PoolState.ACCOUNT.decode(POOL_DATA).unwrap()
        AmmV3.CreatePoolAccounts.findPoolState(pool.ammConfig, pool.tokenMint0, pool.tokenMint1).address shouldBe POOL
        AmmV3.CreatePoolAccounts.findTokenVault0(POOL, pool.tokenMint0).address shouldBe pool.tokenVault0
        AmmV3.CreatePoolAccounts.findTokenVault1(POOL, pool.tokenMint1).address shouldBe pool.tokenVault1
        AmmV3.CreatePoolAccounts.findObservationState(POOL).address shouldBe pool.observationKey
    }

    // addresses of mainnet accounts, which Raydium derives from big-endian integers
    test("integer seeds configured as big-endian derive mainnet addresses") {
        AmmV3.CreateAmmConfigAccounts.findAmmConfig(4).address shouldBe SolanaAddress("9iFER3bpjf1PTTCQCfTRu17EJgvsxo9pVyA9QWwEuX4x")
        AmmV3.OpenPositionAccounts.findTickArrayLower(POOL, -72540).address shouldBe SolanaAddress("HufTnbzT4JkH1YtJeF8eBPoEZ1LmGYNSYKyqzSNQCEx6")

        val positionPool = SolanaAddress("3BVNZ9vzUZ9ohoXbvwZXbmkv76vvbZSBSQjNK38YAFJp")
        val protocolPosition = SolanaAddress("114J3ZH4prYDvg325DTfsFUqgr1gfArcK5Q1nqx7oV4")
        AmmV3.OpenPositionAccounts.findProtocolPosition(positionPool, 75600, 86640).address shouldBe protocolPosition
        // the same account, derived from a personal position's data fields
        AmmV3.DecreaseLiquidityAccounts.findProtocolPosition(positionPool, 75600, 86640).address shouldBe protocolPosition
    }
})
