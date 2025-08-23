package io.ethers.ens

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.core.ExceptionalError
import io.ethers.core.FastHex
import io.ethers.core.Jackson
import io.ethers.core.Result
import io.ethers.core.asTypeOrNull
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.unwrapOrReturn
import io.ethers.ens.EnsMiddleware.Companion.IPFS_GATEWAY
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.wrn
import io.ethers.providers.middleware.Middleware
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.util.concurrent.CompletableFuture

class EnsMiddleware @JvmOverloads constructor(
    provider: Middleware,
    private val registryAddress: Address,
    private val ccipLookupLimit: Int = 4,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : Middleware by provider {
    private val LOG = getLogger()
    private val registryContract = EnsRegistry(provider, registryAddress)

    @JvmOverloads
    constructor(
        provider: Middleware,
        ccipLookupLimit: Int = 4,
        client: OkHttpClient = OkHttpClient(),
    ) : this(
        provider,
        getRegistryAddressOrThrow(provider.chainId),
        ccipLookupLimit,
        client,
    )

    /**
     * Resolve ENS name to [Address], as per [specification](https://docs.ens.domains/dapp-developer-guide/resolving-names).
     *
     * Additional support for:
     * - [Wildcard resolution](https://docs.ens.domains/ens-improvement-proposals/ensip-10-wildcard-resolution)
     * - [Offchain metadata](https://docs.ens.domains/ens-improvement-proposals/ensip-16-offchain-metadata)
     */
    fun resolveAddress(ensName: String): CompletableFuture<Result<Address, Error>> = CompletableFuture.supplyAsync {
        resolveWithParameters(
            ensName,
            ExtendedResolver.FUNCTION_ADDR,
        ).map { AbiCodec.decode(AbiType.Address, it.asByteArray()) }
    }

    /**
     * Resolve ENS name to a text record associated with a [key], as per [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-5-text-records).
     */
    fun resolveText(ensName: String, key: String): CompletableFuture<Result<String, Error>> = CompletableFuture.supplyAsync {
        return@supplyAsync resolveWithParameters(
            ensName,
            ExtendedResolver.FUNCTION_TEXT,
            mutableListOf(key),
            mutableListOf(AbiType.String),
        ).map { AbiCodec.decode(AbiType.String, it.asByteArray()) }
    }

    /**
     * Reverse ENS name resolution, as per [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-3-reverse-resolution).
     */
    fun resolveEnsName(address: Address): CompletableFuture<Result<String, Error>> = CompletableFuture.supplyAsync {
        val resolvedName = resolveWithParameters(
            reverseAddressEnsName(address),
            ExtendedResolver.FUNCTION_NAME,
        ).map { AbiCodec.decode(AbiType.String, it.asByteArray()) }

        return@supplyAsync resolvedName.andThen { ensName ->
            // To be certain of reverse lookup ENS name, forward resolution must resolve to the original address
            resolveAddress(ensName).get().andThen {
                if (it == address) {
                    success(ensName)
                } else {
                    failure(Error.IncorrectOwner("True owner of ENS name: $ensName is not the same as provided address: $address"))
                }
            }
        }
    }

    /**
     * Resolve avatar of [ensName], as per
     * [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-12-avatar-text-records).
     */
    fun resolveAvatar(ensName: String): CompletableFuture<Result<URI, Error>> = CompletableFuture.supplyAsync {
        val uriRes = getAvatarUri(ensName)
        if (uriRes.isFailure()) return@supplyAsync uriRes

        // Get owner of ens name for NFT ownership validation
        val ensOwnerRes = resolveAddress(ensName).get()
        if (ensOwnerRes.isFailure()) return@supplyAsync ensOwnerRes

        return@supplyAsync matchAvatarUri(uriRes.unwrap(), ensOwnerRes.unwrap())
    }

    /**
     * Resolve avatar of [address], as per
     * [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-12-avatar-text-records).
     */
    fun resolveAvatar(address: Address): CompletableFuture<Result<URI, Error>> = CompletableFuture.supplyAsync {
        val uriRes = getAvatarUri(address)
        if (uriRes.isFailure()) return@supplyAsync uriRes

        return@supplyAsync matchAvatarUri(uriRes.unwrap(), address)
    }

    /**
     * Resolves [ensName] with provided [abiFunction] (eg. addr(bytes32), text(bytes32,string), name(bytes32), ...)
     * and corresponding parameters and parameter types.
     */
    private fun resolveWithParameters(
        ensName: String,
        abiFunction: AbiFunction,
        parameters: MutableList<Any> = mutableListOf(),
        paramTypes: MutableList<AbiType<*>> = mutableListOf(),
    ): Result<Bytes, Error> {
        // Check that ens name is valid
        if (ensName.isBlank() || (ensName.trim().length == 1 && ensName.contains("."))) {
            return failure(Error.EnsNameInvalid)
        }

        val nameHash = runCatching { NameHash.nameHash(ensName) }.unwrapOrReturn {
            return failure(Error.Normalisation(it))
        }

        val resolverResponse = getResolver(ensName)
        if (resolverResponse.isFailure()) return resolverResponse

        // Unwrap resolver from RpcResponse and call its addr() function.
        // If RpcResponse is an error, map it to error FailedToResolve.
        val resolver = resolverResponse.unwrap()
        val supportsWildcard = resolver.supportsInterface(ENSIP_10_INTERFACE_ID).call(BlockId.LATEST).sendAwait()

        // add nodehash as first parameter, because it is present in all resolutions
        parameters.add(0, Bytes(nameHash))
        paramTypes.add(0, AbiType.FixedBytes(32))
        if (supportsWildcard.unwrapElse(false)) {
            val dnsEncoded = NameHash.dnsEncode(ensName)
            val encodedParams = abiFunction.encodeCall(parameters)

            val resolveResult = resolver.resolve(dnsEncoded, encodedParams)
                .call(BlockId.LATEST)
                .sendAwait()

            // try to decode OffchainLookup error
            val resolveLookupRevert = resolveResult.unwrapErrorOrNull()?.asTypeOrNull<ExtendedResolver.OffchainLookup>()

            return if (resolveLookupRevert == null) {
                // result is resolved ens name
                resolveResult.mapError {
                    Error.FailedToResolve("Failed to resolve ens name: $ensName with resolver ${resolver.address}.", it)
                }
            } else {
                // result is OffchainLookup error
                resolveOffchain(
                    resolveLookupRevert,
                    resolver,
                    ccipLookupLimit,
                )
            }
        } else {
            // Simple ENS name resolution: resolve by calling corresponding resolving function
            // (eg. addr(bytes32), text(bytes32, string)) of resolver directly.

            // Validate that resolver supports abiFunction
            val supportsFunction =
                resolver.supportsInterface(abiFunction.selector).call(BlockId.LATEST).sendAwait()

            if (!supportsFunction.unwrapElse(false)) {
                return failure(Error.UnsupportedSelector(resolver.address, abiFunction.selector.toString()))
            }

            // create callback for corresponding function selector
            val callbackData = AbiCodec.encodeWithPrefix(
                abiFunction.selector,
                paramTypes,
                parameters,
            )

            return provider.call(
                CallRequest {
                    to = resolver.address
                    data = Bytes(callbackData)
                },
                BlockId.LATEST,
            )
                .mapError<Error> { Error.FailedToResolve("Failed to resolve ens name: $ensName with resolver ${resolver.address}.") }
                .andThen {
                    // Return different errors on empty address and failure to resolve
                    // TODO - handle differently
                    val isAddrCall = abiFunction.selector == ExtendedResolver.FUNCTION_ADDR.selector
                    if (isAddrCall && AbiCodec.decode(AbiType.Address, it.asByteArray()) == Address.ZERO) {
                        return@andThen failure(
                            Error.UnknownEnsName(resolver.address, FastHex.encodeWithPrefix(nameHash)),
                        )
                    }

                    success(it)
                }.sendAwait()
        }
    }

    /**
     * Get [ExtendedResolver] for [ensName] using [EnsRegistry] of current chain.
     */
    private fun getResolver(ensName: String): Result<ExtendedResolver, Error> {
        return getResolverAddress(ensName).map { ExtendedResolver(provider, it) }
    }

    /**
     * Get resolver address for [ensName] from [EnsRegistry].
     */
    private fun getResolverAddress(ensName: String): Result<Address, Error> {
        if (ensName.isEmpty()) {
            return failure(Error.UnknownResolver)
        }

        val nameHash: ByteArray = runCatching { NameHash.nameHash(ensName) }
            .unwrapOrReturn { return failure(Error.Normalisation(it)) }

        val address = registryContract.resolver(Bytes(nameHash))
            .call(BlockId.LATEST)
            .mapError<Error> { Error.ResolvingResolver(registryAddress, FastHex.encodeWithPrefix(nameHash)) }
            .andThen { if (it == Address.ZERO) failure(Error.UnknownResolver) else success(it) }
            .sendAwait()

        if (address.unwrapErrorOrNull()?.asTypeOrNull<Error.UnknownResolver>() != null) {
            return getResolverAddress(getParent(ensName))
        }

        return address
    }

    /**
     * If result of resolver.resolve() call is [ExtendedResolver.OffchainLookup] error, try to resolve the name using
     * [ERC-3668: CCIP offchain data retrieval](https://eips.ethereum.org/EIPS/eip-3668)
     */
    private fun resolveOffchain(
        revert: ExtendedResolver.OffchainLookup,
        resolver: ExtendedResolver,
        lookupLimit: Int,
    ): Result<Bytes, Error> {
        // OffchainLookup.sender has to be resolver address
        if (revert.sender != resolver.address) return failure(Error.NestedOffchainLookup)
        if (revert.callData.isEmpty) return failure(Error.CcipRevertDataInvalid("Calldata is empty!"))

        // get gateway result by trying urls one by one and passing sender and data returned by OffchainLookup error
        val gatewayResult = httpCall(revert.urls, revert.sender, revert.callData).unwrapOrReturn {
            return failure(it)
        }

        // call resolver.callbackFunction(gatewayResult, extraData). If this call is CCIP, repeat the procedure.
        val callbackData = AbiCodec.encodeWithPrefix(
            revert.callbackFunction,
            CALLBACK_FUNCTION_PARAM_TYPES,
            listOf(gatewayResult, revert.extraData),
        )

        // dynamic bytes
        val callbackResult = provider.call(
            CallRequest {
                to = resolver.address
                data = Bytes(callbackData)
            },
            BlockId.LATEST,
        ).sendAwait().unwrapOrReturn {
            return failure(Error.CcipCallFailed("Callback call failed", it))
        }

        // If callbackResult is OffchainLookup error, resolve using recursive CCIP calls
        val callbackLookupRevert = ExtendedResolver.OffchainLookup.decode(callbackResult)
        if (callbackLookupRevert != null) {
            if (lookupLimit <= 0) return failure(Error.CcipLookupLimit)

            return resolveOffchain(callbackLookupRevert, resolver, lookupLimit - 1)
        } else {
            // callbackResult is resolved ENS name. Decode dynamic bytes to address
            val resolvedDecoded = AbiCodec.decode(AbiType.Bytes, callbackResult.asByteArray())
            return success(resolvedDecoded)
        }
    }

    /**
     * Executes Cross-Chain Interoperability Protocol (CCIP-Read) request and returns opaque gateway response as [Bytes]
     * which is sent to the callbackFunction on Offchain Resolver contract.
     *
     * @param urls urls parameter of Offchain revert [ExtendedResolver.OffchainLookup]
     * @param sender sender parameter of [ExtendedResolver.OffchainLookup] - replacing {sender} RPC call parameter
     * @param calldata calldata parameter of [ExtendedResolver.OffchainLookup] - replacing {data} RPC call parameter
     */
    private fun httpCall(urls: List<String>, sender: Address, calldata: Bytes): Result<Bytes, Error> {
        if (urls.isEmpty()) return failure(Error.CcipCallFailed("No urls to resolve ens name!", null))

        for (url in urls) {
            // If url is missing mandatory {sender} parameter, try next url
            val request = buildCcipRequest(url, sender, calldata) ?: continue

            return try {
                httpClient.newCall(request).execute().use { handleCcipResponse(it, url) } ?: continue
            } catch (e: Exception) {
                LOG.err(e) { e.message ?: "" }
                failure(Error.CcipCallFailed("Unknown error", ExceptionalError(e)))
            }
        }

        return failure(Error.CcipCallFailed("All urls are invalid or got server response 5xx", null))
    }

    /**
     * If CCIP [response] from [url]:
     * - is successful, decode it and return [Bytes].
     * - has status code 4xx, return error. Execution is stopped.
     * - has status code 5xx, return null and try another url, if present.
     */
    private fun handleCcipResponse(
        response: Response,
        url: String,
    ): Result<Bytes, Error>? {
        if (response.isSuccessful) {
            val gatewayRequestDTO = Jackson.MAPPER.readValue(
                response.body.byteStream(),
                EnsGatewayResponseDTO::class.java,
            )

            return success(gatewayRequestDTO.data)
        }

        return if (response.code in 400..499) {
            // 4xx - return an error and stop
            val msg = "Received status code: ${response.code} during CCIP call (url: $url, error: ${response.message})"
            LOG.err { msg }
            failure(Error.CcipCallFailed(msg, null))
        } else {
            // 5xx - server issue, try different url
            LOG.wrn { "500 error during CCIP call: url: $url, error: ${response.message}" }
            null
        }
    }

    /**
     * Builds CCIP-read [okhttp3.Request] from [url], [sender] and [calldata], where [sender] and [calldata]
     * are RPC request parameters.
     *
     * If RPC url contains {data}, the request is GET, otherwise POST.
     *
     * If [url] is missing {sender} parameter, return null.
     */
    private fun buildCcipRequest(url: String, sender: Address, calldata: Bytes): Request? {
        if (!url.contains("{sender}")) return null // skip this url

        // URL expansion
        var href = url.replace("{sender}", sender.toString())

        return if (url.contains("{data}")) {
            href = href.replace("{data}", calldata.toString())
            Request.Builder().url(href).get().build()
        } else {
            val requestDTO = EnsGatewayRequestDTO(calldata, sender.toString())

            Request.Builder().url(href)
                .post(Jackson.MAPPER.writeValueAsString(requestDTO).toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .build()
        }
    }

    /**
     * Converts avatar URI to URL.
     *
     * If URI schema is:
     * - https or data, return unchanged
     * - ipfs, converts IPFS into HTTPS url
     * - eip155, resolves NFT avatar
     */
    private fun matchAvatarUri(avatarUri: URI, ensOwner: Address): Result<URI, Error> {
        return when (val scheme = avatarUri.scheme) {
            "https", "data" -> success(avatarUri)
            "ipfs" -> success(joinWithIPFSGateway(avatarUri))
            "eip155" -> {
                return getAvatarNFT(avatarUri, ensOwner)
                    .andThen(::resolveNftMetadata)
                    .map {
                        when (it.scheme) {
                            "ipfs" -> joinWithIPFSGateway(it)
                            else -> it
                        }
                    }
            }

            else -> failure(Error.UnsupportedScheme(scheme))
        }
    }

    /**
     * Retrieves [AvatarNFT] from [avatarUri] and returns it as [Result]. Performs NFT ownership validation.
     */
    private fun getAvatarNFT(avatarUri: URI, ensOwner: Address): Result<AvatarNFT, Error> {
        val parseResult = AvatarNFT.parse(avatarUri)
        if (parseResult.isFailure()) return parseResult
        val nftToken = parseResult.unwrap()

        // validate NFT ownership
        when (nftToken.nftType) {
            AvatarNFTType.ERC721 -> {
                val nft = ERC721(provider, nftToken.nftAddr)
                val nftOwnerRes = nft.ownerOf(nftToken.tokenId).call(BlockId.LATEST).sendAwait()

                if (nftOwnerRes.isFailure()) {
                    return failure(
                        Error.AvatarParsing(
                            "Error when retrieving owner of nft ${nftToken.nftAddr} (token id: ${nftToken.tokenId})",
                            null,
                        ),
                    )
                }

                val nftOwner = nftOwnerRes.unwrap()
                if (nftOwner != ensOwner) {
                    return failure(
                        Error.IncorrectOwner(
                            "ENS name owner: $ensOwner does not match NFT owner: $nftOwner",
                        ),
                    )
                }
            }

            AvatarNFTType.ERC1155 -> {
                val nft = ERC1155(provider, nftToken.nftAddr)
                val balanceRes = nft.balanceOf(ensOwner, nftToken.tokenId).call(BlockId.LATEST).sendAwait()

                if (balanceRes.isFailure()) {
                    return failure(
                        Error.AvatarParsing(
                            "Error when retrieving balance of nft ${nftToken.nftAddr} (token id: ${nftToken.tokenId}, owner: $ensOwner)",
                            null,
                        ),
                    )
                } else if (balanceRes.unwrap() == BigInteger.ZERO) {
                    return failure(
                        Error.IncorrectOwner(
                            "ENS owner has 0 balance of token: ${nftToken.tokenId} for nft: ${nftToken.nftAddr}",
                        ),
                    )
                }
            }
        }

        return success(nftToken)
    }

    /**
     * Retrieves metadata URL from NFT, executes it and retrieves "image" parameter from result.
     *
     * Metadata URL [example](https://ipfs.io/ipfs/QmYTuHaoY1winNAxmf7JmCmSrkChuMAAnqgSuJBTiWZe9f):
     * ipfs://ipfs/QmYTuHaoY1winNAxmf7JmCmSrkChuMAAnqgSuJBTiWZe9f
     */
    private fun resolveNftMetadata(token: AvatarNFT): Result<URI, Error> {
        val metadataUriStr = when (token.nftType) {
            AvatarNFTType.ERC721 -> ERC721(provider, token.nftAddr)
                .tokenURI(token.tokenId)
                .call(BlockId.LATEST)
                .sendAwait()

            AvatarNFTType.ERC1155 -> ERC1155(provider, token.nftAddr)
                .uri(token.tokenId)
                .call(BlockId.LATEST)
                .sendAwait()
        }.unwrapOrReturn {
            return failure(
                Error.AvatarParsing(
                    "Error when retrieving metadata URL for token: ${token.tokenId} of NFT: ${token.nftAddr}",
                    it,
                ),
            )
        }

        if (token.nftType == AvatarNFTType.ERC1155) {
            // Replace {id} with token id, zero padded to 64 hex characters
            metadataUriStr.replace(
                "{id}",
                FastHex.encodeWithoutPrefix(AbiCodec.encode(AbiType.UInt(256), token.tokenId)),
            )
        }

        val metadataUri = runCatching {
            val uri = URI(metadataUriStr)
            if (uri.scheme == "ipfs") joinWithIPFSGateway(uri)
            else uri
        }.unwrapOrReturn {
            return failure(Error.AvatarParsing("Error on parsing NFT metadata URL: $metadataUriStr", it))
        }

        // Execute metadataUri request and extract "image" attribute
        val request = Request.Builder().url(metadataUri.toURL()).build()
        return runCatching {
            httpClient.newCall(request).execute().use {
                if (!it.isSuccessful) {
                    return failure(
                        Error.AvatarParsing(
                            "Error on executing NFT metadata URL: $metadataUri for token: ${token.tokenId} of NFT: ${token.nftAddr} (${it.message})",
                            null,
                        ),
                    )
                }

                val metadataDTO = Jackson.MAPPER.readValue(
                    it.body.byteStream(),
                    MetadataDTO::class.java,
                )

                success(URI(metadataDTO.image))
            }
        }.unwrapOrReturn {
            return failure(
                Error.AvatarParsing("Error while execution metadata request for url: $metadataUri", it),
            )
        }
    }

    /**
     * Get avatar URI text record from [ensName].
     */
    private fun getAvatarUri(ensName: String): Result<URI, Error> {
        return resolveText(ensName, "avatar").get().andThen { uriStr ->
            if (uriStr.isEmpty()) {
                failure(Error.FailedToResolve("Failed to resolve avatar of ens name: $ensName"))
            } else try {
                success(URI(uriStr))
            } catch (e: Exception) {
                failure(Error.AvatarParsing("Error on parsing URI: $uriStr", ExceptionalError(e)))
            }
        }
    }

    /**
     * Get avatar URI text record from [address].
     */
    private fun getAvatarUri(address: Address): Result<URI, Error> {
        // Build reverse address ENS
        val reverseAddr = reverseAddressEnsName(address)

        // Get resolver for reverse address ENS
        val resolverResponse = getResolver(reverseAddr)
        if (resolverResponse.isFailure()) return resolverResponse
        val reverseResolver = resolverResponse.unwrap()

        // Try to get avatar via text() for reverse address ENS
        val uriRes = reverseResolver.text(Bytes(NameHash.nameHash(reverseAddr)), "avatar")
            .call(BlockId.LATEST)
            .sendAwait()

        if (uriRes.isFailure() || uriRes.unwrap().isEmpty()) {
            val nameHash = runCatching { NameHash.nameHash(reverseAddr) }.unwrapOrReturn {
                return failure(Error.Normalisation(it))
            }

            // If text() is unsuccessful, reverse resolve address to ENS name, validate its ownership and forward resolve to avatar
            return reverseResolver.name(Bytes(nameHash))
                .call(BlockId.LATEST)
                .sendAwait()
                .mapError<Error> { Error.FailedToResolve("Failed to resolve ens name for address: $address", it) }
                .andThen { ensName ->
                    // Validate ENS name by "resolving the returned name and calling addr on the resolver, checking it matches the original Ethereum address"
                    resolveAddress(ensName).get().andThen { resolved ->
                        if (resolved != address) {
                            failure(Error.IncorrectOwner("ENS name: $ensName resolves to: $resolved which is not equal to original address: $address"))
                        } else {
                            // Forward resolve avatar
                            getAvatarUri(ensName)
                        }
                    }
                }
        }

        return try {
            uriRes.map { URI(it) }
        } catch (e: Exception) {
            failure(Error.AvatarParsing("Error on parsing URI: ${uriRes.unwrap()}", ExceptionalError(e)))
        }
    }

    /**
     * Build ENS name used for reverse address resolution: <address without prefix>.addr.reverse
     */
    private fun reverseAddressEnsName(address: Address): String {
        return "${address.toString().substring(2)}.$ENS_DOMAIN_REVERSE_REGISTER"
    }

    /**
     * Convert IPFS native URL into HTTPS using [IPFS_GATEWAY], as per
     * [specification](https://docs-ipfs-tech.ipns.dweb.link/how-to/address-ipfs-on-web/#ipfs-addressing-in-brief).
     */
    private fun joinWithIPFSGateway(uri: URI): URI {
        val path = uri.toString().removePrefix("ipfs://").removePrefix("ipfs/")
        return URL(URL(IPFS_GATEWAY), path).toURI()
    }

    /**
     * Possible errors during ens name resolution
     */
    sealed class Error : Result.Error {
        /**
         * Ens name is not valid.
         */
        data object EnsNameInvalid : Error()

        /**
         * Error on ens name normalisation attempt.
         */
        data class Normalisation(val cause: ExceptionalError) : Error() {
            override fun doThrow(): Nothing {
                throw RuntimeException("Normalisation failed: $cause")
            }
        }

        // Errors when getting resolver address
        /**
         * Error on getting resolver address from registry
         */
        data class ResolvingResolver(
            val registryAddress: Address,
            val nameHash: String,
        ) : Error() {
            override fun doThrow(): Nothing {
                throw RuntimeException("Error when getting resolver address from registry> $registryAddress for nameHash: $nameHash.")
            }
        }

        /**
         * Resolver address not found for given nameHash on registry.
         */
        data object UnknownResolver : Error()

        // Errors when resolving ENS name with resolver
        /**
         * Resolver does not support selector
         */
        data class UnsupportedSelector(
            val resolver: Address,
            val selector: String,
        ) : Error() {
            override fun doThrow(): Nothing {
                throw RuntimeException("Resolver '$resolver' does not support selector '$selector'")
            }
        }

        /**
         * Resolver address resolved ens name to an empty address.
         */
        data class UnknownEnsName(
            val resolverAddr: Address,
            val nameHash: String,
        ) : Error() {
            override fun doThrow(): Nothing {
                throw RuntimeException("Resolver '$resolverAddr' resolved namehash '$nameHash' to an empty address!")
            }
        }

        /**
         * Resolver for ensName exists, but was not able to resolve it.
         */
        data class FailedToResolve(val message: String, val cause: Result.Error? = null) : Error() {
            override fun doThrow(): Nothing {
                throw RuntimeException("Failed to resolve ens name: $message, caused by: $cause")
            }
        }

        // Reverse lookup specific errors
        /**
         * Reverse lookup ENS name does not resolve to original address.
         * The owner of ENS name is incorrect
         */
        data class IncorrectOwner(val message: String) : Error()

        // Avatar specific errors
        /**
         * Avatar URI scheme is not supported
         */
        data class UnsupportedScheme(val scheme: String) : Error() {
            override fun doThrow(): Nothing {
                throw RuntimeException("Avatar URI scheme '$scheme' is not supported")
            }
        }

        data class AvatarParsing(val message: String, val cause: Result.Error?) : Error() {
            override fun doThrow(): Nothing {
                throw RuntimeException("$message, caused by: $cause")
            }
        }

        // CCIP specific errors
        /**
         * Cannot handle OffchainLookup raised inside nested call.
         */
        data object NestedOffchainLookup : Error()

        /**
         * CCIP calls reached a maximum limit.
         */
        data object CcipLookupLimit : Error()

        /**
         * Data returned with OffchainLookup error is invalid.
         */
        data class CcipRevertDataInvalid(val message: String) : Error()

        /**
         * Unknown error during CCIP call execution.
         */
        data class CcipCallFailed(val message: String, val cause: Result.Error?) : Error()
    }

    companion object {
        private val ENSIP_10_INTERFACE_ID = Bytes("0x9061b923")
        private val CALLBACK_FUNCTION_PARAM_TYPES = listOf(AbiType.Bytes, AbiType.Bytes)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val ENS_DOMAIN_REVERSE_REGISTER = "addr.reverse"
        private const val IPFS_GATEWAY = "https://ipfs.io/ipfs/"

        private val MAINNET_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val ROPSTEN_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val RINKEBY_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val GOERLI_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val SEPOLIA_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")

        private fun getRegistryAddress(chainId: Long): Address? {
            return when (chainId) {
                1L -> MAINNET_REGISTRY_ADDR
                3L -> ROPSTEN_REGISTRY_ADDR
                4L -> RINKEBY_REGISTRY_ADDR
                5L -> GOERLI_REGISTRY_ADDR
                11155111L -> SEPOLIA_REGISTRY_ADDR
                else -> return null
            }
        }

        private fun getRegistryAddressOrThrow(chainId: Long): Address {
            return getRegistryAddress(chainId)
                ?: throw IllegalArgumentException("No registry address found for chain id: $chainId")
        }

        private fun getParent(name: String): String {
            val ensName = if (name.isNotEmpty()) name.trim() else ""

            return if (ensName == "." || !ensName.contains(".")) ""
            else ensName.substring(ensName.indexOf(".") + 1)
        }
    }
}

private data class EnsGatewayRequestDTO(val data: Bytes, val sender: String)
private data class EnsGatewayResponseDTO(val data: Bytes)
