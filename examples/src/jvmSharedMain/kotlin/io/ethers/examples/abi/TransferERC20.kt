package io.ethers.examples.abi

import io.ethers.core.types.Address
import io.ethers.examples.gen.ERC20
import io.ethers.providers.Provider
import io.ethers.signers.PrivateKeySigner
import io.github.artificialpb.bignum.BigInteger
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

/**
 * This example shows how to use smart contract ABI of ERC20 token USDC (src/main/abi) to generate contract wrappers
 * automatically with abigen-plugin (see build.gradle), and send tokens from one address to another.
 */

class TransferERC20(
    privateKey: String,
    private val tokenAddress: String,
    private val receiver: String,
    private val amount: BigInteger,
    rpcUrl: String,
) {
    private val provider = Provider.fromUrl(rpcUrl).unwrap()
    private val signer = PrivateKeySigner(privateKey)

    fun run() {
        // Initialise token contract
        val token = ERC20(provider, Address(tokenAddress))

        println("Sending amount: $amount of token: $tokenAddress to: $receiver")
        // Submit transaction to transfer 1 USDC token
        val pendingTransaction = token.transfer(Address(receiver), amount)
            .send(signer)
            .sendAwait()
            .unwrap()

        // Wait for transaction to be included in a block.
        // Wait for tx inclusion for 12 retries, using the default 6-second interval and 1 confirmation.
        val receipt = pendingTransaction.awaitInclusion(12).unwrap()

        println("Tx ${receipt.transactionHash} was included in block ${receipt.blockNumber}")
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("io.ethers.examples.abi.TransferERC20")

    val privateKey by argParser.option(ArgType.String, description = "Private key").required()
    val receiver by argParser.option(ArgType.String, description = "Address of token receiver").required()
    val tokenAddress by argParser.option(ArgType.String, description = "Token address")
        .default("0xD87Ba7A50B2E7E660f678A895E4B72E7CB4CCd9C") // USDC on Goerli
    val amount by argParser.option(ArgType.String, description = "Token amount (in lowest denomination)")
        .default("1000000") // 1 USDC
    val httpRpc by argParser.option(ArgType.String, description = "HTTP RPC URL")
        .default("https://ethereum-goerli.publicnode.com") // Goerli RPC

    argParser.parse(args)

    val transferERC20 = TransferERC20(privateKey, tokenAddress, receiver, amount.toBigInteger(), httpRpc)
    transferERC20.run()
}
