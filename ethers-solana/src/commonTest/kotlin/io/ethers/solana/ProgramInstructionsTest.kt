package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.solana.instruction.AssociatedTokenProgram
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.instruction.TokenProgram
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Instruction layouts are consensus, so each case pins the discriminant, the little-endian payload and
 * the account order the program expects, rather than only that an instruction was produced.
 */
class ProgramInstructionsTest : FunSpec({
    fun address(seed: Int) = SolanaAddress(ByteArray(32) { seed.toByte() })
    val payer = address(1)
    val account = address(2)
    val owner = address(3)
    val mint = address(4)

    test("createAccount carries lamports, space and owner after discriminant 0") {
        val instruction = SystemProgram.createAccount(payer, account, bigIntegerOf(2_039_280), 165, Programs.TOKEN)
        instruction.programId shouldBe Programs.SYSTEM
        instruction.keys shouldBe listOf(
            io.ethers.solana.types.AccountMeta.signerAndWritable(payer),
            io.ethers.solana.types.AccountMeta.signerAndWritable(account),
        )
        instruction.data.toHex() shouldBe
            "00000000" + "f01d1f0000000000" + "a500000000000000" + FastHex.encodeWithoutPrefix(Programs.TOKEN.asByteArray())
    }

    test("assign and allocate use their own discriminants") {
        SystemProgram.assign(account, Programs.TOKEN).data.toHex() shouldBe
            "01000000" + FastHex.encodeWithoutPrefix(Programs.TOKEN.asByteArray())
        SystemProgram.allocate(account, 165).data.toHex() shouldBe "08000000" + "a500000000000000"
    }

    test("account space is bounded by what the runtime permits") {
        shouldThrow<IllegalArgumentException> { SystemProgram.allocate(account, -1) }
        shouldThrow<IllegalArgumentException> { SystemProgram.createAccount(payer, account, 1L, SystemProgram.MAX_PERMITTED_DATA_LENGTH + 1, Programs.TOKEN) }
        SystemProgram.allocate(account, SystemProgram.MAX_PERMITTED_DATA_LENGTH).data.size shouldBe 12
    }

    test("nonce instructions name the sysvars positionally, as the program reads them") {
        val initialize = SystemProgram.initializeNonceAccount(account, owner)
        initialize.data.toHex() shouldBe "06000000" + FastHex.encodeWithoutPrefix(owner.asByteArray())
        initialize.keys.map { it.publicKey } shouldBe listOf(account, Programs.SYSVAR_RECENT_BLOCKHASHES, Programs.SYSVAR_RENT)

        // must be the first instruction of a nonce-authorized transaction, and the authority signs it
        val advance = SystemProgram.advanceNonceAccount(account, owner)
        advance.data.toHex() shouldBe "04000000"
        advance.keys.map { it.publicKey } shouldBe listOf(account, Programs.SYSVAR_RECENT_BLOCKHASHES, owner)
        advance.keys.last().signer shouldBe true

        val withdraw = SystemProgram.withdrawNonceAccount(account, owner, payer, bigIntegerOf(1))
        withdraw.data.toHex() shouldBe "05000000" + "0100000000000000"
        withdraw.keys.map { it.publicKey } shouldBe listOf(account, payer, Programs.SYSVAR_RECENT_BLOCKHASHES, Programs.SYSVAR_RENT, owner)
    }

    test("idempotent ATA creation encodes discriminator one and six ordered accounts") {
        val instructions = listOf(
            AssociatedTokenProgram.createAccountIdempotent(payer, account, owner, mint) to Programs.TOKEN,
            AssociatedTokenProgram.createToken2022AccountIdempotent(payer, account, owner, mint) to Programs.TOKEN_2022,
        )
        for ((instruction, tokenProgram) in instructions) {
            instruction.programId shouldBe Programs.ASSOCIATED_TOKEN
            instruction.data.toHex() shouldBe "01"
            instruction.keys shouldBe listOf(
                AccountMeta.signerAndWritable(payer),
                AccountMeta.writable(account),
                AccountMeta(owner),
                AccountMeta(mint),
                AccountMeta(Programs.SYSTEM),
                AccountMeta(tokenProgram),
            )
        }
        AssociatedTokenProgram.createAccount(payer, account, owner, mint).data.toHex() shouldBe "00"
        AssociatedTokenProgram.createToken2022Account(payer, account, owner, mint).data.toHex() shouldBe "00"
    }

    test("idempotent ATA convenience overloads derive addresses for the selected token program") {
        val tokenAddress = SolanaAddress.findAssociatedTokenAddress(owner, mint, Programs.TOKEN).address
        val token2022Address = SolanaAddress.findAssociatedTokenAddress(owner, mint, Programs.TOKEN_2022).address
        (tokenAddress == token2022Address) shouldBe false
        AssociatedTokenProgram.createAccountIdempotent(payer, owner, mint) shouldBe
            AssociatedTokenProgram.createAccountIdempotent(payer, tokenAddress, owner, mint)
        AssociatedTokenProgram.createToken2022AccountIdempotent(payer, owner, mint) shouldBe
            AssociatedTokenProgram.createToken2022AccountIdempotent(payer, token2022Address, owner, mint)
    }

    test("checked token instructions carry amount and decimals after their discriminant") {
        TokenProgram.approveChecked(account, mint, payer, owner, bigIntegerOf(5), 6).data.toHex() shouldBe "0d" + "0500000000000000" + "06"
        TokenProgram.burnChecked(account, mint, owner, bigIntegerOf(5), 6).data.toHex() shouldBe "0f" + "0500000000000000" + "06"
        TokenProgram.mintToChecked(mint, account, owner, bigIntegerOf(5), 6).data.toHex() shouldBe "0e" + "0500000000000000" + "06"
        TokenProgram.revoke(account, owner).data.toHex() shouldBe "05"
        TokenProgram.closeAccount(account, payer, owner).data.toHex() shouldBe "09"
    }

    test("token instructions mark the owner a signer only when no multisig signers are given") {
        TokenProgram.revoke(account, owner).keys.last().signer shouldBe true
        val multisig = TokenProgram.revoke(account, owner, listOf(payer, mint))
        multisig.keys[1].signer shouldBe false
        multisig.keys.drop(2).all { it.signer } shouldBe true
        multisig.keys.map { it.publicKey } shouldBe listOf(account, owner, payer, mint)
    }

    test("token instructions target the program that owns the accounts") {
        TokenProgram.burnChecked(account, mint, owner, bigIntegerOf(1), 0, emptyList(), Programs.TOKEN_2022).programId shouldBe Programs.TOKEN_2022
        TokenProgram.closeAccount(account, payer, owner).programId shouldBe Programs.TOKEN
        shouldThrow<IllegalArgumentException> { TokenProgram.mintToChecked(mint, account, owner, bigIntegerOf(1), 256) }
    }
})
