package io.ethers.ens

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.unwrapOrReturn
import java.math.BigInteger
import java.net.URI

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class MetadataDTO @JsonCreator constructor(
    @param:JsonProperty("image")
    val image: String,
)

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
        fun parse(avatarUri: URI): Result<AvatarNFT, EnsMiddleware.Error> {
            val data = avatarUri.toString().removePrefix("eip155:1/").split(":")
            if (data.size != 2) {
                return failure(EnsMiddleware.Error.AvatarParsing("Unsupported URI link: $avatarUri", null))
            }

            val inner = data[1].split("/")
            if (inner.size != 2) {
                return failure(EnsMiddleware.Error.AvatarParsing("Unsupported URI link path: $avatarUri", null))
            }

            val nftType = runCatching { AvatarNFTType.valueOf(data[0].uppercase()) }.unwrapOrReturn {
                return failure(EnsMiddleware.Error.AvatarParsing("Unsupported URI token type: ${data[0]}", it))
            }

            val nftAddr = runCatching { Address(inner[0]) }.unwrapOrReturn {
                return failure(EnsMiddleware.Error.AvatarParsing("Invalid URI NFT contract address: ${inner[0]}", it))
            }

            val tokenId = runCatching { BigInteger(inner[1]) }.unwrapOrReturn {
                return failure(EnsMiddleware.Error.AvatarParsing("Unsupported URI token id type: ${inner[1]}", it))
            }

            return success(AvatarNFT(nftType, nftAddr, tokenId))
        }
    }
}
