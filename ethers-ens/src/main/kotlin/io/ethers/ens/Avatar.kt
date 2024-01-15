package io.ethers.ens

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ethers.core.types.Address
import io.ethers.providers.types.RpcResponse
import java.lang.NumberFormatException
import java.math.BigInteger
import java.net.URI

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class MetadataDTO(val image: String)

internal enum class AvatarNFTType {
    ERC721,
    ERC1155,
}

internal class AvatarNFT private constructor(
    val nftType: AvatarNFTType,
    val nftAddr: Address,
    val tokenId: BigInteger,
) {
    companion object {
        /**
         * Converts avatar URI into [AvatarNFT].
         *
         * Returns error [EnsResolver.Error.AvatarParsing].
         */
        fun parse(avatarUri: URI): RpcResponse<AvatarNFT> {
            val dataArr = avatarUri.toString().removePrefix("eip155:1/").split(":")
            val (nftType, innerData) = if (dataArr.size == 2) {
                try {
                    Pair(AvatarNFTType.valueOf(dataArr[0].uppercase()), dataArr[1])
                } catch (e: IllegalArgumentException) {
                    return RpcResponse.error(
                        EnsResolver.Error.AvatarParsing(
                            "Unsupported URI token type: $dataArr[0]",
                            null,
                        ),
                    )
                }
            } else {
                return RpcResponse.error(EnsResolver.Error.AvatarParsing("Unsupported URI link: $avatarUri", null))
            }

            val innerDataArr = innerData.split("/")
            val (nftAddr, tokenId) = if (innerDataArr.size == 2) {
                try {
                    Pair(Address(innerDataArr[0]), BigInteger(innerDataArr[1]))
                } catch (e: IllegalArgumentException) {
                    return RpcResponse.error(
                        EnsResolver.Error.AvatarParsing(
                            "Invalid URI NFT contract address: ${innerDataArr[0]}",
                            e,
                        ),
                    )
                } catch (e: NumberFormatException) {
                    return RpcResponse.error(
                        EnsResolver.Error.AvatarParsing(
                            "Unsupported URI token id type: ${innerDataArr[1]}",
                            e,
                        ),
                    )
                }
            } else {
                return RpcResponse.error(EnsResolver.Error.AvatarParsing("Unsupported URI link path: $avatarUri", null))
            }

            return RpcResponse.result(AvatarNFT(nftType, nftAddr, tokenId))
        }
    }
}
