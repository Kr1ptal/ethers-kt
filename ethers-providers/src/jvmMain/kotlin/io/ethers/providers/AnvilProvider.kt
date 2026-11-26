package io.ethers.providers

import io.ethers.core.FastHex
import io.ethers.core.HexBigIntegerSerializer
import io.ethers.core.Result
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.providers.bindings.AnvilInstance
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcParams
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.rpcParams
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * Provider implementation for interacting with an [Anvil](https://book.getfoundry.sh/reference/anvil/) node.
 * Contains additional functions for modifying the blockchain state.
 * */
class AnvilProvider private constructor(
    private val anvil: AnvilInstance,
    override val provider: Provider,
) : Middleware by provider, AutoCloseable by anvil {
    /**
     * Set the auto-impersonate flag. When enabled, any transaction’s sender will be automatically impersonated.
     * */
    fun setAutoImpersonate(autoImpersonate: Boolean): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_autoImpersonateAccount", rpcParams(autoImpersonate), ACKNOWLEDGED)
    }

    /**
     * Modify the balance of an account.
     * */
    fun setBalance(address: Address, balance: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setBalance", rpcParams(address, FastHex.encodeWithPrefix(balance)), ACKNOWLEDGED)
    }

    /**
     * Modify the code of an account.
     * */
    fun setCode(address: Address, code: Bytes): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setCode", rpcParams(address, code), ACKNOWLEDGED)
    }

    /**
     * Modify the nonce of an account.
     * */
    fun setNonce(address: Address, nonce: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setNonce", rpcParams(address, FastHex.encodeWithPrefix(nonce)), ACKNOWLEDGED)
    }

    /**
     * Modify the storage of an account.
     * */
    fun setStorageAt(address: Address, key: Hash, value: Hash): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setStorageAt", rpcParams(address, key, value), ACKNOWLEDGED)
    }

    /**
     * Set the coinbase address to be used in new blocks.
     * */
    fun setCoinbase(coinbase: Address): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setCoinbase", rpcParams(coinbase), ACKNOWLEDGED)
    }

    /**
     * Remove a transaction from the mempool by its [txHash].
     * */
    fun removeMempoolTx(txHash: Hash): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_dropTransaction", rpcParams(txHash), ACKNOWLEDGED)
    }

    /**
     * Remove all transactions from the mempool sent by the given [sender].
     * */
    fun removeMempoolTxsBySender(sender: Address): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_removePoolTransactions", rpcParams(sender), ACKNOWLEDGED)
    }

    /**
     * Remove all transactions from the mempool.
     * */
    fun removeAllMempoolTxs(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_dropAllTransactions", RpcParams.EMPTY, ACKNOWLEDGED)
    }

    /**
     * Set the minimum gas price for the node to accept.
     * */
    fun setMinGasPrice(minGasPrice: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setMinGasPrice", rpcParams(FastHex.encodeWithPrefix(minGasPrice)), ACKNOWLEDGED)
    }

    /**
     * Set the base fee for the next block.
     * */
    fun setNextBlockBaseFee(baseFee: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setNextBlockBaseFeePerGas", rpcParams(FastHex.encodeWithPrefix(baseFee)), ACKNOWLEDGED)
    }

    /**
     * Mine the next block.
     * */
    fun mineNextBlock(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_mine", RpcParams.EMPTY, ACKNOWLEDGED)
    }

    /**
     * Return whether automatic block mining is enabled. Can be enabled or disabled using [setAutoMine].
     * */
    fun isAutoMine(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_getAutomine", RpcParams.EMPTY, Boolean.serializer())
    }

    /**
     * Enable or disable automatic block mining.
     * */
    fun setAutoMine(autoMine: Boolean): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_setAutomine", rpcParams(autoMine), ACKNOWLEDGED)
    }

    /**
     * Set the block mining interval in [seconds].
     * */
    fun setMineInterval(seconds: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_setIntervalMining", rpcParams(FastHex.encodeWithPrefix(seconds)), ACKNOWLEDGED)
    }

    /**
     * Set the exact timestamp of the next block.
     * */
    fun setNextBlockTimestamp(timestamp: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setNextBlockTimestamp", rpcParams(FastHex.encodeWithPrefix(timestamp)), ACKNOWLEDGED)
    }

    /**
     * Set the block time interval in [seconds]. The timestamp of the next block will be set to `lastBlockTimestamp +
     * seconds`.
     */
    fun setBlockTimeInterval(seconds: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_setBlockTimestampInterval", rpcParams(seconds), ACKNOWLEDGED)
    }

    /**
     * Jump forward in time by the given amount of time, in [seconds]. Returns the total time adjustment, in seconds.
     * */
    fun elapseBlockTime(seconds: Long): RpcRequest<Long, RpcError> {
        return RpcCall(client, "evm_increaseTime", rpcParams(FastHex.encodeWithPrefix(seconds)), Long.serializer())
    }

    /**
     * Set the block gas limit for future blocks.
     * */
    fun setBlockGasLimit(gasLimit: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(
            client,
            "evm_setBlockGasLimit",
            rpcParams(FastHex.encodeWithPrefix(gasLimit)),
            Boolean.serializer(),
        )
    }

    /**
     * Snapshot the state of the blockchain at the current block, returning the snapshot id. Can be used to revert to
     * this state later using [revertSnapshot].
     * */
    fun getSnapshot(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "evm_snapshot", RpcParams.EMPTY, HexBigIntegerSerializer)
    }

    /**
     * Revert the state of the blockchain to a previous snapshot. Can be used to revert to a previous state returned by
     * [getSnapshot].
     * */
    fun revertSnapshot(snapshotId: BigInteger): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "evm_revert", rpcParams(snapshotId), Boolean.serializer())
    }

    /**
     * Fork the state of the blockchain at the latest block fetched from the [forkUrl] JSON-RPC endpoint.
     * */
    fun forkState(forkUrl: String): RpcRequest<Boolean, RpcError> {
        return RpcCall(
            client,
            "anvil_reset",
            rpcParams(
                mapOf(
                    "forking" to mapOf(
                        "jsonRpcUrl" to forkUrl,
                    ),
                ),
            ),
            ACKNOWLEDGED,
        )
    }

    /**
     * Fork the state of the blockchain at the given block number fetched from the [forkUrl] JSON-RPC endpoint.
     * */
    fun forkStateAtBlock(forkUrl: String, blockNumber: Long): RpcRequest<Boolean, RpcError> {
        return RpcCall(
            client,
            "anvil_reset",
            rpcParams(
                mapOf(
                    "forking" to mapOf(
                        "jsonRpcUrl" to forkUrl,
                        "blockNumber" to FastHex.encodeWithPrefix(blockNumber),
                    ),
                ),
            ),
            ACKNOWLEDGED,
        )
    }

    /**
     * Reset the state of the blockchain to the initial state, disabling forking if it was enabled.
     *
     * Initial state is defined [here](https://hardhat.org/hardhat-network/docs/reference#initial-state).
     * */
    fun resetStateAndDisableForking(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_reset", RpcParams.EMPTY, ACKNOWLEDGED)
    }

    /**
     * Return the bytes of the complete blockchain state. Can be reimported using [applyBlockchainState].
     * */
    fun dumpBlockchainState(): RpcRequest<Bytes, RpcError> {
        return RpcCall(client, "anvil_dumpState", RpcParams.EMPTY, Bytes.serializer())
    }

    /**
     * Apply the blockchain state from the bytes previously dumped by [dumpBlockchainState]. Can be used to reimport a
     * previously dumped state. Will overwrite any colliding accounts / storage slots.
     * */
    fun applyBlockchainState(state: Bytes): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "anvil_loadState", rpcParams(state), ACKNOWLEDGED)
    }

    override fun close() {
        provider.close()
        anvil.close()
    }

    companion object {
        /**
         * Create a new [AnvilProvider] from an [AnvilInstance].
         * */
        fun fromAnvil(
            anvil: AnvilInstance,
            config: RpcClientConfig = RpcClientConfig(),
        ): Result<AnvilProvider, Provider.Error> {
            return Provider.builder(anvil.endpointWs).config(config).build(anvil.chainId).map {
                AnvilProvider(anvil, it)
            }
        }
    }
}

/** For calls that report success by returning at all: reads past whatever result they return, and returns `true`. */
private val ACKNOWLEDGED: DeserializationStrategy<Boolean> = JsonElement.serializer().map { true }
