package io.ethers.examples.batchrequests

import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.examples.gen.UniswapV2Pair
import io.ethers.providers.Provider
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.await
import io.ethers.providers.types.batchRequest
import io.ethers.providers.types.unwrap
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.math.BigDecimal

/**
 * Request batching of pool reserves on different DEXes for WETH / USDC pair and calculating WETH price,
 * showcasing both consolidated and manual batching methods.
 */
class BatchRequests(
    rpcUrl: String,
    uniPoolAddr: String,
    sushiPoolAddr: String,
) {
    // Init provider
    private val provider = Provider.fromUrl(rpcUrl).unwrap()

    // Init pair contracts
    private val uniPool = UniswapV2Pair(provider, Address(uniPoolAddr))
    private val sushiPool = UniswapV2Pair(provider, Address(sushiPoolAddr))

    fun run() {
        var uniWethPrice: BigDecimal
        var sushiWethPrice: BigDecimal

        // Consolidated request batching
        var (uniReserves, sushiReserves) = batchRequest(
            uniPool.getReserves().call(BlockId.LATEST),
            sushiPool.getReserves().call(BlockId.LATEST),
        ).await().unwrap()

        uniWethPrice = uniReserves._reserve0.toBigDecimal(6) / uniReserves._reserve1.toBigDecimal(18)
        sushiWethPrice = sushiReserves._reserve0.toBigDecimal(6) / sushiReserves._reserve1.toBigDecimal(18)

        println(
            """
        Consolidated request batching:
        WETH price on Uniswap: $uniWethPrice USDC
        WETH price on Sushiswap: $sushiWethPrice USDC
        Price difference: ${(uniWethPrice - sushiWethPrice).abs()} USDC
            """.trimIndent() + '\n',
        )

        // Manual request batching
        // Init batch
        val batch = BatchRpcRequest()

        // Init blockchain calls and add them to batch
        val uniFuture = uniPool.getReserves().call(BlockId.LATEST).batch(batch)
        val sushiFuture = sushiPool.getReserves().call(BlockId.LATEST).batch(batch)

        if (batch.sendAwait()) {
            // success
            uniReserves = uniFuture.get().unwrap()
            uniWethPrice = uniReserves._reserve0.toBigDecimal(6) / uniReserves._reserve1.toBigDecimal(18)

            sushiReserves = sushiFuture.get().unwrap()
            sushiWethPrice = sushiReserves._reserve0.toBigDecimal(6) / sushiReserves._reserve1.toBigDecimal(18)

            println(
                """
            Manual request batching:
            WETH price on Uniswap: $uniWethPrice USDC
            WETH price on Sushiswap: $sushiWethPrice USDC
            Price difference: ${(uniWethPrice - sushiWethPrice).abs()} USDC
                """.trimIndent(),
            )
        } else {
            throw Exception("Batch did not execute successfully")
        }
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("BatchRequests")

    val rpcUrl by argParser.option(ArgType.String, description = "RPC URL")
        .default("https://ethereum.publicnode.com") // Mainnet HTTP url
    val uniPoolAddr by argParser.option(ArgType.String, description = "Uniswap V2 USDC/WETH pair address")
        .default("0xb4e16d0168e52d35cacd2c6185b44281ec28c9dc") // Mainnet
    val sushiPoolAddr by argParser.option(ArgType.String, description = "Sushiswap USDC/WETH pair address")
        .default("0x397ff1542f962076d0bfe58ea045ffa2d347aca0") // Mainnet

    argParser.parse(args)

    BatchRequests(rpcUrl, uniPoolAddr, sushiPoolAddr).run()
}
