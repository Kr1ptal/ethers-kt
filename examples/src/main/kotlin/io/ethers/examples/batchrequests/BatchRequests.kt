package io.ethers.examples.batchrequests

import UniswapV2Pair
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.examples.ConstantsMainnet
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.await
import io.ethers.providers.types.batchRequest
import io.ethers.providers.types.resultOrThrow
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Demonstrating request batching for pool reserves retrieval on various DEXes, showcasing both consolidated and
 * manual batching methods.
 */
fun main() {
    // Init provider
    val httpClient = HttpClient(ConstantsMainnet.HTTP_URL)
    val httpProvider = Provider(httpClient)

    val uniPoolAddr = Address("0xb4e16d0168e52d35cacd2c6185b44281ec28c9dc") // Uniswap V2 USDC/WETH
    val sushiPoolAddr = Address("0x397ff1542f962076d0bfe58ea045ffa2d347aca0") // Sushiswap USDC/WETH

    // Init pair contracts
    val uniPool = UniswapV2Pair(httpProvider, uniPoolAddr)
    val sushiPool = UniswapV2Pair(httpProvider, sushiPoolAddr)

    var uniWethPrice: BigDecimal
    var sushiWethPrice: BigDecimal

    // Consolidated request batching
    var (uniReserves, sushiReserves) = batchRequest(
        uniPool.getReserves().call(BlockId.LATEST),
        sushiPool.getReserves().call(BlockId.LATEST),
    ).await().resultOrThrow()

    uniWethPrice = formatUnits(uniReserves._reserve0, 6) / formatUnits(uniReserves._reserve1, 18)
    sushiWethPrice = formatUnits(sushiReserves._reserve0, 6) / formatUnits(sushiReserves._reserve1, 18)

    println(
        """
        Simplified request batching:
        WETH price on Uniswap: $uniWethPrice USDC
        WETH price on Sushiswap: $sushiWethPrice USDC
        Price difference: ${(uniWethPrice - sushiWethPrice).abs()} USDC
    """.trimIndent() + '\n'
    )

    // Manual request batching
    // Init batch
    val batch = BatchRpcRequest()

    // Init blockchain calls and add them to batch
    val uniFuture = uniPool.getReserves().call(BlockId.LATEST).batch(batch)
    val sushiFuture = sushiPool.getReserves().call(BlockId.LATEST).batch(batch)

    if (batch.sendAwait()) {
        // success
        uniReserves = uniFuture.get().resultOrThrow()
        uniWethPrice = formatUnits(uniReserves._reserve0, 6) / formatUnits(uniReserves._reserve1, 18)

        sushiReserves = sushiFuture.get().resultOrThrow()
        sushiWethPrice = formatUnits(sushiReserves._reserve0, 6) / formatUnits(sushiReserves._reserve1, 18)

        println(
            """
            Manual request batching:
            WETH price on Uniswap: $uniWethPrice USDC
            WETH price on Sushiswap: $sushiWethPrice USDC
            Price difference: ${(uniWethPrice - sushiWethPrice).abs()} USDC
        """.trimIndent()
        )
    } else {
        throw Exception("Batch did not execute successfully")
    }
}

// Convert wei to units with specific decimal places
fun formatUnits(value: BigInteger, decimalPlaces: Int): BigDecimal {
    val weiValue = BigDecimal(value)
    return weiValue.scaleByPowerOfTen(-decimalPlaces)
}