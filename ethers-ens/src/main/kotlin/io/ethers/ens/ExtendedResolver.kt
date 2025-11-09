@file:Suppress("UNCHECKED_CAST", "FunctionName", "PropertyName", "RedundantVisibilityModifier", "RemoveRedundantQualifierName", "LocalVariableName", "unused")

package io.ethers.ens

import io.ethers.abi.AbiContract
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.abi.error.CustomContractError
import io.ethers.abi.error.CustomErrorFactory
import io.ethers.abi.error.CustomErrorFactoryResolver
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.providers.middleware.Middleware

public class ExtendedResolver(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    /**
     * Call contract function
     *
     * Selector: `0x3b3b57de`
     *
     * Signature:
     * ```solidity
     *     function addr(bytes32 node) external returns (address);
     * ```
     */
    public fun addr(node: Bytes): ReadFunctionCall<Address> = ReadFunctionCall(this.provider, this.address, FUNCTION_ADDR.encodeCall(listOf<Any>(node))) {
        val data = FUNCTION_ADDR.decodeResponse(it)
        data[0] as io.ethers.core.types.Address
    }

    /**
     * Call contract function
     *
     * Selector: `0x691f3431`
     *
     * Signature:
     * ```solidity
     *     function name(bytes32 node) external returns (string);
     * ```
     */
    public fun name(node: Bytes): ReadFunctionCall<String> = ReadFunctionCall(this.provider, this.address, FUNCTION_NAME.encodeCall(listOf<Any>(node))) {
        val data = FUNCTION_NAME.decodeResponse(it)
        data[0] as kotlin.String
    }

    /**
     * Call contract function
     *
     * Selector: `0x9061b923`
     *
     * Signature:
     * ```solidity
     *     function resolve(bytes name, bytes data) external returns (bytes);
     * ```
     */
    public fun resolve(name: Bytes, `data`: Bytes): ReadFunctionCall<Bytes> = ReadFunctionCall(this.provider, this.address, FUNCTION_RESOLVE.encodeCall(listOf<Any>(name, `data`))) {
        val data = FUNCTION_RESOLVE.decodeResponse(it)
        data[0] as io.ethers.core.types.Bytes
    }

    /**
     * Call contract function
     *
     * Selector: `0x01ffc9a7`
     *
     * Signature:
     * ```solidity
     *     function supportsInterface(bytes4 interfaceId) external returns (bool);
     * ```
     */
    public fun supportsInterface(interfaceId: Bytes): ReadFunctionCall<Boolean> = ReadFunctionCall(this.provider, this.address, FUNCTION_SUPPORTS_INTERFACE.encodeCall(listOf<Any>(interfaceId))) {
        val data = FUNCTION_SUPPORTS_INTERFACE.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    /**
     * Call contract function
     *
     * Selector: `0x59d1d43c`
     *
     * Signature:
     * ```solidity
     *     function text(bytes32 node, string key) external returns (string);
     * ```
     */
    public fun text(node: Bytes, key: String): ReadFunctionCall<String> = ReadFunctionCall(this.provider, this.address, FUNCTION_TEXT.encodeCall(listOf(node, key))) {
        val data = FUNCTION_TEXT.decodeResponse(it)
        data[0] as kotlin.String
    }

    public sealed class Error : CustomContractError()

    /**
     * Contract error
     *
     * Selector: `0x556f1830`
     *
     * Signature:
     * ```solidity
     *     error OffchainLookup(address sender, string[] urls, bytes callData, bytes4 callbackFunction, bytes extraData);
     * ```
     */
    public data class OffchainLookup(
        public val sender: Address,
        public val urls: List<String>,
        public val callData: Bytes,
        public val callbackFunction: Bytes,
        public val extraData: Bytes,
    ) : ExtendedResolver.Error() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as OffchainLookup
            if (sender != other.sender) return false
            if (urls != other.urls) return false
            if (callData != other.callData) return false
            if (callbackFunction != other.callbackFunction) return false
            if (extraData != other.extraData) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + urls.hashCode()
            result = 31 * result + callData.hashCode()
            result = 31 * result + callbackFunction.hashCode()
            result = 31 * result + extraData.hashCode()
            return result
        }

        public companion object : CustomErrorFactory<OffchainLookup> {
            @JvmStatic
            override val abi: AbiFunction =
                AbiFunction("OffchainLookup", listOf(AbiType.Address, AbiType.Array(AbiType.String), AbiType.Bytes, AbiType.FixedBytes(4), AbiType.Bytes), emptyList())

            @JvmStatic
            override fun decode(`data`: List<Any>): OffchainLookup = OffchainLookup(data[0] as io.ethers.core.types.Address, data[1] as List<kotlin.String>, data[2] as io.ethers.core.types.Bytes, data[3] as io.ethers.core.types.Bytes, data[4] as io.ethers.core.types.Bytes)
        }
    }

    public companion object {
        @JvmField
        public val ERRORS: List<CustomErrorFactory<out ExtendedResolver.Error>> =
            listOf(OffchainLookup)

        @JvmField
        public val FUNCTION_NAME: AbiFunction =
            AbiFunction("name", listOf(AbiType.FixedBytes(32)), listOf(AbiType.String))

        @JvmField
        public val FUNCTION_RESOLVE: AbiFunction =
            AbiFunction("resolve", listOf(AbiType.Bytes, AbiType.Bytes), listOf(AbiType.Bytes))

        @JvmField
        public val FUNCTION_ADDR: AbiFunction =
            AbiFunction("addr", listOf(AbiType.FixedBytes(32)), listOf(AbiType.Address))

        @JvmField
        public val FUNCTION_TEXT: AbiFunction =
            AbiFunction("text", listOf(AbiType.FixedBytes(32), AbiType.String), listOf(AbiType.String))

        @JvmField
        public val FUNCTION_SUPPORTS_INTERFACE: AbiFunction =
            AbiFunction("supportsInterface", listOf(AbiType.FixedBytes(4)), listOf(AbiType.Bool))

        init {
            CustomErrorFactoryResolver.addFactories(ERRORS)
        }

        @JvmStatic
        public fun decodeError(error: Bytes): ExtendedResolver.Error? {
            for (err in ERRORS) {
                return err.decode(error) ?: continue
            }
            return null
        }
    }
}
