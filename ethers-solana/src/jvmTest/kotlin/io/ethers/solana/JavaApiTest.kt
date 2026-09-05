package io.ethers.solana

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.nio.file.Files
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

class JavaApiTest : FunSpec({
    test("Java 11 callers can build, sign and use inherited blocking and future APIs") {
        val source = """
            import io.ethers.solana.providers.SolanaProvider;
            import io.ethers.solana.signers.KeypairSigner;
            import io.ethers.solana.types.PublicKey;
            import io.ethers.solana.types.transaction.TransactionMessage;
            import io.ethers.solana.types.transaction.SolanaTransaction;
            import io.ethers.solana.instruction.TransferInstruction;
            public class SolanaJavaExample {
                public void transfer(SolanaProvider provider, byte[] seed, PublicKey recipient) {
                    var signer = KeypairSigner.fromSeed(seed);
                    var latest = provider.getLatestBlockhash().sendAwait().unwrap().getValue();
                    var message = TransactionMessage.compile(signer.getPublicKey(), latest.getBlockhash(),
                        new TransferInstruction(signer.getPublicKey(), recipient, 1000L));
                    var transaction = new SolanaTransaction(message).sign(signer);
                    var signature = provider.sendTransaction(transaction).sendAwait().unwrap();
                    var subscription = provider.subscribeSignature(signature).sendAsync();
                }
            }
        """.trimIndent()
        val file = object : SimpleJavaFileObject(URI.create("string:///SolanaJavaExample.java"), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val output = Files.createTempDirectory("ethers-solana-java-api").toFile()
        try {
            val compiler = ToolProvider.getSystemJavaCompiler()
            compiler.getStandardFileManager(diagnostics, null, null).use { manager ->
                val result = compiler.getTask(null, manager, diagnostics, listOf("--release", "11", "-proc:none", "-classpath", System.getProperty("java.class.path"), "-d", output.path), null, listOf(file)).call()
                check(result) { diagnostics.diagnostics.joinToString("\n") }
                result shouldBe true
            }
        } finally {
            output.deleteRecursively()
        }
    }
})
