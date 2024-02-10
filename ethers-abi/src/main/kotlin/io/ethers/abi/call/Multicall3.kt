package io.ethers.abi.call

import io.ethers.abi.AbiContract
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.StructFactory
import io.ethers.core.Jackson
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.providers.middleware.Middleware
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

class Multicall3(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    fun <T> aggregate(vararg calls: Multicall3Aggregatable<out T>): Multicall3AggregateCall<T> {
        val ret = Multicall3AggregateCall<T>(provider, address)
        for (call in calls) {
            ret.addCall(call)
        }
        return ret
    }

    fun aggregate(vararg calls: Call): PayableFunctionCall<AggregateResult> =
        PayableFunctionCall(
            this.provider,
            this.address,
            FUNCTION_AGGREGATE.encodeCall(arrayOf(calls)),
        ) {
            val data = FUNCTION_AGGREGATE.decodeResponse(it)
            AggregateResult(data[0] as BigInteger, data[1] as Array<Bytes>)
        }

    fun aggregate3(vararg calls: Call3): PayableFunctionCall<Array<Result>> =
        PayableFunctionCall(
            this.provider,
            this.address,
            FUNCTION_AGGREGATE3.encodeCall(arrayOf(calls)),
        ) {
            val data = FUNCTION_AGGREGATE3.decodeResponse(it)
            data[0] as Array<Result>
        }

    fun aggregate3Value(vararg calls: Call3Value): PayableFunctionCall<Array<Result>> =
        PayableFunctionCall(
            this.provider,
            this.address,
            FUNCTION_AGGREGATE3_VALUE.encodeCall(arrayOf(calls)),
        ) {
            val data = FUNCTION_AGGREGATE3_VALUE.decodeResponse(it)
            data[0] as Array<Result>
        }

    fun blockAndAggregate(vararg calls: Call): PayableFunctionCall<BlockAndAggregateResult> =
        PayableFunctionCall(
            this.provider,
            this.address,
            FUNCTION_BLOCK_AND_AGGREGATE.encodeCall(arrayOf(calls)),
        ) {
            val data = FUNCTION_BLOCK_AND_AGGREGATE.decodeResponse(it)
            BlockAndAggregateResult(
                data[0] as BigInteger,
                data[1] as Bytes,
                data[2] as Array<Result>,
            )
        }

    fun getBasefee(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_BASEFEE.encodeCall(emptyArray()),
    ) {
        val data = FUNCTION_GET_BASEFEE.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getBlockHash(blockNumber: BigInteger): ReadFunctionCall<Bytes> =
        ReadFunctionCall(
            this.provider,
            this.address,
            FUNCTION_GET_BLOCK_HASH.encodeCall(arrayOf(blockNumber)),
        ) {
            val data = FUNCTION_GET_BLOCK_HASH.decodeResponse(it)
            data[0] as Bytes
        }

    fun getBlockNumber(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_BLOCK_NUMBER.encodeCall(emptyArray()),
    ) {
        val data = FUNCTION_GET_BLOCK_NUMBER.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getChainId(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_CHAIN_ID.encodeCall(emptyArray()),
    ) {
        val data = FUNCTION_GET_CHAIN_ID.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getCurrentBlockCoinbase(): ReadFunctionCall<Address> =
        ReadFunctionCall(
            this.provider,
            this.address,
            FUNCTION_GET_CURRENT_BLOCK_COINBASE.encodeCall(emptyArray()),
        ) {
            val data = FUNCTION_GET_CURRENT_BLOCK_COINBASE.decodeResponse(it)
            data[0] as Address
        }

    fun getCurrentBlockDifficulty(): ReadFunctionCall<BigInteger> =
        ReadFunctionCall(
            this.provider,
            this.address,
            FUNCTION_GET_CURRENT_BLOCK_DIFFICULTY.encodeCall(emptyArray()),
        ) {
            val data = FUNCTION_GET_CURRENT_BLOCK_DIFFICULTY.decodeResponse(it)
            data[0] as BigInteger
        }

    fun getCurrentBlockGasLimit(): ReadFunctionCall<BigInteger> =
        ReadFunctionCall(
            this.provider,
            this.address,
            FUNCTION_GET_CURRENT_BLOCK_GAS_LIMIT.encodeCall(emptyArray()),
        ) {
            val data = FUNCTION_GET_CURRENT_BLOCK_GAS_LIMIT.decodeResponse(it)
            data[0] as BigInteger
        }

    fun getCurrentBlockTimestamp(): ReadFunctionCall<BigInteger> =
        ReadFunctionCall(
            this.provider,
            this.address,
            FUNCTION_GET_CURRENT_BLOCK_TIMESTAMP.encodeCall(emptyArray()),
        ) {
            val data = FUNCTION_GET_CURRENT_BLOCK_TIMESTAMP.decodeResponse(it)
            data[0] as BigInteger
        }

    fun getEthBalance(addr: Address): ReadFunctionCall<BigInteger> =
        ReadFunctionCall(
            this.provider,
            this.address,
            FUNCTION_GET_ETH_BALANCE.encodeCall(arrayOf(addr)),
        ) {
            val data = FUNCTION_GET_ETH_BALANCE.decodeResponse(it)
            data[0] as BigInteger
        }

    fun getLastBlockHash(): ReadFunctionCall<Bytes> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_LAST_BLOCK_HASH.encodeCall(emptyArray()),
    ) {
        val data = FUNCTION_GET_LAST_BLOCK_HASH.decodeResponse(it)
        data[0] as Bytes
    }

    fun tryAggregate(requireSuccess: Boolean, vararg calls: Call): PayableFunctionCall<Array<Result>> =
        PayableFunctionCall(
            this.provider,
            this.address,
            FUNCTION_TRY_AGGREGATE.encodeCall(arrayOf(requireSuccess, calls)),
        ) {
            val data = FUNCTION_TRY_AGGREGATE.decodeResponse(it)
            data[0] as Array<Result>
        }

    fun tryBlockAndAggregate(
        requireSuccess: Boolean,
        vararg calls: Call,
    ): PayableFunctionCall<TryBlockAndAggregateResult> = PayableFunctionCall(
        this.provider,
        this.address,
        FUNCTION_TRY_BLOCK_AND_AGGREGATE.encodeCall(arrayOf(requireSuccess, calls)),
    ) {
        val data = FUNCTION_TRY_BLOCK_AND_AGGREGATE.decodeResponse(it)
        TryBlockAndAggregateResult(
            data[0] as BigInteger,
            data[1] as Bytes,
            data[2] as Array<Result>,
        )
    }

    data class BlockAndAggregateResult(
        val blockNumber: BigInteger,
        val blockHash: Bytes,
        val returnData: Array<Result>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BlockAndAggregateResult
            if (blockNumber != other.blockNumber) return false
            if (blockHash != other.blockHash) return false
            if (!returnData.contentEquals(other.returnData)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = blockNumber.hashCode()
            result = 31 * result + blockHash.hashCode()
            result = 31 * result + returnData.contentHashCode()
            return result
        }
    }

    data class AggregateResult(
        val blockNumber: BigInteger,
        val returnData: Array<Bytes>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AggregateResult
            if (blockNumber != other.blockNumber) return false
            if (!returnData.contentEquals(other.returnData)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = blockNumber.hashCode()
            result = 31 * result + returnData.contentHashCode()
            return result
        }
    }

    data class TryBlockAndAggregateResult(
        val blockNumber: BigInteger,
        val blockHash: Bytes,
        val returnData: Array<Result>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TryBlockAndAggregateResult
            if (blockNumber != other.blockNumber) return false
            if (blockHash != other.blockHash) return false
            if (!returnData.contentEquals(other.returnData)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = blockNumber.hashCode()
            result = 31 * result + blockHash.hashCode()
            result = 31 * result + returnData.contentHashCode()
            return result
        }
    }

    data class Call(
        val target: Address,
        val callData: Bytes,
    ) : ContractStruct {
        override val tuple: Array<Any> = arrayOf(target, callData)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Call
            if (target != other.target) return false
            if (callData != other.callData) return false
            return true
        }

        override fun hashCode(): Int {
            var result = target.hashCode()
            result = 31 * result + callData.hashCode()
            return result
        }

        companion object : StructFactory<Call> {
            @JvmStatic
            override fun fromTuple(`data`: Array<Any>): Call = Call(data[0] as Address, data[1] as Bytes)
        }
    }

    data class Call3(
        val target: Address,
        val allowFailure: Boolean,
        val callData: Bytes,
    ) : ContractStruct {
        override val tuple: Array<Any> = arrayOf(target, allowFailure, callData)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Call3
            if (target != other.target) return false
            if (allowFailure != other.allowFailure) return false
            if (callData != other.callData) return false
            return true
        }

        override fun hashCode(): Int {
            var result = target.hashCode()
            result = 31 * result + allowFailure.hashCode()
            result = 31 * result + callData.hashCode()
            return result
        }

        companion object : StructFactory<Call3> {
            @JvmStatic
            override fun fromTuple(`data`: Array<Any>): Call3 = Call3(
                data[0] as Address,
                data[1] as Boolean,
                data[2] as Bytes,
            )
        }
    }

    data class Call3Value(
        val target: Address,
        val allowFailure: Boolean,
        val `value`: BigInteger,
        val callData: Bytes,
    ) : ContractStruct {
        override val tuple: Array<Any> = arrayOf(target, allowFailure, value, callData)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Call3Value
            if (target != other.target) return false
            if (allowFailure != other.allowFailure) return false
            if (value != other.value) return false
            if (callData != other.callData) return false
            return true
        }

        override fun hashCode(): Int {
            var result = target.hashCode()
            result = 31 * result + allowFailure.hashCode()
            result = 31 * result + value.hashCode()
            result = 31 * result + callData.hashCode()
            return result
        }

        companion object : StructFactory<Call3Value> {
            @JvmStatic
            override fun fromTuple(`data`: Array<Any>): Call3Value = Call3Value(
                data[0] as Address,
                data[1] as Boolean,
                data[2] as BigInteger,
                data[3] as Bytes,
            )
        }
    }

    data class Result(
        val success: Boolean,
        val returnData: Bytes,
    ) : ContractStruct {
        override val tuple: Array<Any> = arrayOf(success, returnData)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Result
            if (success != other.success) return false
            if (returnData != other.returnData) return false
            return true
        }

        override fun hashCode(): Int {
            var result = success.hashCode()
            result = 31 * result + returnData.hashCode()
            return result
        }

        companion object : StructFactory<Result> {
            @JvmStatic
            override fun fromTuple(`data`: Array<Any>): Result = Result(data[0] as Boolean, data[1] as Bytes)
        }
    }

    companion object {
        private val INSTANCE_PER_CHAIN_ID = ConcurrentHashMap<Long, Multicall3>()
        private val DEFAULT_ADDRESS = Address("0xcA11bde05977b3631167028862bE2a173976CA11")
        private val DEPLOYED_BYTECODE = Bytes(
            "0x6080604052600436106100f35760003560e01c80634d2301cc1161008a578063a8b0574e11610059578063a8b0574e1461025a578063bce38bd714610275578063c3077fa914610288578063ee82ac5e1461029b57600080fd5b80634d2301cc146101ec57806372425d9d1461022157806382ad56cb1461023457806386d516e81461024757600080fd5b80633408e470116100c65780633408e47014610191578063399542e9146101a45780633e64a696146101c657806342cbb15c146101d957600080fd5b80630f28c97d146100f8578063174dea711461011a578063252dba421461013a57806327e86d6e1461015b575b600080fd5b34801561010457600080fd5b50425b6040519081526020015b60405180910390f35b61012d610128366004610a85565b6102ba565b6040516101119190610bbe565b61014d610148366004610a85565b6104ef565b604051610111929190610bd8565b34801561016757600080fd5b50437fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0140610107565b34801561019d57600080fd5b5046610107565b6101b76101b2366004610c60565b610690565b60405161011193929190610cba565b3480156101d257600080fd5b5048610107565b3480156101e557600080fd5b5043610107565b3480156101f857600080fd5b50610107610207366004610ce2565b73ffffffffffffffffffffffffffffffffffffffff163190565b34801561022d57600080fd5b5044610107565b61012d610242366004610a85565b6106ab565b34801561025357600080fd5b5045610107565b34801561026657600080fd5b50604051418152602001610111565b61012d610283366004610c60565b61085a565b6101b7610296366004610a85565b610a1a565b3480156102a757600080fd5b506101076102b6366004610d18565b4090565b60606000828067ffffffffffffffff8111156102d8576102d8610d31565b60405190808252806020026020018201604052801561031e57816020015b6040805180820190915260008152606060208201528152602001906001900390816102f65790505b5092503660005b8281101561047757600085828151811061034157610341610d60565b6020026020010151905087878381811061035d5761035d610d60565b905060200281019061036f9190610d8f565b6040810135958601959093506103886020850185610ce2565b73ffffffffffffffffffffffffffffffffffffffff16816103ac6060870187610dcd565b6040516103ba929190610e32565b60006040518083038185875af1925050503d80600081146103f7576040519150601f19603f3d011682016040523d82523d6000602084013e6103fc565b606091505b50602080850191909152901515808452908501351761046d577f08c379a000000000000000000000000000000000000000000000000000000000600052602060045260176024527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060445260846000fd5b5050600101610325565b508234146104e6576040517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601a60248201527f4d756c746963616c6c333a2076616c7565206d69736d6174636800000000000060448201526064015b60405180910390fd5b50505092915050565b436060828067ffffffffffffffff81111561050c5761050c610d31565b60405190808252806020026020018201604052801561053f57816020015b606081526020019060019003908161052a5790505b5091503660005b8281101561068657600087878381811061056257610562610d60565b90506020028101906105749190610e42565b92506105836020840184610ce2565b73ffffffffffffffffffffffffffffffffffffffff166105a66020850185610dcd565b6040516105b4929190610e32565b6000604051808303816000865af19150503d80600081146105f1576040519150601f19603f3d011682016040523d82523d6000602084013e6105f6565b606091505b5086848151811061060957610609610d60565b602090810291909101015290508061067d576040517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601760248201527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060448201526064016104dd565b50600101610546565b5050509250929050565b43804060606106a086868661085a565b905093509350939050565b6060818067ffffffffffffffff8111156106c7576106c7610d31565b60405190808252806020026020018201604052801561070d57816020015b6040805180820190915260008152606060208201528152602001906001900390816106e55790505b5091503660005b828110156104e657600084828151811061073057610730610d60565b6020026020010151905086868381811061074c5761074c610d60565b905060200281019061075e9190610e76565b925061076d6020840184610ce2565b73ffffffffffffffffffffffffffffffffffffffff166107906040850185610dcd565b60405161079e929190610e32565b6000604051808303816000865af19150503d80600081146107db576040519150601f19603f3d011682016040523d82523d6000602084013e6107e0565b606091505b506020808401919091529015158083529084013517610851577f08c379a000000000000000000000000000000000000000000000000000000000600052602060045260176024527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060445260646000fd5b50600101610714565b6060818067ffffffffffffffff81111561087657610876610d31565b6040519080825280602002602001820160405280156108bc57816020015b6040805180820190915260008152606060208201528152602001906001900390816108945790505b5091503660005b82811015610a105760008482815181106108df576108df610d60565b602002602001015190508686838181106108fb576108fb610d60565b905060200281019061090d9190610e42565b925061091c6020840184610ce2565b73ffffffffffffffffffffffffffffffffffffffff1661093f6020850185610dcd565b60405161094d929190610e32565b6000604051808303816000865af19150503d806000811461098a576040519150601f19603f3d011682016040523d82523d6000602084013e61098f565b606091505b506020830152151581528715610a07578051610a07576040517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601760248201527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060448201526064016104dd565b506001016108c3565b5050509392505050565b6000806060610a2b60018686610690565b919790965090945092505050565b60008083601f840112610a4b57600080fd5b50813567ffffffffffffffff811115610a6357600080fd5b6020830191508360208260051b8501011115610a7e57600080fd5b9250929050565b60008060208385031215610a9857600080fd5b823567ffffffffffffffff811115610aaf57600080fd5b610abb85828601610a39565b90969095509350505050565b6000815180845260005b81811015610aed57602081850181015186830182015201610ad1565b81811115610aff576000602083870101525b50601f017fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe0169290920160200192915050565b600082825180855260208086019550808260051b84010181860160005b84811015610bb1578583037fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe001895281518051151584528401516040858501819052610b9d81860183610ac7565b9a86019a9450505090830190600101610b4f565b5090979650505050505050565b602081526000610bd16020830184610b32565b9392505050565b600060408201848352602060408185015281855180845260608601915060608160051b870101935082870160005b82811015610c52577fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffa0888703018452610c40868351610ac7565b95509284019290840190600101610c06565b509398975050505050505050565b600080600060408486031215610c7557600080fd5b83358015158114610c8557600080fd5b9250602084013567ffffffffffffffff811115610ca157600080fd5b610cad86828701610a39565b9497909650939450505050565b838152826020820152606060408201526000610cd96060830184610b32565b95945050505050565b600060208284031215610cf457600080fd5b813573ffffffffffffffffffffffffffffffffffffffff81168114610bd157600080fd5b600060208284031215610d2a57600080fd5b5035919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052603260045260246000fd5b600082357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff81833603018112610dc357600080fd5b9190910192915050565b60008083357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe1843603018112610e0257600080fd5b83018035915067ffffffffffffffff821115610e1d57600080fd5b602001915036819003821315610a7e57600080fd5b8183823760009101908152919050565b600082357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffc1833603018112610dc357600080fd5b600082357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffa1833603018112610dc357600080fdfea2646970667358221220449a18ce35be1adaedc9fa8ac86170e8c0f9b35e4a63d7b367123a4743501ae564736f6c634300080c0033",
        )

        private val DEPLOYMENT_PER_CHAIN_ID by lazy {
            val ret = HashMap<Long, MulticallDeployment>()
            val reader = Jackson.MAPPER.readerFor(MulticallDeployment::class.java)
            val stream = Multicall3::class.java.getResourceAsStream("/multicall3-deployments.json")

            val deployments = reader.readValues<MulticallDeployment>(stream)
            deployments.forEach { ret[it.chainId] = it }

            ret
        }

        @JvmStatic
        fun getAddressForChainId(chainId: Long): Address {
            return DEPLOYMENT_PER_CHAIN_ID[chainId]?.address ?: DEFAULT_ADDRESS
        }

        @JvmStatic
        fun getInstance(provider: Middleware): Multicall3 {
            return INSTANCE_PER_CHAIN_ID.computeIfAbsent(provider.chainId) {
                Multicall3(provider, getAddressForChainId(provider.chainId))
            }
        }

        /**
         * Create and return a new [Multicall3AggregateCall], which can be used to aggregate multiple
         * [Multicall3Aggregatable] calls into a single [Multicall3] function call.
         * */
        @JvmStatic
        fun <T> newAggregation(provider: Middleware): Multicall3AggregateCall<T> {
            return Multicall3AggregateCall(provider, getAddressForChainId(provider.chainId))
        }

        private data class MulticallDeployment(
            val name: String,
            val chainId: Long,
            val url: String,
            val address: Address? = null,
        )

        /**
         * Get state override with deployed bytecode for the Multicall3 contract on [DEFAULT_ADDRESS]. Useful if
         * calling on blocks where the contract has not been deployed yet.
         * */
        @JvmField
        val STATE_OVERRIDE = mapOf(DEFAULT_ADDRESS to AccountOverride().code(DEPLOYED_BYTECODE))

        @JvmField
        val FUNCTION_AGGREGATE3: AbiFunction = AbiFunction(
            "aggregate3",
            listOf(AbiType.Array(AbiType.Tuple.struct(Call3::class, AbiType.Address, AbiType.Bool, AbiType.Bytes))),
            listOf(AbiType.Array(AbiType.Tuple.struct(Result::class, AbiType.Bool, AbiType.Bytes))),
        )

        @JvmField
        val FUNCTION_BLOCK_AND_AGGREGATE: AbiFunction = AbiFunction(
            "blockAndAggregate",
            listOf(AbiType.Array(AbiType.Tuple.struct(Call::class, AbiType.Address, AbiType.Bytes))),
            listOf(
                AbiType.UInt(256),
                AbiType.FixedBytes(32),
                AbiType.Array(AbiType.Tuple.struct(Result::class, AbiType.Bool, AbiType.Bytes)),
            ),
        )

        @JvmField
        val FUNCTION_AGGREGATE3_VALUE: AbiFunction = AbiFunction(
            "aggregate3Value",
            listOf(
                AbiType.Array(
                    AbiType.Tuple.struct(
                        Call3Value::class,
                        AbiType.Address,
                        AbiType.Bool,
                        AbiType.UInt(256),
                        AbiType.Bytes,
                    ),
                ),
            ),
            listOf(AbiType.Array(AbiType.Tuple.struct(Result::class, AbiType.Bool, AbiType.Bytes))),
        )

        @JvmField
        val FUNCTION_GET_BASEFEE: AbiFunction = AbiFunction(
            "getBasefee",
            listOf(),
            listOf(AbiType.UInt(256)),
        )

        @JvmField
        val FUNCTION_AGGREGATE: AbiFunction = AbiFunction(
            "aggregate",
            listOf(AbiType.Array(AbiType.Tuple.struct(Call::class, AbiType.Address, AbiType.Bytes))),
            listOf(AbiType.UInt(256), AbiType.Array(AbiType.Bytes)),
        )

        @JvmField
        val FUNCTION_GET_CURRENT_BLOCK_DIFFICULTY: AbiFunction = AbiFunction(
            "getCurrentBlockDifficulty",
            listOf(),
            listOf(AbiType.UInt(256)),
        )

        @JvmField
        val FUNCTION_GET_CURRENT_BLOCK_COINBASE: AbiFunction = AbiFunction(
            "getCurrentBlockCoinbase",
            listOf(),
            listOf(AbiType.Address),
        )

        @JvmField
        val FUNCTION_GET_ETH_BALANCE: AbiFunction = AbiFunction(
            "getEthBalance",
            listOf(AbiType.Address),
            listOf(AbiType.UInt(256)),
        )

        @JvmField
        val FUNCTION_GET_BLOCK_NUMBER: AbiFunction = AbiFunction(
            "getBlockNumber",
            listOf(),
            listOf(AbiType.UInt(256)),
        )

        @JvmField
        val FUNCTION_TRY_AGGREGATE: AbiFunction = AbiFunction(
            "tryAggregate",
            listOf(AbiType.Bool, AbiType.Array(AbiType.Tuple.struct(Call::class, AbiType.Address, AbiType.Bytes))),
            listOf(AbiType.Array(AbiType.Tuple.struct(Result::class, AbiType.Bool, AbiType.Bytes))),
        )

        @JvmField
        val FUNCTION_TRY_BLOCK_AND_AGGREGATE: AbiFunction =
            AbiFunction(
                "tryBlockAndAggregate",
                listOf(AbiType.Bool, AbiType.Array(AbiType.Tuple.struct(Call::class, AbiType.Address, AbiType.Bytes))),
                listOf(
                    AbiType.UInt(256),
                    AbiType.FixedBytes(32),
                    AbiType.Array(AbiType.Tuple.struct(Result::class, AbiType.Bool, AbiType.Bytes)),
                ),
            )

        @JvmField
        val FUNCTION_GET_CHAIN_ID: AbiFunction = AbiFunction(
            "getChainId",
            listOf(),
            listOf(AbiType.UInt(256)),
        )

        @JvmField
        val FUNCTION_GET_CURRENT_BLOCK_TIMESTAMP: AbiFunction = AbiFunction(
            "getCurrentBlockTimestamp",
            listOf(),
            listOf(AbiType.UInt(256)),
        )

        @JvmField
        val FUNCTION_GET_BLOCK_HASH: AbiFunction = AbiFunction(
            "getBlockHash",
            listOf(AbiType.UInt(256)),
            listOf(AbiType.FixedBytes(32)),
        )

        @JvmField
        val FUNCTION_GET_LAST_BLOCK_HASH: AbiFunction = AbiFunction(
            "getLastBlockHash",
            listOf(),
            listOf(AbiType.FixedBytes(32)),
        )

        @JvmField
        val FUNCTION_GET_CURRENT_BLOCK_GAS_LIMIT: AbiFunction = AbiFunction(
            "getCurrentBlockGasLimit",
            listOf(),
            listOf(AbiType.UInt(256)),
        )
    }
}
