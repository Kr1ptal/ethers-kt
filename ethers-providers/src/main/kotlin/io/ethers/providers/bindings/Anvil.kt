package io.ethers.providers.bindings

import io.ethers.core.FastHex
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Hash
import io.ethers.crypto.Secp256k1
import io.ethers.crypto.bip39.MnemonicCode
import io.ethers.providers.AnvilProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Anvil instance, created by [AnvilBuilder]. It's automatically stopped when the JVM shuts down.
 *
 * The instance can be used to create [io.ethers.providers.AnvilProvider] via [AnvilProvider.fromAnvil].
 * */
class AnvilInstance(
    private val process: Process,
    port: Int,
    val privateKeys: List<Secp256k1.SigningKey>,
    val chainId: Long,
) : AutoCloseable {
    private val onCloseFutures = ArrayList<CompletableFuture<Unit>>()

    /**
     * HTTP endpoint of the Anvil server.
     * */
    val endpointHttp: String = "http://localhost:$port"

    /**
     * WebSocket endpoint of the Anvil server.
     * */
    val endpointWs: String = "ws://localhost:$port"

    /**
     * Check if the Anvil server is still running.
     * */
    val isRunning: Boolean
        get() = process.isAlive

    /**
     * Future that completes before the Anvil server is stopped.
     * */
    val onClose: CompletableFuture<Unit>
        get() = synchronized(onCloseFutures) {
            val future = CompletableFuture<Unit>()
            onCloseFutures.add(future)
            return future
        }

    override fun close() {
        synchronized(onCloseFutures) {
            onCloseFutures.forEach { it.complete(Unit) }
        }

        process.destroy()

        try {
            process.waitFor(5, TimeUnit.SECONDS)
            println("Anvil instance closed normally")
        } catch (e: Exception) {
            println("Anvil instance closed with error")
            process.destroyForcibly()
        }
    }
}

/**
 * Builder for [AnvilInstance]. Anvil instance is created via [spawn].
 * */
class AnvilBuilder {
    private var binaryPath: String? = null
    private var port: Int = 0
    private var blockTime: Long? = null
    private var chainId: Long = HARDHAT_CHAIN_ID
    private var mnemonic: MnemonicCode? = null
    private var forkUrl: String? = null
    private var forkBlockNumber: Long? = null
    private var forkTransactionHash: Hash? = null
    private var spawnTimeout: Long = DEFAULT_SPAWN_TIMEOUT
    private var autoImpersonate: Boolean? = null
    private val additionalArgs = ArrayList<String>()

    /**
     * Set the path to the Anvil binary. If not set, it's assumed `anvil` is in the PATH.
     * */
    fun binaryPath(binaryPath: String) = apply { this.binaryPath = binaryPath }

    /**
     * Set the port to listen on. If not set, a random port will be chosen.
     * */
    fun port(port: Int) = apply { this.port = port }

    /**
     * Set the block mining interval in seconds. If not set, the default is 1.
     * */
    fun blockTime(blockTime: Long) = apply { this.blockTime = blockTime }

    /**
     * Set the chain ID. If not set, the default is 31337.
     * */
    fun chainId(chainId: Long) = apply { this.chainId = chainId }

    /**
     * Set the mnemonic to use for the initial private key. If not set, the default is `test test test test test test
     * test test test test test junk`.
     * */
    fun mnemonic(mnemonic: MnemonicCode) = apply { this.mnemonic = mnemonic }

    /**
     * Set the URL of the fork to use. If not set, forking is disabled.
     * */
    fun forkUrl(forkUrl: String) = apply { this.forkUrl = forkUrl }

    /**
     * Set the block number to fork from. If not set, it will fork from the latest block.
     *
     * Mutually exclusive with [forkTransactionHash].
     * */
    fun forkBlockNumber(forkBlockNumber: Long) = apply { this.forkBlockNumber = forkBlockNumber }

    /**
     * Set the transaction hash to fork from. If not set, it will fork from the latest block.
     *
     * Mutually exclusive with [forkBlockNumber].
     * */
    fun forkTransactionHash(forkTransactionHash: Hash) = apply { this.forkTransactionHash = forkTransactionHash }

    /**
     * Set the timeout for the spawn process. If not set, the default is 10 seconds.
     * */
    fun spawnTimeout(spawnTimeout: Long) = apply { this.spawnTimeout = spawnTimeout }

    /**
     * Set the auto-impersonate flag. When enabled, any transaction’s sender will be automatically impersonated.
     * If not set, the default is false.
     * */
    fun autoImpersonate(autoImpersonate: Boolean) = apply { this.autoImpersonate = autoImpersonate }

    /**
     * Add additional arguments to the Anvil command line.
     * */
    fun additionalArg(arg: String) = apply { this.additionalArgs.add(arg) }

    fun spawn(): Result<AnvilInstance, Error> {
        val commands = ArrayList<String>()
        (binaryPath ?: "anvil").let {
            commands.add(it)
        }
        port.let {
            commands.add("-p")
            commands.add(it.toString())
        }
        blockTime?.let {
            commands.add("-b")
            commands.add(it.toString())
        }
        chainId.let {
            commands.add("--chain-id")
            commands.add(it.toString())
        }
        mnemonic?.let {
            commands.add("-m")
            commands.add(it.words.joinToString(" "))
        }
        forkUrl?.let {
            commands.add("-f")
            commands.add(it)
        }
        forkBlockNumber?.let {
            commands.add("--fork-block-number")
            commands.add(it.toString())
        }
        forkTransactionHash?.let {
            commands.add("--fork-transaction-hash")
            commands.add(it.toString())
        }
        if (autoImpersonate == true) {
            commands.add("--auto-impersonate")
        }
        additionalArgs.forEach { commands.add(it) }

        val process = ProcessBuilder().command(commands).start()

        // close process when JVM is shutting down
        Runtime.getRuntime().addShutdownHook(Thread { process.destroy() })

        var port = this.port
        val privateKeys = ArrayList<Secp256k1.SigningKey>()

        val startTime = System.currentTimeMillis()
        val reader = process.inputStream.bufferedReader()
        val errorReader = process.errorStream.bufferedReader()
        while (true) {
            if (System.currentTimeMillis() - startTime > spawnTimeout) {
                return failure(Error.SpawnTimeout(spawnTimeout))
            }

            if (errorReader.ready()) {
                val fullError = StringBuilder()
                while (true) {
                    val nextErrorLine = errorReader.readLine() ?: break
                    fullError.append("\n").append(nextErrorLine)
                }
                return failure(Error.SpawnError(fullError.toString()))
            }

            if (!reader.ready()) {
                Thread.sleep(10)
                continue
            }

            var line: String = reader.readLine()

            // "Listening on 127.0.0.1:8545" is printed to stdout when anvil is ready
            if (line.startsWith("Listening on")) {
                // parse actual port - in case it was not explicitly set
                port = line.split(":")[1].trim().toInt()
                break
            }

            /*
             * Private Keys
             * ==================
             *
             * (0) 0xabcd...
             */
            if (line.startsWith("Private Keys")) {
                // seek to first line with private key
                while (true) {
                    line = reader.readLine()
                    if (line.startsWith("(")) break
                }

                do {
                    val hexKey = line.split(" ")[1]
                    if (hexKey.length != 66 || !FastHex.isValidHex(hexKey)) {
                        throw IllegalStateException("Invalid private key format: $hexKey")
                    }

                    privateKeys.add(Secp256k1.SigningKey(FastHex.decode(hexKey)))
                    line = reader.readLine()
                } while (line.isNotEmpty() && line.startsWith("("))
            }
        }

        return success(AnvilInstance(process, port, privateKeys, chainId))
    }

    sealed interface Error : Result.Error {
        data class SpawnTimeout(val spawnTimeout: Long) : Error
        data class SpawnError(val error: String) : Error
    }

    companion object {
        private const val HARDHAT_CHAIN_ID = 31337L
        private const val DEFAULT_SPAWN_TIMEOUT = 10_000L
    }
}
