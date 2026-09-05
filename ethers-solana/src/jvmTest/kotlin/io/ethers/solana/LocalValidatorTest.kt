package io.ethers.solana

import io.ethers.solana.instruction.TransferInstruction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.SignatureNotification
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.TransactionMessage
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import kotlin.time.Duration.Companion.seconds

/** Opt in with SOLANA_VALIDATOR_HTTP and SOLANA_VALIDATOR_WS, both loopback endpoints. */
class LocalValidatorTest : FunSpec({
    val http = System.getenv("SOLANA_VALIDATOR_HTTP")
    val ws = System.getenv("SOLANA_VALIDATOR_WS")
    test("local validator accepts a signed v0 transfer and emits signature status").config(enabled = http != null && ws != null) {
        require(URI(http!!).host in setOf("localhost", "127.0.0.1", "::1"))
        require(URI(ws!!).host in setOf("localhost", "127.0.0.1", "::1"))
        val provider = SolanaProvider.builder(http).webSocketUrl(ws).commitment(Commitment.CONFIRMED).build().unwrap()
        try {
            val sender = KeypairSigner.generate()
            val recipient = KeypairSigner.generate().publicKey
            provider.requestAirdrop(sender.publicKey, 1000000000L).send().unwrap()
            eventually(30.seconds) { provider.getBalance(sender.publicKey).send().unwrap() shouldBe bigIntegerOf(1000000000L) }
            val latest = provider.getLatestBlockhash().send().unwrap().value
            val message = TransactionMessage.compile(sender.publicKey, latest.blockhash, TransferInstruction(sender.publicKey, recipient, 1000000L))
            val transaction = SolanaTransaction(message).sign(sender)
            provider.simulateTransaction(transaction).send().unwrap().value.isSuccess shouldBe true
            val signature = provider.sendTransaction(transaction).send().unwrap()
            val stream = provider.subscribeSignature(signature).send().unwrap()
            try {
                eventually(30.seconds) { stream.isEmpty shouldBe false }
                (stream.take() as SignatureNotification.Status).err shouldBe null
                eventually(5.seconds) { stream.isClosed shouldBe true }
                provider.getBalance(recipient).send().unwrap() shouldBe bigIntegerOf(1000000)
            } finally {
                stream.close()
            }
        } finally {
            provider.close()
        }
    }
})
