package io.ethers.solana.idl.test

import io.ethers.core.FastHex
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.SolanaAddress
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

/**
 * The lenient IDL's `Pool` is a zero-copy struct with an enum field, whose width the IDL does not record, so it is left
 * out, with everything using it: the `Pool` account, the `PoolUpdated` event, the `DEFAULT_POOL` constant, and the
 * `swap` and `migrate` instructions. The `LOCKUP` constant is an expression, so is left out too. What is left compiles
 * for every target and keeps its exact wire format.
 */
class LenientBindingsTest : FunSpec({
    val admin = SolanaAddress(ByteArray(32) { 1 })
    val recipient = SolanaAddress(ByteArray(32) { 2 })
    val config = Lenient.Config(admin, Lenient.Fee(30, recipient))

    test("only the declarations that can be generated are") {
        Lenient.ACCOUNTS shouldBe listOf(Lenient.Config.ACCOUNT)
        Lenient.EVENTS.size shouldBe 1
        Lenient.INSTRUCTIONS shouldBe listOf(Lenient.INITIALIZE, Lenient.SET_POOL)
        Lenient.MAX_FEE_BPS shouldBe 1000
    }

    test("the generated account keeps its wire format") {
        val data = hex("0a00000000000000") + ByteArray(32) { 1 } + hex("1e00") + ByteArray(32) { 2 }
        Lenient.Config.ACCOUNT.encode(config) shouldBe data
        Lenient.decodeAccount(data).unwrap() shouldBe config
        Lenient.Config.Fields.fee.bps.offset shouldBe 32
    }

    test("data of a declaration that is not generated is unrecognized") {
        Lenient.decodeAccount(hex("0b00000000000000") + ByteArray(33)).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
        Lenient.decodeInstruction(Instruction(Lenient.PROGRAM_ID, emptyList(), hex("0300000000000000"))).unwrapError()
            .shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
    }

    test("a generated instruction that only names the account of a type left out still works") {
        val pool = SolanaAddress(ByteArray(32) { 3 })
        val instruction = Lenient.setPool(Lenient.SetPoolAccounts(pool), Lenient.SetPoolArgs(Lenient.Status.Paused))
        instruction.data.toByteArray() shouldBe hex("0200000000000000 01")
        Lenient.decodeInstruction(instruction).unwrap().shouldBeInstanceOf<Lenient.Instruction.SetPool>()
    }
})
