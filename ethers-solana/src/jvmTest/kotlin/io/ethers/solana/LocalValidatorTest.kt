package io.ethers.solana

import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.SignatureNotification
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
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
    test("v1 validator accepts an envelope over 1232 bytes and returns inline config").config(enabled = http != null && System.getenv("SOLANA_VALIDATOR_V1") == "true") {
        require(URI(http!!).host in setOf("localhost", "127.0.0.1", "::1"))
        val provider = SolanaProvider.builder(http).defaultCommitment(Commitment.CONFIRMED).build().unwrap()
        try {
            val sender = KeypairSigner.generate()
            val recipient = KeypairSigner.generate().publicKey
            provider.requestAirdrop(sender.publicKey, 1000000000L).send().unwrap()
            eventually(30.seconds) { provider.getBalance(sender.publicKey).send().unwrap().value shouldBe bigIntegerOf(1000000000L) }
            val latest = provider.getLatestBlockhash().send().unwrap().value
            val config = SolanaTransactionConfig(bigIntegerOf(5000), 200000, 65536)
            val tx = SolanaTxV1.compile(sender.publicKey, latest.blockhash, List(64) { SystemProgram.transfer(sender.publicKey, recipient, 20000L) }, config).unwrap().sign(sender)
            (tx.serialize().size > 1232) shouldBe true
            provider.getFeeForMessage(tx).send().unwrap().value shouldBe bigIntegerOf(10000)
            provider.simulateTransaction(tx).send().unwrap().value.isSuccess shouldBe true
            val signature = provider.sendTransaction(tx).send().unwrap()
            eventually(30.seconds) {
                val rpc = provider.getTransaction(signature).send().unwrap()!!
                rpc.type shouldBe SolanaTxType.V1
                rpc.transaction.message.transactionConfig shouldBe config
                rpc.meta!!.isSuccess shouldBe true
                provider.getBalance(recipient).send().unwrap().value shouldBe bigIntegerOf(1280000)
            }
        } finally {
            provider.close()
        }
    }
    test("local validator accepts a signed v0 transfer and emits signature status").config(enabled = http != null && ws != null) {
        require(URI(http!!).host in setOf("localhost", "127.0.0.1", "::1"))
        require(URI(ws!!).host in setOf("localhost", "127.0.0.1", "::1"))
        val provider = SolanaProvider.builder(http).webSocketUrl(ws).defaultCommitment(Commitment.CONFIRMED).build().unwrap()
        try {
            val sender = KeypairSigner.generate()
            val recipient = KeypairSigner.generate().publicKey
            provider.requestAirdrop(sender.publicKey, 1000000000L).send().unwrap()
            eventually(30.seconds) { provider.getBalance(sender.publicKey).send().unwrap().value shouldBe bigIntegerOf(1000000000L) }
            val latest = provider.getLatestBlockhash().send().unwrap().value
            val message = SolanaTxV0.compile(sender.publicKey, latest.blockhash, SystemProgram.transfer(sender.publicKey, recipient, 1000000L)).unwrap()
            val transaction = message.sign(sender)
            provider.simulateTransaction(transaction).send().unwrap().value.isSuccess shouldBe true
            val signature = provider.sendTransaction(transaction).send().unwrap()
            val stream = provider.subscribeSignature(signature).send().unwrap()
            try {
                eventually(30.seconds) { stream.isEmpty shouldBe false }
                (stream.take()!!.value as SignatureNotification.Status).err shouldBe null
                eventually(5.seconds) { stream.isClosed shouldBe true }
                provider.getBalance(recipient).send().unwrap().value shouldBe bigIntegerOf(1000000)
            } finally {
                stream.close()
            }
        } finally {
            provider.close()
        }
    }
})
