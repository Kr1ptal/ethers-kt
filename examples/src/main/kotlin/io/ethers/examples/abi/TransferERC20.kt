import io.ethers.core.types.Address
import io.ethers.examples.ConstantsGoerli
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.ethers.signers.PrivateKeySigner
import java.math.BigInteger

/**
 * This example shows how to use smart contract ABI of ERC20 token USDC (src/main/abi) to generate contract wrappers
 * automatically with abigen-plugin, and send tokens from one address to another.
 */
fun main() {
    val httpClient = HttpClient(ConstantsGoerli.HTTP_URL)
    val provider = Provider(httpClient)
    // Get private key and receiver wallet address from environment variables
    val signer = PrivateKeySigner(System.getenv("W1_PRIVATE_KEY"))
    val receiver = Address(System.getenv("W2_ADDR"))

    // Initialise token contract
    val token = ERC20(provider, ConstantsGoerli.USDC_ADDR)
    // Submit transaction to transfer 1 USDC token
    val pendingTransaction = token.transfer(receiver, BigInteger.valueOf(1000000))
        .send(signer)
        .sendAwait()
        .resultOrThrow()

    // Wait for transaction to be included in a block
    val receipt = pendingTransaction.awaitInclusion(5).resultOrThrow()

    println("Tx ${receipt.transactionHash} was included in block ${receipt.blockNumber}")
}