package io.ethers.solana

import io.ethers.solana.instruction.TokenAccount
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.io.encoding.Base64

class TokenAccountTest : FunSpec({
    // 3DjZ9MqvMJihtkKzVkQuFdupeG1wSdB29pWxsRuGLPV1 on mainnet, owned by the Token program.
    // Field values below are the node's own jsonParsed reading of these same bytes.
    val live = Base64.decode(
        "lbx/EOQNYs9/ROmd4dD1vVJSbOLrRZayBcTY+mqnSeRBV7BYDzHF/ORKYlgtvPnXjudZQ6CEo5OzUDaNIomTCIBRwJjk" +
            "agAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    )

    test("decodes a live mainnet token account exactly as the node parses it") {
        live.size shouldBe TokenAccount.SIZE
        val account = TokenAccount.decode(live).unwrap()
        account.mint shouldBe SolanaAddress("B5WTLaRwaUQpKk7ir1wniNB6m5o8GgMrimhKMYan2R6B")
        account.owner shouldBe SolanaAddress("5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1")
        account.amount shouldBe bigIntegerOf(117530047828352)
        account.state shouldBe TokenAccount.State.INITIALIZED
        account.delegate shouldBe null
        account.isNative shouldBe null
        account.delegatedAmount shouldBe bigIntegerOf(0)
        account.closeAuthority shouldBe null
    }

    test("populated options are read, and their bytes are skipped when absent") {
        val delegate = SolanaAddress(ByteArray(32) { 7 })
        val closeAuthority = SolanaAddress(ByteArray(32) { 9 })
        val bytes = live.copyOf()
        bytes[72] = 1 // delegate tag
        delegate.toByteArray().copyInto(bytes, 76)
        bytes[109] = 1 // isNative tag: a wrapped-SOL account states its rent-exempt reserve
        bytes[113] = 5
        bytes[121] = 3 // delegatedAmount
        bytes[129] = 1 // closeAuthority tag
        closeAuthority.toByteArray().copyInto(bytes, 133)

        val account = TokenAccount.decode(bytes).unwrap()
        account.delegate shouldBe delegate
        account.isNative shouldBe bigIntegerOf(5)
        account.delegatedAmount shouldBe bigIntegerOf(3)
        account.closeAuthority shouldBe closeAuthority
        // the fields before and after the options are still read from the right offsets
        account.mint shouldBe SolanaAddress("B5WTLaRwaUQpKk7ir1wniNB6m5o8GgMrimhKMYan2R6B")
        account.amount shouldBe bigIntegerOf(117530047828352)
    }

    test("frozen and uninitialized states round-trip, and an undefined one is reported") {
        TokenAccount.decode(live.copyOf().also { it[108] = 0 }).unwrap().state shouldBe TokenAccount.State.UNINITIALIZED
        TokenAccount.decode(live.copyOf().also { it[108] = 2 }).unwrap().state shouldBe TokenAccount.State.FROZEN
        TokenAccount.decode(live.copyOf().also { it[108] = 3 }).unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
    }

    test("Token-2022 accounts decode past the base layout, but its mints do not") {
        // Token-2022 pads a mint to the account length, so only the type byte separates them
        val extended = live + byteArrayOf(2) + ByteArray(40) { 1 }
        TokenAccount.decode(extended).unwrap().amount shouldBe bigIntegerOf(117530047828352)
        val mint = live + byteArrayOf(1) + ByteArray(40)
        TokenAccount.decode(mint).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
    }

    test("a buffer shorter than the base layout is reported rather than read") {
        TokenAccount.decode(live.copyOf(164)).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
        TokenAccount.decode(ByteArray(0)).unwrapError().shouldBeInstanceOf<SolanaTransactionError.MalformedBytes>()
    }
})
