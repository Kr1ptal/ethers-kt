package io.ethers.examples.eip712

import io.ethers.abi.eip712.EIP712Domain
import io.ethers.abi.eip712.EIP712TypedData
import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.utils.EthUnit
import io.ethers.examples.gen.Seaport_1_6
import io.ethers.providers.Provider
import io.ethers.signers.PrivateKeySigner
import io.ethers.signers.Signer
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.math.BigInteger
import kotlin.system.exitProcess

/**
 * Encode, sign, and validate a Seaport Order based on the EIP-712 typed data signature.
 */
class EIP712SigningExample(
    private val provider: Provider,
    private val signer: Signer,
) {
    fun run() {
        val seaport = Seaport_1_6(provider, SEAPORT_ADDRESS)

        val domain = EIP712Domain(
            "Seaport",
            "1.6",
            BigInteger.ONE,
            SEAPORT_ADDRESS,
        )

        val orderComponents = Seaport_1_6.OrderComponents(
            offerer = signer.address,
            zone = Address("0x004C00500000aD104D7DBd00e3ae0A5C00560C00"),
            offer = listOf(
                Seaport_1_6.OfferItem(
                    itemType = BigInteger.TWO,
                    // shared storefront
                    token = Address("0xA604060890923Ff400e8c6f5290461A83AEDACec"),
                    identifierOrCriteria = "2".toBigInteger(), // erc721
                    startAmount = EthUnit.ETHER.toWei(1).toBigInteger(),
                    endAmount = EthUnit.ETHER.toWei(3).toBigInteger(),
                ),
            ),
            consideration = listOf(
                Seaport_1_6.ConsiderationItem(
                    itemType = BigInteger.ONE,
                    // weth
                    token = Address("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"),
                    identifierOrCriteria = "1".toBigInteger(), // erc20
                    startAmount = EthUnit.GWEI.toWei(100).toBigInteger(),
                    endAmount = EthUnit.GWEI.toWei(300).toBigInteger(),
                    recipient = Address("0x6225Dd7302a5aA91116A057a03722A2B12b87337"),
                ),
            ),
            orderType = BigInteger.ZERO,
            startTime = "1757856850".toBigInteger(),
            endTime = "2757856850".toBigInteger(),
            zoneHash = Bytes("0x0000000000000000000000000000000000000000000000000000000000000000"),
            salt = "16178208897136618".toBigInteger(),
            conduitKey = Bytes("0x0000007b02230091a7ed01230072f7006a004d60a8d4e71d599b8104250f0000"),
            counter = seaport.getCounter(signer.address).call(BlockId.LATEST).sendAwait().unwrap(),
        )

        val orderHash = seaport.getOrderHash(orderComponents).call(BlockId.LATEST).sendAwait().unwrap()
        val typedData = EIP712TypedData.from(orderComponents, domain)
        val signature = typedData.sign(signer)

        val validateResult = seaport.validate(
            listOf(
                Seaport_1_6.Order(
                    parameters = Seaport_1_6.OrderParameters(
                        offerer = orderComponents.offerer,
                        zone = orderComponents.zone,
                        offer = orderComponents.offer,
                        consideration = orderComponents.consideration,
                        orderType = orderComponents.orderType,
                        startTime = orderComponents.startTime,
                        endTime = orderComponents.endTime,
                        zoneHash = orderComponents.zoneHash,
                        salt = orderComponents.salt,
                        conduitKey = orderComponents.conduitKey,
                        totalOriginalConsiderationItems = orderComponents.consideration.size.toBigInteger(),
                    ),
                    signature = Bytes(signature.toByteArray()),
                ),
            ),
        )
            .call(BlockId.LATEST)
            .sendAwait()

        println("Order Hash: $orderHash")
        println("TypedData: " + Jackson.MAPPER.writeValueAsString(typedData))
        println("Signature hash: ${Bytes(typedData.signatureHash())}")
        println("Signature: ${Bytes(signature.toByteArray())}")
        println("Validate result: $validateResult")

        exitProcess(0)
    }

    companion object {
        private val SEAPORT_ADDRESS = Address("0x0000000000000068F116a894984e2DB1123eB395")
    }
}

fun main(args: Array<String>) {
    val argParser = ArgParser("EIP712SigningExample")

    val signerPrivateKey by argParser.option(ArgType.String, description = "Private key of the order signer")
        .default("0x175d535f07e8dbaec0f02aaee4f74f86bbfe77511c0d2044eedb6914d0b231ab")
    val rpcUrl by argParser.option(ArgType.String, description = "RPC URL")
        .default("https://ethereum-rpc.publicnode.com")

    argParser.parse(args)

    val provider = Provider.fromUrl(rpcUrl).unwrap()
    val signer = PrivateKeySigner(signerPrivateKey)
    EIP712SigningExample(provider, signer).run()
}
