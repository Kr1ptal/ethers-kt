package io.ethers.providers

import io.ethers.core.FastHex
import io.ethers.core.Result
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.json.jackson.readBytes
import io.ethers.json.jackson.readHexBigInteger
import io.ethers.providers.bindings.AnvilInstance
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import java.math.BigInteger

/**
 * Provider implementation for interacting with an [Anvil](https://book.getfoundry.sh/reference/anvil/) node.
 * Contains additional functions for modifying the blockchain state.
 * */
@Suppress("MoveLambdaOutsideParentheses")
class AnvilProvider private constructor(
    private val anvil: AnvilInstance,
    provider: Provider,
) : Middleware by provider, AutoCloseable by anvil {
    init {
        if (provider.client is WsClient) {
            anvil.onClose.thenRun { provider.client.close() }
        }
    }

    /**
     * Set the auto-impersonate flag. When enabled, any transaction’s sender will be automatically impersonated.
     * */
    fun setAutoImpersonate(autoImpersonate: Boolean): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_autoImpersonateAccount", arrayOf(autoImpersonate), { true })
    }

    /**
     * Modify the balance of an account.
     * */
    fun setBalance(address: Address, balance: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setBalance", arrayOf(address, FastHex.encodeWithPrefix(balance)), { true })
    }

    /**
     * Modify the code of an account.
     * */
    fun setCode(address: Address, code: Bytes): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setCode", arrayOf(address, code), { true })
    }

    /**
     * Modify the nonce of an account.
     * */
    fun setNonce(address: Address, nonce: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setNonce", arrayOf(address, FastHex.encodeWithPrefix(nonce)), { true })
    }

    /**
     * Modify the storage of an account.
     * */
    fun setStorageAt(address: Address, key: Hash, value: Hash): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setStorageAt", arrayOf(address, key, value), { true })
    }

    /**
     * Set the coinbase address to be used in new blocks.
     * */
    fun setCoinbase(coinbase: Address): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setCoinbase", arrayOf(coinbase), { true })
    }

    /**
     * Remove a transaction from the mempool by its [txHash].
     * */
    fun removeMempoolTx(txHash: Hash): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_dropTransaction", arrayOf(txHash), { true })
    }

    /**
     * Remove all transactions from the mempool sent by the given [sender].
     * */
    fun removeMempoolTxsBySender(sender: Address): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_removePoolTransactions", arrayOf(sender), { true })
    }

    /**
     * Remove all transactions from the mempool.
     * */
    fun removeAllMempoolTxs(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_dropAllTransactions", emptyArray<Any>(), { true })
    }

    /**
     * Set the minimum gas price for the node to accept.
     * */
    fun setMinGasPrice(minGasPrice: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setMinGasPrice", arrayOf(FastHex.encodeWithPrefix(minGasPrice)), { true })
    }

    /**
     * Set the base fee for the next block.
     * */
    fun setNextBlockBaseFee(baseFee: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setNextBlockBaseFeePerGas", arrayOf(FastHex.encodeWithPrefix(baseFee)), { true })
    }

    /**
     * Mine the next block.
     * */
    fun mineNextBlock(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_mine", emptyArray<Any>(), { true })
    }

    /**
     * Return whether automatic block mining is enabled. Can be enabled or disabled using [setAutoMine].
     * */
    fun isAutoMine(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_getAutomine", emptyArray<Any>(), { it.valueAsBoolean })
    }

    /**
     * Enable or disable automatic block mining.
     * */
    fun setAutoMine(autoMine: Boolean): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_setAutomine", arrayOf(autoMine), { true })
    }

    /**
     * Set the block mining interval in [seconds].
     * */
    fun setMineInterval(seconds: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_setIntervalMining", arrayOf(FastHex.encodeWithPrefix(seconds)), { true })
    }

    /**
     * Set the exact timestamp of the next block.
     * */
    fun setNextBlockTimestamp(timestamp: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setNextBlockTimestamp", arrayOf(FastHex.encodeWithPrefix(timestamp)), { true })
    }

    /**
     * Set the block time interval in [seconds]. The timestamp of the next block will be set to `lastBlockTimestamp +
     * seconds`.
     */
    fun setBlockTimeInterval(seconds: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setBlockTimestampInterval", arrayOf(seconds), { true })
    }

    /**
     * Jump forward in time by the given amount of time, in [seconds]. Returns the total time adjustment, in seconds.
     * */
    fun elapseBlockTime(seconds: Long): RpcRequest<Long, RpcError> {
        return RpcCall(client, "evm_increaseTime", arrayOf(FastHex.encodeWithPrefix(seconds)), { it.valueAsLong })
    }

    /**
     * Set the block gas limit for future blocks.
     * */
    fun setBlockGasLimit(gasLimit: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(
            client,
            "evm_setBlockGasLimit",
            arrayOf(FastHex.encodeWithPrefix(gasLimit)),
            { it.valueAsBoolean },
        )
    }

    /**
     * Snapshot the state of the blockchain at the current block, returning the snapshot id. Can be used to revert to
     * this state later using [revertSnapshot].
     * */
    fun getSnapshot(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "evm_snapshot", emptyArray<Any>(), { it.readHexBigInteger() })
    }

    /**
     * Revert the state of the blockchain to a previous snapshot. Can be used to revert to a previous state returned by
     * [getSnapshot].
     * */
    fun revertSnapshot(snapshotId: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_revert", arrayOf(snapshotId), { it.valueAsBoolean })
    }

    /**
     * Fork the state of the blockchain at the latest block fetched from the [forkUrl] JSON-RPC endpoint.
     * */
    fun forkState(forkUrl: String): RpcRequest<Boolean, RpcError> {
        return RpcCall(
            client,
            "anvil_reset",
            arrayOf(
                mapOf(
                    "forking" to mapOf(
                        "jsonRpcUrl" to forkUrl,
                    ),
                ),
            ),
            { true },
        )
    }

    /**
     * Fork the state of the blockchain at the given block number fetched from the [forkUrl] JSON-RPC endpoint.
     * */
    fun forkStateAtBlock(forkUrl: String, blockNumber: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(
            client,
            "anvil_reset",
            arrayOf(
                mapOf(
                    "forking" to mapOf(
                        "jsonRpcUrl" to forkUrl,
                        "blockNumber" to FastHex.encodeWithPrefix(blockNumber),
                    ),
                ),
            ),
            { true },
        )
    }

    /**
     * Reset the state of the blockchain to the initial state, disabling forking if it was enabled.
     *
     * Initial state is defined [here](https://hardhat.org/hardhat-network/docs/reference#initial-state).
     * */
    fun resetStateAndDisableForking(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_reset", emptyArray<Any>(), { true })
    }

    /**
     * Return the bytes of the complete blockchain state. Can be reimported using [applyBlockchainState].
     * */
    fun dumpBlockchainState(): RpcRequest<Bytes, RpcError> {
        return RpcCall(client, "anvil_dumpState", emptyArray<Any>(), { it.readBytes() })
    }

    /**
     * Apply the blockchain state from the bytes previously dumped by [dumpBlockchainState]. Can be used to reimport a
     * previously dumped state. Will overwrite any colliding accounts / storage slots.
     * */
    fun applyBlockchainState(state: Bytes): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_loadState", arrayOf(state), { true })
    }

    companion object {
        /**
         * Create a new [AnvilProvider] from an [AnvilInstance].
         * */
        fun fromAnvil(
            anvil: AnvilInstance,
            config: RpcClientConfig = RpcClientConfig(),
        ): Result<AnvilProvider, Provider.Error> {
            return Provider.fromUrl(anvil.endpointWs, config, anvil.chainId).map {
                AnvilProvider(anvil, it)
            }
        }
    }
}
