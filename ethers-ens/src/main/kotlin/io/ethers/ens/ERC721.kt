@file:Suppress("UNCHECKED_CAST", "FunctionName", "PropertyName", "RedundantVisibilityModifier", "RemoveRedundantQualifierName", "LocalVariableName", "unused")

package io.ethers.ens

import io.ethers.abi.AbiContract
import io.ethers.abi.AbiEvent
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractEvent
import io.ethers.abi.EventFactory
import io.ethers.abi.EventFilter
import io.ethers.abi.call.FunctionCall
import io.ethers.abi.call.PayableFunctionCall
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Log
import io.ethers.providers.middleware.Middleware
import java.math.BigInteger

public class ERC721(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    /**
     * Call contract function
     *
     * Selector: `0x095ea7b3`
     *
     * Signature:
     * ```solidity
     *     function approve(address _approved, uint256 _tokenId) external payable;
     * ```
     */
    public fun approve(_approved: Address, _tokenId: BigInteger): PayableFunctionCall<Unit> = PayableFunctionCall(this.provider, this.address, FUNCTION_APPROVE.encodeCall(listOf(_approved, _tokenId))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x70a08231`
     *
     * Signature:
     * ```solidity
     *     function balanceOf(address _owner) external returns (uint256);
     * ```
     */
    public fun balanceOf(_owner: Address): ReadFunctionCall<BigInteger> = ReadFunctionCall(this.provider, this.address, FUNCTION_BALANCE_OF.encodeCall(listOf<Any>(_owner))) {
        val data = FUNCTION_BALANCE_OF.decodeResponse(it)
        data[0] as java.math.BigInteger
    }

    /**
     * Call contract function
     *
     * Selector: `0x081812fc`
     *
     * Signature:
     * ```solidity
     *     function getApproved(uint256 _tokenId) external returns (address);
     * ```
     */
    public fun getApproved(_tokenId: BigInteger): ReadFunctionCall<Address> = ReadFunctionCall(this.provider, this.address, FUNCTION_GET_APPROVED.encodeCall(listOf<Any>(_tokenId))) {
        val data = FUNCTION_GET_APPROVED.decodeResponse(it)
        data[0] as io.ethers.core.types.Address
    }

    /**
     * Call contract function
     *
     * Selector: `0xe985e9c5`
     *
     * Signature:
     * ```solidity
     *     function isApprovedForAll(address _owner, address _operator) external returns (bool);
     * ```
     */
    public fun isApprovedForAll(_owner: Address, _operator: Address): ReadFunctionCall<Boolean> = ReadFunctionCall(this.provider, this.address, FUNCTION_IS_APPROVED_FOR_ALL.encodeCall(listOf<Any>(_owner, _operator))) {
        val data = FUNCTION_IS_APPROVED_FOR_ALL.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    /**
     * Call contract function
     *
     * Selector: `0x06fdde03`
     *
     * Signature:
     * ```solidity
     *     function name() external returns (string _name);
     * ```
     */
    public fun name(): ReadFunctionCall<String> = ReadFunctionCall(this.provider, this.address, FUNCTION_NAME.encodeCall(emptyList())) {
        val data = FUNCTION_NAME.decodeResponse(it)
        data[0] as kotlin.String
    }

    /**
     * Call contract function
     *
     * Selector: `0x150b7a02`
     *
     * Signature:
     * ```solidity
     *     function onERC721Received(address _operator, address _from, uint256 _tokenId, bytes _data) external returns (bytes4);
     * ```
     */
    public fun onERC721Received(
        _operator: Address,
        _from: Address,
        _tokenId: BigInteger,
        _data: Bytes,
    ): FunctionCall<Bytes> = FunctionCall(this.provider, this.address, FUNCTION_ON_ERC721_RECEIVED.encodeCall(listOf(_operator, _from, _tokenId, _data))) {
        val data = FUNCTION_ON_ERC721_RECEIVED.decodeResponse(it)
        data[0] as io.ethers.core.types.Bytes
    }

    /**
     * Call contract function
     *
     * Selector: `0x6352211e`
     *
     * Signature:
     * ```solidity
     *     function ownerOf(uint256 _tokenId) external returns (address);
     * ```
     */
    public fun ownerOf(_tokenId: BigInteger): ReadFunctionCall<Address> = ReadFunctionCall(this.provider, this.address, FUNCTION_OWNER_OF.encodeCall(listOf<Any>(_tokenId))) {
        val data = FUNCTION_OWNER_OF.decodeResponse(it)
        data[0] as io.ethers.core.types.Address
    }

    /**
     * Call contract function
     *
     * Selector: `0x42842e0e`
     *
     * Signature:
     * ```solidity
     *     function safeTransferFrom(address _from, address _to, uint256 _tokenId) external payable;
     * ```
     */
    public fun safeTransferFrom(
        _from: Address,
        _to: Address,
        _tokenId: BigInteger,
    ): PayableFunctionCall<Unit> = PayableFunctionCall(this.provider, this.address, FUNCTION_SAFE_TRANSFER_FROM.encodeCall(listOf(_from, _to, _tokenId))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0xb88d4fde`
     *
     * Signature:
     * ```solidity
     *     function safeTransferFrom(address _from, address _to, uint256 _tokenId, bytes data) external payable;
     * ```
     */
    public fun safeTransferFrom(
        _from: Address,
        _to: Address,
        _tokenId: BigInteger,
        `data`: Bytes,
    ): PayableFunctionCall<Unit> = PayableFunctionCall(this.provider, this.address, FUNCTION_SAFE_TRANSFER_FROM_1.encodeCall(listOf(_from, _to, _tokenId, `data`))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0xa22cb465`
     *
     * Signature:
     * ```solidity
     *     function setApprovalForAll(address _operator, bool _approved) external;
     * ```
     */
    public fun setApprovalForAll(_operator: Address, _approved: Boolean): FunctionCall<Unit> = FunctionCall(this.provider, this.address, FUNCTION_SET_APPROVAL_FOR_ALL.encodeCall(listOf(_operator, _approved))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x01ffc9a7`
     *
     * Signature:
     * ```solidity
     *     function supportsInterface(bytes4 interfaceID) external returns (bool);
     * ```
     */
    public fun supportsInterface(interfaceID: Bytes): ReadFunctionCall<Boolean> = ReadFunctionCall(this.provider, this.address, FUNCTION_SUPPORTS_INTERFACE.encodeCall(listOf<Any>(interfaceID))) {
        val data = FUNCTION_SUPPORTS_INTERFACE.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    /**
     * Call contract function
     *
     * Selector: `0x95d89b41`
     *
     * Signature:
     * ```solidity
     *     function symbol() external returns (string _symbol);
     * ```
     */
    public fun symbol(): ReadFunctionCall<String> = ReadFunctionCall(this.provider, this.address, FUNCTION_SYMBOL.encodeCall(emptyList())) {
        val data = FUNCTION_SYMBOL.decodeResponse(it)
        data[0] as kotlin.String
    }

    /**
     * Call contract function
     *
     * Selector: `0x4f6ccce7`
     *
     * Signature:
     * ```solidity
     *     function tokenByIndex(uint256 _index) external returns (uint256);
     * ```
     */
    public fun tokenByIndex(_index: BigInteger): ReadFunctionCall<BigInteger> = ReadFunctionCall(this.provider, this.address, FUNCTION_TOKEN_BY_INDEX.encodeCall(listOf<Any>(_index))) {
        val data = FUNCTION_TOKEN_BY_INDEX.decodeResponse(it)
        data[0] as java.math.BigInteger
    }

    /**
     * Call contract function
     *
     * Selector: `0x2f745c59`
     *
     * Signature:
     * ```solidity
     *     function tokenOfOwnerByIndex(address _owner, uint256 _index) external returns (uint256);
     * ```
     */
    public fun tokenOfOwnerByIndex(_owner: Address, _index: BigInteger): ReadFunctionCall<BigInteger> = ReadFunctionCall(this.provider, this.address, FUNCTION_TOKEN_OF_OWNER_BY_INDEX.encodeCall(listOf(_owner, _index))) {
        val data = FUNCTION_TOKEN_OF_OWNER_BY_INDEX.decodeResponse(it)
        data[0] as java.math.BigInteger
    }

    /**
     * Call contract function
     *
     * Selector: `0xc87b56dd`
     *
     * Signature:
     * ```solidity
     *     function tokenURI(uint256 _tokenId) external returns (string);
     * ```
     */
    public fun tokenURI(_tokenId: BigInteger): ReadFunctionCall<String> = ReadFunctionCall(this.provider, this.address, FUNCTION_TOKEN_URI.encodeCall(listOf<Any>(_tokenId))) {
        val data = FUNCTION_TOKEN_URI.decodeResponse(it)
        data[0] as kotlin.String
    }

    /**
     * Call contract function
     *
     * Selector: `0x18160ddd`
     *
     * Signature:
     * ```solidity
     *     function totalSupply() external returns (uint256);
     * ```
     */
    public fun totalSupply(): ReadFunctionCall<BigInteger> = ReadFunctionCall(this.provider, this.address, FUNCTION_TOTAL_SUPPLY.encodeCall(emptyList())) {
        val data = FUNCTION_TOTAL_SUPPLY.decodeResponse(it)
        data[0] as java.math.BigInteger
    }

    /**
     * Call contract function
     *
     * Selector: `0x23b872dd`
     *
     * Signature:
     * ```solidity
     *     function transferFrom(address _from, address _to, uint256 _tokenId) external payable;
     * ```
     */
    public fun transferFrom(
        _from: Address,
        _to: Address,
        _tokenId: BigInteger,
    ): PayableFunctionCall<Unit> = PayableFunctionCall(this.provider, this.address, FUNCTION_TRANSFER_FROM.encodeCall(listOf(_from, _to, _tokenId))) {
    }

    public sealed class Event : ContractEvent

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event Approval(address indexed _owner, address indexed _approved, uint256 indexed _tokenId);
     * ```
     */
    public data class Approval(
        public val _owner: Address,
        public val _approved: Address,
        public val _tokenId: BigInteger,
        override val log: Log,
    ) : ERC721.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Approval
            if (_owner != other._owner) return false
            if (_approved != other._approved) return false
            if (_tokenId != other._tokenId) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = _owner.hashCode()
            result = 31 * result + _approved.hashCode()
            result = 31 * result + _tokenId.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<Approval> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("Approval", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), true)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<Approval> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): Approval? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): Approval = Approval(data[0] as io.ethers.core.types.Address, data[1] as io.ethers.core.types.Address, data[2] as java.math.BigInteger, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event ApprovalForAll(address indexed _owner, address indexed _operator, bool _approved);
     * ```
     */
    public data class ApprovalForAll(
        public val _owner: Address,
        public val _operator: Address,
        public val _approved: Boolean,
        override val log: Log,
    ) : ERC721.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ApprovalForAll
            if (_owner != other._owner) return false
            if (_operator != other._operator) return false
            if (_approved != other._approved) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = _owner.hashCode()
            result = 31 * result + _operator.hashCode()
            result = 31 * result + _approved.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<ApprovalForAll> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("ApprovalForAll", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Bool, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<ApprovalForAll> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): ApprovalForAll? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): ApprovalForAll = ApprovalForAll(data[0] as io.ethers.core.types.Address, data[1] as io.ethers.core.types.Address, data[2] as kotlin.Boolean, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event Transfer(address indexed _from, address indexed _to, uint256 indexed _tokenId);
     * ```
     */
    public data class Transfer(
        public val _from: Address,
        public val _to: Address,
        public val _tokenId: BigInteger,
        override val log: Log,
    ) : ERC721.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Transfer
            if (_from != other._from) return false
            if (_to != other._to) return false
            if (_tokenId != other._tokenId) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = _from.hashCode()
            result = 31 * result + _to.hashCode()
            result = 31 * result + _tokenId.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<Transfer> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("Transfer", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), true)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<Transfer> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): Transfer? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): Transfer = Transfer(data[0] as io.ethers.core.types.Address, data[1] as io.ethers.core.types.Address, data[2] as java.math.BigInteger, log)
        }
    }

    public companion object {
        @JvmField
        public val EVENTS: List<EventFactory<out ERC721.Event>> =
            listOf(Approval, ApprovalForAll, Transfer)

        @JvmField
        public val FUNCTION_SYMBOL: AbiFunction =
            AbiFunction("symbol", listOf(), listOf(AbiType.String))

        @JvmField
        public val FUNCTION_IS_APPROVED_FOR_ALL: AbiFunction =
            AbiFunction("isApprovedForAll", listOf(AbiType.Address, AbiType.Address), listOf(AbiType.Bool))

        @JvmField
        public val FUNCTION_OWNER_OF: AbiFunction =
            AbiFunction("ownerOf", listOf(AbiType.UInt(256)), listOf(AbiType.Address))

        @JvmField
        public val FUNCTION_BALANCE_OF: AbiFunction =
            AbiFunction("balanceOf", listOf(AbiType.Address), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_TOKEN_OF_OWNER_BY_INDEX: AbiFunction =
            AbiFunction("tokenOfOwnerByIndex", listOf(AbiType.Address, AbiType.UInt(256)), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_TOTAL_SUPPLY: AbiFunction =
            AbiFunction("totalSupply", listOf(), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_SET_APPROVAL_FOR_ALL: AbiFunction =
            AbiFunction("setApprovalForAll", listOf(AbiType.Address, AbiType.Bool), listOf())

        @JvmField
        public val FUNCTION_APPROVE: AbiFunction =
            AbiFunction("approve", listOf(AbiType.Address, AbiType.UInt(256)), listOf())

        @JvmField
        public val FUNCTION_GET_APPROVED: AbiFunction =
            AbiFunction("getApproved", listOf(AbiType.UInt(256)), listOf(AbiType.Address))

        @JvmField
        public val FUNCTION_ON_ERC721_RECEIVED: AbiFunction =
            AbiFunction("onERC721Received", listOf(AbiType.Address, AbiType.Address, AbiType.UInt(256), AbiType.Bytes), listOf(AbiType.FixedBytes(4)))

        @JvmField
        public val FUNCTION_NAME: AbiFunction =
            AbiFunction("name", listOf(), listOf(AbiType.String))

        @JvmField
        public val FUNCTION_TOKEN_BY_INDEX: AbiFunction =
            AbiFunction("tokenByIndex", listOf(AbiType.UInt(256)), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_TOKEN_URI: AbiFunction =
            AbiFunction("tokenURI", listOf(AbiType.UInt(256)), listOf(AbiType.String))

        @JvmField
        public val FUNCTION_SAFE_TRANSFER_FROM: AbiFunction =
            AbiFunction("safeTransferFrom", listOf(AbiType.Address, AbiType.Address, AbiType.UInt(256)), listOf())

        @JvmField
        public val FUNCTION_SAFE_TRANSFER_FROM_1: AbiFunction =
            AbiFunction("safeTransferFrom", listOf(AbiType.Address, AbiType.Address, AbiType.UInt(256), AbiType.Bytes), listOf())

        @JvmField
        public val FUNCTION_TRANSFER_FROM: AbiFunction =
            AbiFunction("transferFrom", listOf(AbiType.Address, AbiType.Address, AbiType.UInt(256)), listOf())

        @JvmField
        public val FUNCTION_SUPPORTS_INTERFACE: AbiFunction =
            AbiFunction("supportsInterface", listOf(AbiType.FixedBytes(4)), listOf(AbiType.Bool))

        @JvmStatic
        public fun decodeEvent(log: Log): ERC721.Event? {
            for (event in EVENTS) {
                return event.decode(log) ?: continue
            }
            return null
        }
    }
}
