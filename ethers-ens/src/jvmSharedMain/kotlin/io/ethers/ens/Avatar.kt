package io.ethers.ens

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ethers.core.types.Address
import io.ethers.core.unwrapOrReturn
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.Serializable

@Serializable
internal data class MetadataDTO(
    val image: String,
)

internal enum class AvatarNFTType {
    ERC721,
    ERC1155,
}

internal class AvatarNFT private constructor(
    val chainId: Long,
    val nftType: AvatarNFTType,
    val nftAddr: Address,
    val tokenId: BigInteger,
) {
    companion object {
        /**
         * Converts avatar URI into [AvatarNFT].
         *
         * Expected format: `eip155:<chainId>/<nftType>:<contractAddr>/<tokenId>`
         *
         * Returns error [EnsMiddleware.Error.AvatarParsing].
         */
        fun parse(avatarUri: String): Result<AvatarNFT, EnsMiddleware.Error> {
            val withoutPrefix = avatarUri.removePrefix("eip155:")
            if (withoutPrefix == avatarUri) {
                return Err(EnsMiddleware.Error.AvatarParsing("Unsupported URI link: $avatarUri", null))
            }

            // Format: <chainId>/<nftType>:<contractAddr>/<tokenId>
            val parts = withoutPrefix.split("/")
            if (parts.size != 3) {
                return Err(EnsMiddleware.Error.AvatarParsing("Unsupported URI link: $avatarUri", null))
            }

            val chainId = runCatching { parts[0].toLong() }.unwrapOrReturn {
                return Err(EnsMiddleware.Error.AvatarParsing("Invalid chain ID in URI: $avatarUri", it))
            }

            val typeAndAddr = parts[1].split(":")
            if (typeAndAddr.size != 2) {
                return Err(EnsMiddleware.Error.AvatarParsing("Unsupported URI link: $avatarUri", null))
            }

            val nftType = runCatching { AvatarNFTType.valueOf(typeAndAddr[0].uppercase()) }.unwrapOrReturn {
                return Err(EnsMiddleware.Error.AvatarParsing("Unsupported URI token type: ${typeAndAddr[0]}", it))
            }

            val nftAddr = runCatching { Address(typeAndAddr[1]) }.unwrapOrReturn {
                return Err(EnsMiddleware.Error.AvatarParsing("Invalid URI NFT contract address: ${typeAndAddr[1]}", it))
            }

            val tokenId = runCatching { BigInteger(parts[2]) }.unwrapOrReturn {
                return Err(EnsMiddleware.Error.AvatarParsing("Unsupported URI token id type: ${parts[2]}", it))
            }

            return Ok(AvatarNFT(chainId, nftType, nftAddr, tokenId))
        }
    }
}
