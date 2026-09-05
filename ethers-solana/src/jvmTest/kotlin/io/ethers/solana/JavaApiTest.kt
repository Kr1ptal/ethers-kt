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
            import io.ethers.solana.types.SolanaAddress;
            import io.ethers.solana.types.SolanaBytes;
            import io.ethers.solana.types.ContextValue;
            import io.ethers.solana.types.RpcContext;
            import io.ethers.solana.types.SolanaNodeIdentity;
            import io.ethers.solana.types.transaction.SolanaTxV0;
            import io.ethers.solana.types.transaction.SolanaTxLegacy;
            import io.ethers.solana.types.transaction.SolanaTransaction;
            import io.ethers.solana.types.transaction.SolanaTransactionUnsigned;
            import io.ethers.solana.types.transaction.SolanaTransactionSigned;
            import io.ethers.solana.instruction.TransferInstruction;
            import io.ethers.solana.utils.SolUnit;
            public class SolanaJavaExample {
                public void transfer(SolanaProvider provider, byte[] seed, SolanaAddress recipient) {
                    var builder = SolanaProvider.builder("https://example.invalid")
                        .defaultCommitment(io.ethers.solana.types.Commitment.CONFIRMED);
                    var defaultCommitment = provider.getDefaultCommitment();
                    ContextValue<java.math.BigInteger> balance = provider.getBalance(recipient).sendAwait().unwrap();
                    ContextValue<java.math.BigInteger> overridden = provider.getBalance(recipient, defaultCommitment).sendAwait().unwrap();
                    RpcContext context = balance.getContext();
                    java.math.BigInteger lamports = balance.getValue();
                    SolanaNodeIdentity identity = provider.getIdentity().sendAwait().unwrap();
                    SolanaAddress nodeAddress = identity.getIdentity();
                    var bytes = SolanaBytes.fromBytes(new byte[] {1, 2, 3});
                    var decoded58 = SolanaBytes.fromBase58(bytes.toBase58());
                    var decoded64 = SolanaBytes.fromBase64(bytes.toBase64());
                    var decodedHex = SolanaBytes.fromHex(bytes.toHex());
                    SolanaBytes empty = SolanaBytes.EMPTY;
                    int size = bytes.getSize();
                    boolean isEmpty = bytes.isEmpty();
                    byte first = bytes.get(0);
                    var slice = bytes.slice(0, 1);
                    bytes.copyInto(new byte[3]);
                    bytes.copyInto(new byte[4], 1);
                    byte[] copied = bytes.toByteArray();
                    var fromBytes = new SolanaAddress(recipient.toByteArray());
                    var fromBase58 = new SolanaAddress(recipient.toBase58());
                    var signer = KeypairSigner.fromSeed(seed);
                    var latest = provider.getLatestBlockhash().sendAwait().unwrap().getValue();
                    var message = SolanaTxV0.compile(signer.getPublicKey(), latest.getBlockhash(),
                        new TransferInstruction(signer.getPublicKey(), recipient,
                            SolUnit.SOL.toLamports("0.000001").toBigIntegerExact()));
                    var transaction = message.sign(signer);
                    SolanaTransactionUnsigned unsigned = message;
                    SolanaTransactionSigned.Builder partial = unsigned.signingBuilder().sign(signer);
                    SolanaTransactionSigned completed = partial.build();
                    SolanaTransaction common = completed;
                    var simulation = provider.simulateTransaction(unsigned, provider.getDefaultCommitment());
                    var partialSimulation = provider.simulateTransaction(partial.serializePartial(), provider.getDefaultCommitment());
                    var fee = provider.getFeeForMessage(common, provider.getDefaultCommitment());
                    SolanaTransactionUnsigned refreshed = completed.withNewBlockhash(latest.getBlockhash());
                    var legacy = SolanaTxLegacy.compile(signer.getPublicKey(), latest.getBlockhash(),
                        new TransferInstruction(signer.getPublicKey(), recipient, 1L));
                    var signature = provider.sendTransaction(transaction).sendAwait().unwrap();
                    var fetched = provider.getTransaction(signature).sendAwait().unwrap();
                    var fetchedVersion = provider.getTransaction(signature, 1).sendAwait().unwrap();
                    if (fetched != null) {
                        var type = fetched.getType();
                        var otherFields = fetched.getOtherFields();
                        java.util.List<io.ethers.solana.types.SolanaSignature> signatures = fetched.getTransaction().getSignatures();
                        io.ethers.solana.types.SolanaRPCMessage rpcMessage = fetched.getTransaction().getMessage();
                        if (!rpcMessage.getInstructions().isEmpty()) {
                            io.ethers.solana.types.SolanaBytes data = rpcMessage.getInstructions().get(0).getData();
                            java.util.List<io.ethers.solana.types.SolanaAddress> accounts = rpcMessage.getAccountKeys();
                        }
                        if (fetched.getMeta() != null) {
                            java.math.BigInteger paidFee = fetched.getMeta().getFee();
                            java.util.List<io.ethers.solana.types.TokenBalance> balances = fetched.getMeta().getPostTokenBalances();
                        }
                        var unsupported = io.ethers.solana.types.transaction.SolanaTxType.fromVersion(1);
                    }
                    var subscription = provider.subscribeSignature(signature).sendAsync();
                    var sol = SolUnit.LAMPORT.toSol(1000L);
                    var microLamports = SolUnit.MICRO_LAMPORT.fromLamports("0.5");
                    var customUnit = new SolUnit(3);
                    var converted = customUnit.convert(1.5, SolUnit.LAMPORT);
                }
            }
        """.trimIndent()
        val (result, diagnostics) = compileJava(source)
        check(result) { diagnostics }
    }

    test("Java can import EVM and Solana signatures and RPC transactions together") {
        val source = """
            import io.ethers.core.types.Signature;
            import io.ethers.core.types.RPCTransaction;
            import io.ethers.solana.providers.SolanaProvider;
            import io.ethers.solana.types.SolanaSignature;
            import io.ethers.solana.types.SolanaBlockhash;
            import io.ethers.solana.types.SolanaRPCTransaction;
            import io.ethers.solana.types.SolanaRPCTransactionData;
            import io.ethers.solana.types.SolanaRPCTransactionMeta;
            import io.ethers.solana.types.SolanaRPCMessage;
            import io.ethers.solana.types.SolanaRPCInstruction;
            import io.ethers.solana.types.SolanaNodeVersion;
            import io.ethers.solana.types.SolanaNodeHealth;
            public class SolanaJavaExample {
                public void consume(Signature evmSignature, RPCTransaction evmTransaction,
                    SolanaSignature solanaSignature, SolanaRPCTransaction solanaTransaction,
                    SolanaProvider provider) {
                    SolanaRPCTransactionData data = solanaTransaction.getTransaction();
                    SolanaRPCMessage message = data.getMessage();
                    SolanaBlockhash blockhash = message.getRecentBlockhash();
                    java.util.List<SolanaSignature> signatures = data.getSignatures();
                    java.util.List<SolanaRPCInstruction> instructions = message.getInstructions();
                    SolanaRPCTransactionMeta meta = solanaTransaction.getMeta();
                    SolanaNodeVersion version = provider.getVersion().sendAwait().unwrap();
                    SolanaNodeHealth health = provider.getHealth().sendAwait().unwrap();
                    SolanaSignature signature = provider.requestAirdrop(
                        message.getAccountKeys().get(0), 1L).sendAwait().unwrap();
                    SolanaRPCTransaction fetched = provider.getTransaction(solanaSignature).sendAwait().unwrap();
                }
            }
        """.trimIndent()
        val (result, diagnostics) = compileJava(source)
        check(result) { diagnostics }
    }

    test("Java cannot submit incomplete typed transactions or attach lookups to legacy transactions") {
        for (body in listOf(
            "provider.sendTransaction(unsigned);",
            "provider.sendTransaction(partial);",
            "unsigned.serialize();",
            "partial.serialize();",
            "SolanaTransaction transaction = partial;",
            "provider.sendTransaction(rpc);",
            "new SolanaBytes(new byte[] {1});",
            "new SolanaBytes(\"Ldp\");",
            "SolanaBytes.EMPTY.asByteArray();",
            "SolanaTransaction transaction = rpc;",
            "SolanaTxLegacy.compile(address, blockhash, instruction, java.util.Collections.emptyList());",
        )) {
            val source = """
                import io.ethers.solana.providers.SolanaProvider;
                import io.ethers.solana.types.transaction.*;
                import io.ethers.solana.types.*;
                import io.ethers.solana.instruction.Instruction;
                public class SolanaJavaExample {
                    public void invalid(SolanaProvider provider, SolanaTransactionUnsigned unsigned,
                        SolanaTransactionSigned.Builder partial, SolanaAddress address, SolanaBlockhash blockhash,
                        Instruction instruction, SolanaRPCTransaction rpc) { $body }
                }
            """.trimIndent()
            compileJava(source).first shouldBe false
        }
    }
})

private fun compileJava(source: String): Pair<Boolean, String> {
    val file = object : SimpleJavaFileObject(URI.create("string:///SolanaJavaExample.java"), JavaFileObject.Kind.SOURCE) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source
    }
    val diagnostics = DiagnosticCollector<JavaFileObject>()
    val output = Files.createTempDirectory("ethers-solana-java-api").toFile()
    try {
        val compiler = ToolProvider.getSystemJavaCompiler()
        compiler.getStandardFileManager(diagnostics, null, null).use { manager ->
            val result = compiler.getTask(null, manager, diagnostics, listOf("--release", "11", "-proc:none", "-classpath", System.getProperty("java.class.path"), "-d", output.path), null, listOf(file)).call()
            return result to diagnostics.diagnostics.joinToString("\n")
        }
    } finally {
        output.deleteRecursively()
    }
}
