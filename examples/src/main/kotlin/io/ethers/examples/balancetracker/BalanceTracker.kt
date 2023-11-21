package io.ethers.examples.balancetracker

import ERC20
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.ethers.providers.WsClient
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcResponse
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CompletableFuture

/**
 * Monitoring of ETH balance and balance of list of ERC20 tokens for a given address, using manual request batching.
 */

// Tokens for which we monitor the corresponding balances
private val TOKEN_LIST = listOf(
    "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
    "0xfffffffFf15AbF397dA76f1dcc1A1604F45126DB",
    "0xff56Cc6b1E6dEd347aA0B7676C85AB0B3D08B0FA",
    "0xfeF4185594457050cC9c23980d301908FE057Bb1",
    "0xfe9A29aB92522D14Fc65880d817214261D8479AE",
    "0xfd30C9BEA1A952FEeEd2eF2C6B2Ff8A8FC4aAD07",
    "0xfc82bb4ba86045Af6F327323a46E80412b91b27d",
    "0xfc05987bd2be489ACCF0f509E44B0145d68240f7",
    "0xfb5453340C03db5aDe474b27E68B6a9c6b2823Eb",
    "0xfb40e79E56cc7D406707B66C4Fd175E07EB2Ae3C",
    "0xfa6de2697D59E88Ed7Fc4dFE5A33daC43565ea41",
    "0xfFffFffF2ba8F66D4e51811C5190992176930278",
    "0xfF709449528B6fB6b88f557F7d93dEce33bca78D",
    "0xfF20817765cB7f73d4bde2e66e067E58D11095C2",
    "0xfE18be6b3Bd88A2D2A7f928d00292E7a9963CfC6",
    "0xfC98e825A2264D890F9a1e68ed50E1526abCcacD",
    "0xfC1E690f61EFd961294b3e1Ce3313fBD8aa4f85d",
    "0xfB7B4564402E5500dB5bB6d63Ae671302777C75a",
    "0xfB5c6815cA3AC72Ce9F5006869AE67f18bF77006",
    "0xfAd45E47083e4607302aa43c65fB3106F1cd7607",
    "0xfA5047c9c78B8877af97BDcb85Db743fD7313d4a",
    "0xf9FBE825BFB2bF3E387af0Dc18caC8d87F29DEa8",
    "0xf99d58e463A2E07e5692127302C20A191861b4D6",
    "0xf911a7ec46a2c6fa49193212fe4a2a9B95851c27",
    "0xf8e386EDa857484f5a12e4B5DAa9984E06E73705",
    "0xf8E9F10c22840b613cdA05A0c5Fdb59A4d6cd7eF",
    "0xf8C3527CC04340b208C854E985240c02F7B7793f",
    "0xf87C4B9C0c1528147CAc4E05b7aC349A9Ab23A12",
    "0xf83ae621A52737e3Ef9307af91df834Ed8431aC3",
    "0xf680429328caaaCabee69b7A9FdB21a71419c063",
    "0xddaC9C604BA6Bc4ACEc0FBB485B83f390ECF2f31",
    "0xdd974D5C2e2928deA5F71b9825b8b646686BD200",
    "0xdc9Ac3C20D1ed0B540dF9b1feDC10039Df13F99c",
    "0xdbDD6F355A37b94e6C7D32fef548e98A280B8Df5",
    "0xdb726152680eCe3c9291f1016f1d36f3995f6941",
)

class BalanceTracker(
    httpRpcUrl: String,
    wsRpcUrl: String,
    val address: Address
) {
    // Init providers
    private val httpClient = HttpClient(httpRpcUrl)
    private val httpProvider = Provider(httpClient)
    private val wsClient = WsClient(wsRpcUrl)
    private val wsProvider = Provider(wsClient)

    // Stored token balances
    private lateinit var tokenBalances: HashMap<String, BigDecimal>
    fun run() {
        val decimals = getTokenDecimals(TOKEN_LIST, httpProvider)
        val balances = getTokenBalances(address, TOKEN_LIST, decimals, httpProvider)
        println("Balances for block ${httpProvider.getBlockNumber().sendAwait().resultOrThrow()}")
        displayTokenBalances(balances)

        // Problematic for some public ws nodes
        // For each new block, update token balances and display them
        val stream = wsProvider.subscribeNewHeads().sendAwait().resultOrThrow()
        stream.forEach {
            println("\nBalances for block ${it.number}")
            tokenBalances = getTokenBalances(address, TOKEN_LIST, decimals, httpProvider)
            displayTokenBalances(tokenBalances)
        }

        // Close web socket client
        wsClient.close()
    }
}

fun getTokenDecimals(tokenList: List<String>, provider: Provider): HashMap<String, BigInteger> {
    val decimals = hashMapOf<String, BigInteger>()
    val responses = hashMapOf<String, CompletableFuture<RpcResponse<BigInteger>>>()

    // Init batch
    val batch = BatchRpcRequest()

    tokenList.forEach {
        // Init ERC20 contract
        val erc20 = ERC20(provider, Address(it))

        // Init "decimals" RpcRequest and store it in responses
        responses[it] = erc20.decimals().call(BlockId.LATEST).batch(batch)
    }

    if (batch.sendAwait()) {
        responses.forEach { (addr, future) ->
            // Unwrap each response representing token decimal and store it in decimals
            decimals[addr] = future.get().resultOrThrow()
        }
    } else {
        throw Exception("Batch did not execute successfully")
    }

    return decimals
}

fun getTokenBalances(
    address: Address,
    tokenList: List<String>,
    decimals: HashMap<String, BigInteger>,
    provider: Provider
): HashMap<String, BigDecimal> {
    val balances = hashMapOf<String, BigDecimal>()
    val responses = hashMapOf<String, CompletableFuture<RpcResponse<BigInteger>>>()

    // Init batch
    val batch = BatchRpcRequest()

    // The first request is ETH balance
    responses["ETH"] = provider.getBalance(address, BlockId.LATEST).batch(batch)

    // Other requests are balances for tokens
    tokenList.forEach {
        // Init ERC20 contract
        val erc20 = ERC20(provider, Address(it))
        // Init "balanceOf" RpcRequest and store it in responses
        responses[it] = erc20.balanceOf(address).call(BlockId.LATEST).batch(batch)
    }

    if (batch.sendAwait()) {
        // The first request is ETH balance
        balances["ETH"] = responses["ETH"]!!.get().resultOrThrow().toBigDecimal().scaleByPowerOfTen(-18)

        // Other requests are balances for tokens
        responses.forEach { (addr, future) ->
            val decimal = decimals[addr]?.toInt() ?: 18

            // Unwrap each response representing token balance, format it, and store it in balances
            val balance = future.get().resultOrThrow().toBigDecimal()
            balances[addr] = if (balance == BigDecimal.ZERO) BigDecimal.ZERO else balance.scaleByPowerOfTen(-decimal)
        }
    } else {
        throw Exception("Batch did not execute successfully")
    }

    return balances
}

fun displayTokenBalances(balances: HashMap<String, BigDecimal>) {
    balances.forEach { (addr, balance) -> println("$addr - $balance") }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("TransferERC20")
    val httpRpc by argParser.option(ArgType.String, description = "HTTP RPC URL")
        .default("https://ethereum.publicnode.com")
    val wsRpc by argParser.option(ArgType.String, description = "WS RPC URL")
        .default("wss://mainnet.gateway.tenderly.co")
    val address by argParser.option(ArgType.String, description = "Token holder address")
        .default("0x0D0707963952f2fBA59dD06f2b425ace40b492Fe") // Gate.io address

    argParser.parse(args)

    val balanceTracker = BalanceTracker(httpRpc, wsRpc, Address(address))
    balanceTracker.run()
}