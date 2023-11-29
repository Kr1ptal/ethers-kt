package io.ethers.examples.functionselectors

import io.ethers.abi.AbiFunction
import io.ethers.core.FastHex
import io.ethers.examples.gen.UniswapV2Router02
import io.ethers.providers.Provider
import io.ethers.providers.WsClient
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default

/**
 * Searching for all transactions with specific function selector, starting from the most recent block.
 * We get function selector in two ways: from ABI smart contract wrapper and manually from function signature.
 */
class FunctionSelectors(
    wsRpcUrl: String,
    private val abiFunction: AbiFunction,
    private val functionSignature: String,
    private val maxBlocks: Int,
) {
    // Initialize provider
    private val wsClient = WsClient(wsRpcUrl)
    private val wsProvider = Provider(wsClient)
    fun run() {
        // Get current block number
        val blockNumber = wsProvider.getBlockNumber().sendAwait().resultOrThrow()

        // Function selector obtained from smart contract wrapper
        val wrapperSelector = abiFunction.selector

        // Function selector obtained manually from function signature
        val abiFunction = AbiFunction.parseSignature(functionSignature)

        println("Searching for selector from SC wrapper: ${FastHex.encodeWithPrefix(wrapperSelector)}")
        println("Searching for selector from signature:  ${FastHex.encodeWithPrefix(abiFunction.selector)}")
        var blockCounter = blockNumber
        while (blockCounter > blockNumber - maxBlocks) {
            val block = wsProvider.getBlockWithTransactions(blockCounter).sendAwait().resultOrThrow()
            println("Searching block: ${block.number}")

            // # Searching for transactions with selector from SC wrapper
            block.transactions
                .filter { it.data != null && it.data!!.startsWith(wrapperSelector) }
                .forEach { println("Tx with selector from SC wrapper: ${it.hash}") }

            // # Searching for transactions manually from function signature
            block.transactions
                .filter { it.data != null && it.data!!.startsWith(abiFunction.selector) }
                .forEach { println("Tx with selector from signature:  ${it.hash}") }
            blockCounter--
        }

        wsClient.close()
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("FunctionSelectors")

    val wsRpc by argParser.option(ArgType.String, description = "WS RPC URL")
        .default("wss://ethereum.publicnode.com") // Mainnet WS url
    val abiFunctionSignature by argParser.option(ArgType.String, description = "Function signature")
        .default("swapExactTokensForTokens(uint256,uint256,address[],address,uint256)") // selector: 0x38ed1739
    val maxBlocks by argParser.option(ArgType.String, description = "Max blocks - how many blocks to search")
        .default("20")

    argParser.parse(args)

    FunctionSelectors(
        wsRpc,
        UniswapV2Router02.FUNCTION_SWAP_EXACT_TOKENS_FOR_TOKENS,
        abiFunctionSignature,
        maxBlocks.toInt(),
    ).run()
}
