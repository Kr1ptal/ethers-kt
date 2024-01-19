package io.ethers.ens

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ethers.core.types.Address
import io.ethers.providers.types.RpcResponse
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
         * Returns error [EnsMiddleware.Error.AvatarParsing].
         */
        fun parse(avatarUri: URI): RpcResponse<AvatarNFT> {
            val dataArr = avatarUri.toString().removePrefix("eip155:1/").split(":")
            if (dataArr.size != 2) {
                return RpcResponse.error(EnsMiddleware.Error.AvatarParsing("Unsupported URI link: $avatarUri", null))
            }

            val nftType = runCatching { AvatarNFTType.valueOf(dataArr[0].uppercase()) }
                .getOrElse {
                    return RpcResponse.error(
                        EnsMiddleware.Error.AvatarParsing("Unsupported URI token type: ${dataArr[0]}", null),
                    )
                }

            val innerDataArr = dataArr[1].split("/")
            if (innerDataArr.size != 2) {
                return RpcResponse.error(EnsMiddleware.Error.AvatarParsing("Unsupported URI link path: $avatarUri", null))
            }

            val nftAddr = runCatching { Address(innerDataArr[0]) }
                .getOrElse {
                    return RpcResponse.error(
                        EnsMiddleware.Error.AvatarParsing("Invalid URI NFT contract address: ${innerDataArr[0]}", it),
                    )
                }

            val tokenId = runCatching { BigInteger(innerDataArr[1]) }
                .getOrElse {
                    return RpcResponse.error(
                        EnsMiddleware.Error.AvatarParsing("Unsupported URI token id type: ${innerDataArr[1]}", it),
                    )
                }

            return RpcResponse.result(AvatarNFT(nftType, nftAddr, tokenId))
        }
    }
}
