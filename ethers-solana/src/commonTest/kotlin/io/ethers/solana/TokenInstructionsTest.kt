package io.ethers.solana

import io.ethers.solana.instruction.Token2022Program
import io.ethers.solana.instruction.TokenAuthorityType
import io.ethers.solana.instruction.TokenProgram
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TokenInstructionsTest : FunSpec({
    val account = SolanaAddress(ByteArray(32) { 1 })
    val mint = SolanaAddress(ByteArray(32) { 2 })
    val authority = SolanaAddress(ByteArray(32) { 3 })
    val other = SolanaAddress(ByteArray(32) { 4 })

    test("mint initialization uses compact instruction options rather than account COption layout") {
        val legacy = TokenProgram.initializeMint(mint, 6, authority)
        legacy.programId shouldBe Programs.TOKEN
        legacy.data.toHex() shouldBe "0006" + "03".repeat(32) + "00"
        legacy.keys shouldBe listOf(AccountMeta.writable(mint), AccountMeta(Programs.SYSVAR_RENT))
        val modern = TokenProgram.initializeMint2(mint, 255, authority, other)
        modern.data.toHex() shouldBe "14ff" + "03".repeat(32) + "01" + "04".repeat(32)
        modern.keys shouldBe listOf(AccountMeta.writable(mint))
        TokenProgram.initializeMint(mint, 0, authority, other).data.toHex() shouldBe "0000" + "03".repeat(32) + "01" + "04".repeat(32)
        TokenProgram.initializeMint2(mint, 0, authority).data.toHex() shouldBe "1400" + "03".repeat(32) + "00"
        for (decimals in listOf(-1, 256)) {
            shouldThrow<IllegalArgumentException> { TokenProgram.initializeMint(mint, decimals, authority) }
            shouldThrow<IllegalArgumentException> { Token2022Program.initializeMint2(mint, decimals, authority) }
        }
    }

    test("account initialization places owner in accounts or data according to the variant") {
        val legacy = TokenProgram.initializeAccount(account, mint, authority)
        legacy.data.toHex() shouldBe "01"
        legacy.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta(mint), AccountMeta(authority), AccountMeta(Programs.SYSVAR_RENT))
        val modern = TokenProgram.initializeAccount3(account, mint, authority)
        modern.data.toHex() shouldBe "12" + "03".repeat(32)
        modern.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta(mint))
    }

    test("multisig initialization encodes its threshold and does not require members to sign") {
        val members = listOf(authority, other)
        val legacy = TokenProgram.initializeMultisig(account, 2, members)
        legacy.data.toHex() shouldBe "0202"
        legacy.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta(Programs.SYSVAR_RENT), AccountMeta(authority), AccountMeta(other))
        val modern = TokenProgram.initializeMultisig2(account, 1, members)
        modern.data.toHex() shouldBe "1301"
        modern.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta(authority), AccountMeta(other))
        for ((threshold, count) in listOf(0 to 2, 3 to 2, 1 to 0, 1 to 12)) {
            shouldThrow<IllegalArgumentException> { TokenProgram.initializeMultisig(account, threshold, List(count) { authority }) }
            shouldThrow<IllegalArgumentException> { Token2022Program.initializeMultisig2(account, threshold, List(count) { authority }) }
        }
        TokenProgram.initializeMultisig2(account, 11, List(11) { SolanaAddress(ByteArray(32) { index -> (index + it).toByte() }) }).data.toHex() shouldBe "130b"
    }

    test("all base authority kinds encode nullable new authority and multisig flags") {
        val kinds = listOf(TokenAuthorityType.MINT_TOKENS, TokenAuthorityType.FREEZE_ACCOUNT, TokenAuthorityType.ACCOUNT_OWNER, TokenAuthorityType.CLOSE_ACCOUNT)
        kinds.forEachIndexed { index, kind ->
            val single = TokenProgram.setAuthority(account, authority, kind, other)
            single.data.toHex() shouldBe "060${index}01" + "04".repeat(32)
            single.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta.signer(authority))
            val multi = TokenProgram.setAuthority(account, authority, kind, null, listOf(mint, other))
            multi.data.toHex() shouldBe "060${index}00"
            multi.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta(authority), AccountMeta.signer(mint), AccountMeta.signer(other))
        }
    }

    test("freeze thaw and sync-native have the exact tags and writable accounts") {
        val freeze = TokenProgram.freezeAccount(account, mint, authority)
        freeze.data.toHex() shouldBe "0a"
        freeze.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta(mint), AccountMeta.signer(authority))
        val thaw = TokenProgram.thawAccount(account, mint, authority, listOf(other))
        thaw.data.toHex() shouldBe "0b"
        thaw.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta(mint), AccountMeta(authority), AccountMeta.signer(other))
        TokenProgram.syncNative(account).data.toHex() shouldBe "11"
        TokenProgram.syncNative(account).keys shouldBe listOf(AccountMeta.writable(account))
    }

    test("Token-2022 wrappers select their program and preserve base instruction layouts") {
        val pairs = listOf(
            TokenProgram.initializeMint(mint, 6, authority, other) to Token2022Program.initializeMint(mint, 6, authority, other),
            TokenProgram.initializeMint2(mint, 6, authority) to Token2022Program.initializeMint2(mint, 6, authority),
            TokenProgram.initializeAccount(account, mint, authority) to Token2022Program.initializeAccount(account, mint, authority),
            TokenProgram.initializeAccount3(account, mint, authority) to Token2022Program.initializeAccount3(account, mint, authority),
            TokenProgram.initializeMultisig(account, 1, listOf(authority)) to Token2022Program.initializeMultisig(account, 1, listOf(authority)),
            TokenProgram.initializeMultisig2(account, 1, listOf(authority)) to Token2022Program.initializeMultisig2(account, 1, listOf(authority)),
            TokenProgram.setAuthority(account, authority, TokenAuthorityType.CLOSE_ACCOUNT, null) to Token2022Program.setAuthority(account, authority, TokenAuthorityType.CLOSE_ACCOUNT, null),
            TokenProgram.syncNative(account) to Token2022Program.syncNative(account),
            TokenProgram.freezeAccount(account, mint, authority) to Token2022Program.freezeAccount(account, mint, authority),
            TokenProgram.thawAccount(account, mint, authority) to Token2022Program.thawAccount(account, mint, authority),
            TokenProgram.approveChecked(account, mint, other, authority, bigIntegerOf(7), 6) to Token2022Program.approveChecked(account, mint, other, authority, bigIntegerOf(7), 6),
            TokenProgram.revoke(account, authority) to Token2022Program.revoke(account, authority),
            TokenProgram.burnChecked(account, mint, authority, bigIntegerOf(7), 6) to Token2022Program.burnChecked(account, mint, authority, bigIntegerOf(7), 6),
            TokenProgram.mintToChecked(mint, account, authority, bigIntegerOf(7), 6) to Token2022Program.mintToChecked(mint, account, authority, bigIntegerOf(7), 6),
            TokenProgram.closeAccount(account, other, authority) to Token2022Program.closeAccount(account, other, authority),
        )
        for ((legacy, modern) in pairs) {
            legacy.programId shouldBe Programs.TOKEN
            modern.programId shouldBe Programs.TOKEN_2022
            modern.keys shouldBe legacy.keys
            modern.data shouldBe legacy.data
        }
    }
})
