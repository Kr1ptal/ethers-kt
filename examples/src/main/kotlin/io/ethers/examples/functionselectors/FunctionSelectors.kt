package io.ethers.examples.functionselectors

import UniswapV2Router02
import io.ethers.abi.AbiFunction
import io.ethers.examples.ConstantsMainnet
import io.ethers.providers.Provider
import io.ethers.providers.WsClient

/**
 * Here we search for all transactions for specific function selector, starting from the most recent block
 */
fun main() {
    // Initialize provider for subscription and for sending transactions
    val wsClient = WsClient(ConstantsMainnet.WS_URL)
    val wsProvider = Provider(wsClient)

    val blockNumber = wsProvider.getBlockNumber().sendAwait().resultOrThrow()
    val maxBlocks = 5 // how many blocks to search

    // Function selector obtained from smart contract wrapper
    println("Searching for transactions with selector from SC wrapper...")
    val wrapperSelector = UniswapV2Router02.FUNCTION_SWAP_EXACT_TOKENS_FOR_TOKENS.selector

    var blockCounter = blockNumber
    while (blockCounter >= blockNumber - maxBlocks) {
        val block = wsProvider.getBlockWithTransactions(blockCounter).sendAwait().resultOrThrow()
        block.transactions
            .filter { it.data != null && it.data!!.startsWith(wrapperSelector) }
            .forEach { println("block: $blockCounter, tx: ${it.hash}") }
        blockCounter--
    }


    // Function selector obtained manually from function signature
    println("Searching for transactions manually from function description...")
    val signature = "swapExactTokensForTokens(uint256,uint256,address[],address,uint256)" // 0x38ed1739
    val function = AbiFunction.parseSignature(signature)

    blockCounter = blockNumber
    while (blockCounter >= blockNumber - maxBlocks) {
        val block = wsProvider.getBlockWithTransactions(blockCounter).sendAwait().resultOrThrow()
        block.transactions
            .filter { it.data != null && it.data!!.startsWith(function.selector) }
            .forEach { println("block: $blockCounter, tx: ${it.hash}") }
        blockCounter--
    }
    wsClient.close()
}
