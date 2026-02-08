package io.ethers.abi.call

import io.ethers.abi.AbiContract
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.StructFactory
import io.ethers.abi.call.Multicall3.Companion.DEFAULT_ADDRESS
import io.ethers.abi.call.Multicall3.Companion.STATE_OVERRIDE
import io.ethers.abi.error.ContractError
import io.ethers.abi.error.RevertError
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.IntoCallRequest
import io.ethers.providers.middleware.Middleware
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import java.math.BigInteger

private typealias EthersResult<T, E> = Result<T, E>

/**
 * [Multicall3](https://www.multicall3.com/) implementation for executing batch contracts calls on multiple EVM-based
 * chains, via single contract call.
 *
 * Instances of this class are chain-specific, and should be obtained via [Multicall3.getInstance], which returns a
 * cached instance. If querying on chains with no [Multicall3] contract deployed - or on blocks before it was
 * deployed -, you can use the [STATE_OVERRIDE] when calling/tracing to "deploy" it for the duration of the calls
 * on [DEFAULT_ADDRESS].
 *
 * It can be used to aggregate function calls of other abi-generated contracts as long as they implement the
 * [Aggregatable] interface. Aggregate calls can also be nested, i.e. an aggregate call can contain other
 * aggregate calls.
 *
 * Example usage:
 * ```kotlin
 *    // two nested aggregate calls, and a regular one
 *    val agg = Multicall3.aggregate(
 *        ticks.map { bitmap.flipTick(it.toBigInteger(), spacing.toBigInteger()) }.aggregate(),
 *        ticks.map { bitmap.isTickSet(it.toBigInteger(), spacing.toBigInteger()) }.aggregate(),
 *        router.quote("100000".toBigInteger(), "12415235134".toBigInteger(), "982341485157841".toBigInteger())
 *    )
 *
 *    // execute the call
 *    val response = agg.call(BlockId.LATEST).sendAwait().unwrap()
 *
 *    // nested aggregate calls are returned as an instance AggregationResult
 *    val areTicksSet = response.getAsAggregation<Boolean>(1)
 *    val swapResult = response.getAs<BigInteger>(2)
 * ```
 * */
class Multicall3(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    /**
     * Contract call that aggregates multiple [Aggregatable] calls into a single [Multicall3] contract call.
     *
     * **IMPORTANT**: From the call, only [Aggregatable.to], [Aggregatable.value] and [Aggregatable.data] parameters
     * are used. All others are ignored.
     *
     * The calls will be aggregated using as few calldata as possible, depending on the calls' properties. One of the
     * following aggregation functions will be used:
     * - [aggregate3Value], if any call is payable with non-zero value set,
     * - [aggregate3], if calls have mixed failure conditions (i.e. some allow failure, some don't),
     * - [tryAggregate], if all calls have the same failure condition, and are not payable or with zero value.
     *
     * Aggregate calls can be nested, i.e. an aggregate call can contain other aggregate calls.
     * */
    @Suppress("UNCHECKED_CAST")
    fun <T> aggregateCalls(vararg calls: Aggregatable<out T>): FunctionCall<AggregationResult<T>> {
        return aggregateCalls(calls.toList())
    }

    /**
     * Contract call that aggregates multiple [Aggregatable] calls into a single [Multicall3] contract call.
     *
     * **IMPORTANT**: From the call, only [Aggregatable.to], [Aggregatable.value] and [Aggregatable.data] parameters
     * are used. All others are ignored.
     *
     * The calls will be aggregated using as few calldata as possible, depending on the calls' properties. One of the
     * following aggregation functions will be used:
     * - [aggregate3Value], if any call is payable with non-zero value set,
     * - [aggregate3], if calls have mixed failure conditions (i.e. some allow failure, some don't),
     * - [tryAggregate], if all calls have the same failure condition, and are not payable or with zero value.
     *
     * Aggregate calls can be nested, i.e. an aggregate call can contain other aggregate calls.
     * */
    @Suppress("UNCHECKED_CAST")
    fun <T> aggregateCalls(calls: List<Aggregatable<out T>>): FunctionCall<AggregationResult<T>> {
        return calls.withDataAndValue { data, value ->
            FunctionCall(this.provider, this.address, value, data) {
                val decoded = FUNCTION_AGGREGATE3_VALUE.decodeResponse(it)[0] as List<Result>

                val ret = arrayOfNulls<EthersResult<*, ContractError>>(decoded.size)
                for (i in decoded.indices) {
                    val request = calls[i]
                    val callResult = decoded[i]

                    ret[i] = handleRequestResult(request, callResult)
                }

                AggregationResult(ret as Array<EthersResult<T, ContractError>>)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun aggregate(vararg calls: Call): PayableFunctionCall<AggregateResult> = PayableFunctionCall(
        this.provider,
        this.address,
        FUNCTION_AGGREGATE.encodeCall(listOf(calls)),
    ) {
        val data = FUNCTION_AGGREGATE.decodeResponse(it)
        AggregateResult(data[0] as BigInteger, data[1] as List<Bytes>)
    }

    @Suppress("UNCHECKED_CAST")
    fun aggregate3(vararg calls: Call3): PayableFunctionCall<List<Result>> = PayableFunctionCall(
        this.provider,
        this.address,
        FUNCTION_AGGREGATE3.encodeCall(listOf(calls)),
    ) {
        val data = FUNCTION_AGGREGATE3.decodeResponse(it)
        data[0] as List<Result>
    }

    @Suppress("UNCHECKED_CAST")
    fun aggregate3Value(vararg calls: Call3Value): PayableFunctionCall<List<Result>> = PayableFunctionCall(
        this.provider,
        this.address,
        FUNCTION_AGGREGATE3_VALUE.encodeCall(listOf(calls)),
    ) {
        val data = FUNCTION_AGGREGATE3_VALUE.decodeResponse(it)
        data[0] as List<Result>
    }

    @Suppress("UNCHECKED_CAST")
    fun blockAndAggregate(vararg calls: Call): PayableFunctionCall<BlockAndAggregateResult> = PayableFunctionCall(
        this.provider,
        this.address,
        FUNCTION_BLOCK_AND_AGGREGATE.encodeCall(listOf(calls)),
    ) {
        val data = FUNCTION_BLOCK_AND_AGGREGATE.decodeResponse(it)
        BlockAndAggregateResult(
            data[0] as BigInteger,
            data[1] as Bytes,
            data[2] as List<Result>,
        )
    }

    fun getBasefee(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_BASEFEE.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_BASEFEE.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getBlockHash(blockNumber: BigInteger): ReadFunctionCall<Bytes> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_BLOCK_HASH.encodeCall(listOf(blockNumber)),
    ) {
        val data = FUNCTION_GET_BLOCK_HASH.decodeResponse(it)
        data[0] as Bytes
    }

    fun getBlockNumber(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_BLOCK_NUMBER.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_BLOCK_NUMBER.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getChainId(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_CHAIN_ID.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_CHAIN_ID.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getCurrentBlockCoinbase(): ReadFunctionCall<Address> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_CURRENT_BLOCK_COINBASE.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_CURRENT_BLOCK_COINBASE.decodeResponse(it)
        data[0] as Address
    }

    fun getCurrentBlockDifficulty(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_CURRENT_BLOCK_DIFFICULTY.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_CURRENT_BLOCK_DIFFICULTY.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getCurrentBlockGasLimit(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_CURRENT_BLOCK_GAS_LIMIT.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_CURRENT_BLOCK_GAS_LIMIT.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getCurrentBlockTimestamp(): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_CURRENT_BLOCK_TIMESTAMP.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_CURRENT_BLOCK_TIMESTAMP.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getEthBalance(addr: Address): ReadFunctionCall<BigInteger> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_ETH_BALANCE.encodeCall(listOf(addr)),
    ) {
        val data = FUNCTION_GET_ETH_BALANCE.decodeResponse(it)
        data[0] as BigInteger
    }

    fun getLastBlockHash(): ReadFunctionCall<Bytes> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_GET_LAST_BLOCK_HASH.encodeCall(emptyList()),
    ) {
        val data = FUNCTION_GET_LAST_BLOCK_HASH.decodeResponse(it)
        data[0] as Bytes
    }

    @Suppress("UNCHECKED_CAST")
    fun tryAggregate(requireSuccess: Boolean, vararg calls: Call): PayableFunctionCall<List<Result>> = PayableFunctionCall(
        this.provider,
        this.address,
        FUNCTION_TRY_AGGREGATE.encodeCall(listOf(requireSuccess, calls)),
    ) {
        val data = FUNCTION_TRY_AGGREGATE.decodeResponse(it)
        data[0] as List<Result>
    }

    @Suppress("UNCHECKED_CAST")
    fun tryBlockAndAggregate(
        requireSuccess: Boolean,
        vararg calls: Call,
    ): PayableFunctionCall<TryBlockAndAggregateResult> = PayableFunctionCall(
        this.provider,
        this.address,
        FUNCTION_TRY_BLOCK_AND_AGGREGATE.encodeCall(listOf(requireSuccess, calls)),
    ) {
        val data = FUNCTION_TRY_BLOCK_AND_AGGREGATE.decodeResponse(it)
        TryBlockAndAggregateResult(
            data[0] as BigInteger,
            data[1] as Bytes,
            data[2] as List<Result>,
        )
    }

    private fun <T> handleRequestResult(
        request: Aggregatable<T>,
        result: Result,
    ): EthersResult<T, ContractError> {
        val res = if (result.success) {
            request.decodeCallResult(result.returnData)
        } else {
            failure(tryDecodingCallRevert(result.returnData))
        }

        return res
    }

    private fun tryDecodingCallRevert(err: Bytes): ContractError {
        val contractError = ContractError.getOrNull(err)
        if (contractError != null) {
            return contractError
        }

        // if we can't decode the error, just return the raw bytes as hex
        return RevertError(err.toString())
    }

    data class BlockAndAggregateResult(
        val blockNumber: BigInteger,
        val blockHash: Bytes,
        val returnData: List<Result>,
    )

    data class AggregateResult(
        val blockNumber: BigInteger,
        val returnData: List<Bytes>,
    )

    data class TryBlockAndAggregateResult(
        val blockNumber: BigInteger,
        val blockHash: Bytes,
        val returnData: List<Result>,
    )

    data class Call(
        val target: Address,
        val callData: Bytes,
    ) : ContractStruct {
        override val tuple: List<Any> = listOf(target, callData)

        override val abiType: AbiType.Struct<*>
            get() = abi

        companion object : StructFactory<Call> {
            @JvmStatic
            override val abi: AbiType.Struct<Call> = AbiType.Struct(
                Call::class,
                ::fromTuple,
                AbiType.Struct.Field("target", AbiType.Address),
                AbiType.Struct.Field("callData", AbiType.Bytes),
            )

            @JvmStatic
            override fun fromTuple(data: List<Any>): Call = Call(data[0] as Address, data[1] as Bytes)
        }
    }

    data class Call3(
        val target: Address,
        val allowFailure: Boolean,
        val callData: Bytes,
    ) : ContractStruct {
        override val tuple: List<Any> = listOf(target, allowFailure, callData)

        override val abiType: AbiType.Struct<*>
            get() = abi

        companion object : StructFactory<Call3> {
            @JvmStatic
            override val abi: AbiType.Struct<Call3> = AbiType.Struct(
                Call3::class,
                ::fromTuple,
                AbiType.Struct.Field("target", AbiType.Address),
                AbiType.Struct.Field("allowFailure", AbiType.Bool),
                AbiType.Struct.Field("callData", AbiType.Bytes),
            )

            @JvmStatic
            override fun fromTuple(data: List<Any>): Call3 = Call3(
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
        override val tuple: List<Any> = listOf(target, allowFailure, value, callData)

        override val abiType: AbiType.Struct<*>
            get() = abi

        companion object : StructFactory<Call3Value> {
            @JvmStatic
            override val abi: AbiType.Struct<Call3Value> = AbiType.Struct(
                Call3Value::class,
                ::fromTuple,
                AbiType.Struct.Field("target", AbiType.Address),
                AbiType.Struct.Field("allowFailure", AbiType.Bool),
                AbiType.Struct.Field("value", AbiType.UInt(256)),
                AbiType.Struct.Field("callData", AbiType.Bytes),
            )

            @JvmStatic
            override fun fromTuple(data: List<Any>): Call3Value = Call3Value(
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
        override val tuple: List<Any> = listOf(success, returnData)

        override val abiType: AbiType.Struct<*>
            get() = abi

        companion object : StructFactory<Result> {
            @JvmStatic
            override val abi: AbiType.Struct<Result> = AbiType.Struct(
                Result::class,
                ::fromTuple,
                AbiType.Struct.Field("success", AbiType.Bool),
                AbiType.Struct.Field("returnData", AbiType.Bytes),
            )

            @JvmStatic
            override fun fromTuple(data: List<Any>): Result = Result(data[0] as Boolean, data[1] as Bytes)
        }
    }

    /**
     * A contract call that can be aggregated via [Multicall3] contract function call. Only [to], [value] and [data]
     * are used from the original call for the aggregation.
     * */
    interface Aggregatable<T> : IntoCallRequest {
        val provider: Middleware

        val to: Address?
        val value: BigInteger?
        val data: Bytes?

        /**
         * Whether this call can fail without reverting the whole aggregate call. Defaults to false, can be set
         * to true by calling [allowFailure] function.
         * */
        val allowFailure: Boolean
            get() = false

        /**
         * Safely decode the result of the call, returning the decoded value or a [ContractError] if decoding fails.
         * */
        fun decodeCallResult(result: Bytes): io.ethers.core.Result<T, ContractError>

        /**
         * Mark this call as allowing failure, by wrapping it in a new instance of [Aggregatable] that allows failure.
         * */
        fun allowFailure(): Aggregatable<T> {
            if (this.allowFailure) {
                return this
            }

            // delegate to this instance, but override allowFailure
            return object : Aggregatable<T> by this {
                override val allowFailure: Boolean
                    get() = true
            }
        }
    }

    companion object {
        private val INSTANCE_PER_CHAIN_ID_LOCK = SynchronizedObject()
        private val INSTANCE_PER_CHAIN_ID = HashMap<Long, Multicall3>()
        private val DEFAULT_ADDRESS = Address("0xcA11bde05977b3631167028862bE2a173976CA11")
        private val DEPLOYED_BYTECODE = Bytes(
            "0x6080604052600436106100f35760003560e01c80634d2301cc1161008a578063a8b0574e11610059578063a8b0574e1461025a578063bce38bd714610275578063c3077fa914610288578063ee82ac5e1461029b57600080fd5b80634d2301cc146101ec57806372425d9d1461022157806382ad56cb1461023457806386d516e81461024757600080fd5b80633408e470116100c65780633408e47014610191578063399542e9146101a45780633e64a696146101c657806342cbb15c146101d957600080fd5b80630f28c97d146100f8578063174dea711461011a578063252dba421461013a57806327e86d6e1461015b575b600080fd5b34801561010457600080fd5b50425b6040519081526020015b60405180910390f35b61012d610128366004610a85565b6102ba565b6040516101119190610bbe565b61014d610148366004610a85565b6104ef565b604051610111929190610bd8565b34801561016757600080fd5b50437fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0140610107565b34801561019d57600080fd5b5046610107565b6101b76101b2366004610c60565b610690565b60405161011193929190610cba565b3480156101d257600080fd5b5048610107565b3480156101e557600080fd5b5043610107565b3480156101f857600080fd5b50610107610207366004610ce2565b73ffffffffffffffffffffffffffffffffffffffff163190565b34801561022d57600080fd5b5044610107565b61012d610242366004610a85565b6106ab565b34801561025357600080fd5b5045610107565b34801561026657600080fd5b50604051418152602001610111565b61012d610283366004610c60565b61085a565b6101b7610296366004610a85565b610a1a565b3480156102a757600080fd5b506101076102b6366004610d18565b4090565b60606000828067ffffffffffffffff8111156102d8576102d8610d31565b60405190808252806020026020018201604052801561031e57816020015b6040805180820190915260008152606060208201528152602001906001900390816102f65790505b5092503660005b8281101561047757600085828151811061034157610341610d60565b6020026020010151905087878381811061035d5761035d610d60565b905060200281019061036f9190610d8f565b6040810135958601959093506103886020850185610ce2565b73ffffffffffffffffffffffffffffffffffffffff16816103ac6060870187610dcd565b6040516103ba929190610e32565b60006040518083038185875af1925050503d80600081146103f7576040519150601f19603f3d011682016040523d82523d6000602084013e6103fc565b606091505b50602080850191909152901515808452908501351761046d577f08c379a000000000000000000000000000000000000000000000000000000000600052602060045260176024527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060445260846000fd5b5050600101610325565b508234146104e6576040517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601a60248201527f4d756c746963616c6c333a2076616c7565206d69736d6174636800000000000060448201526064015b60405180910390fd5b50505092915050565b436060828067ffffffffffffffff81111561050c5761050c610d31565b60405190808252806020026020018201604052801561053f57816020015b606081526020019060019003908161052a5790505b5091503660005b8281101561068657600087878381811061056257610562610d60565b90506020028101906105749190610e42565b92506105836020840184610ce2565b73ffffffffffffffffffffffffffffffffffffffff166105a66020850185610dcd565b6040516105b4929190610e32565b6000604051808303816000865af19150503d80600081146105f1576040519150601f19603f3d011682016040523d82523d6000602084013e6105f6565b606091505b5086848151811061060957610609610d60565b602090810291909101015290508061067d576040517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601760248201527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060448201526064016104dd565b50600101610546565b5050509250929050565b43804060606106a086868661085a565b905093509350939050565b6060818067ffffffffffffffff8111156106c7576106c7610d31565b60405190808252806020026020018201604052801561070d57816020015b6040805180820190915260008152606060208201528152602001906001900390816106e55790505b5091503660005b828110156104e657600084828151811061073057610730610d60565b6020026020010151905086868381811061074c5761074c610d60565b905060200281019061075e9190610e76565b925061076d6020840184610ce2565b73ffffffffffffffffffffffffffffffffffffffff166107906040850185610dcd565b60405161079e929190610e32565b6000604051808303816000865af19150503d80600081146107db576040519150601f19603f3d011682016040523d82523d6000602084013e6107e0565b606091505b506020808401919091529015158083529084013517610851577f08c379a000000000000000000000000000000000000000000000000000000000600052602060045260176024527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060445260646000fd5b50600101610714565b6060818067ffffffffffffffff81111561087657610876610d31565b6040519080825280602002602001820160405280156108bc57816020015b6040805180820190915260008152606060208201528152602001906001900390816108945790505b5091503660005b82811015610a105760008482815181106108df576108df610d60565b602002602001015190508686838181106108fb576108fb610d60565b905060200281019061090d9190610e42565b925061091c6020840184610ce2565b73ffffffffffffffffffffffffffffffffffffffff1661093f6020850185610dcd565b60405161094d929190610e32565b6000604051808303816000865af19150503d806000811461098a576040519150601f19603f3d011682016040523d82523d6000602084013e61098f565b606091505b506020830152151581528715610a07578051610a07576040517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601760248201527f4d756c746963616c6c333a2063616c6c206661696c656400000000000000000060448201526064016104dd565b506001016108c3565b5050509392505050565b6000806060610a2b60018686610690565b919790965090945092505050565b60008083601f840112610a4b57600080fd5b50813567ffffffffffffffff811115610a6357600080fd5b6020830191508360208260051b8501011115610a7e57600080fd5b9250929050565b60008060208385031215610a9857600080fd5b823567ffffffffffffffff811115610aaf57600080fd5b610abb85828601610a39565b90969095509350505050565b6000815180845260005b81811015610aed57602081850181015186830182015201610ad1565b81811115610aff576000602083870101525b50601f017fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe0169290920160200192915050565b600082825180855260208086019550808260051b84010181860160005b84811015610bb1578583037fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe001895281518051151584528401516040858501819052610b9d81860183610ac7565b9a86019a9450505090830190600101610b4f565b5090979650505050505050565b602081526000610bd16020830184610b32565b9392505050565b600060408201848352602060408185015281855180845260608601915060608160051b870101935082870160005b82811015610c52577fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffa0888703018452610c40868351610ac7565b95509284019290840190600101610c06565b509398975050505050505050565b600080600060408486031215610c7557600080fd5b83358015158114610c8557600080fd5b9250602084013567ffffffffffffffff811115610ca157600080fd5b610cad86828701610a39565b9497909650939450505050565b838152826020820152606060408201526000610cd96060830184610b32565b95945050505050565b600060208284031215610cf457600080fd5b813573ffffffffffffffffffffffffffffffffffffffff81168114610bd157600080fd5b600060208284031215610d2a57600080fd5b5035919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052603260045260246000fd5b600082357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff81833603018112610dc357600080fd5b9190910192915050565b60008083357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe1843603018112610e0257600080fd5b83018035915067ffffffffffffffff821115610e1d57600080fd5b602001915036819003821315610a7e57600080fd5b8183823760009101908152919050565b600082357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffc1833603018112610dc357600080fd5b600082357fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffa1833603018112610dc357600080fdfea2646970667358221220449a18ce35be1adaedc9fa8ac86170e8c0f9b35e4a63d7b367123a4743501ae564736f6c634300080c0033",
        )

        private val DEPLOYMENT_PER_CHAIN_ID: Map<Long, MulticallDeployment> by lazy {
            Multicall3DeploymentsData.DEPLOYMENTS.associateBy(
                keySelector = { it.chainId },
                valueTransform = { MulticallDeployment(it.name, it.chainId, it.url, it.address?.let(::Address)) },
            )
        }

        @JvmStatic
        fun getAddressForChainId(chainId: Long): Address {
            return DEPLOYMENT_PER_CHAIN_ID[chainId]?.address ?: DEFAULT_ADDRESS
        }

        @JvmStatic
        fun getInstance(provider: Middleware): Multicall3 {
            return synchronized(INSTANCE_PER_CHAIN_ID_LOCK) {
                INSTANCE_PER_CHAIN_ID.getOrPut(provider.chainId) {
                    Multicall3(provider, getAddressForChainId(provider.chainId))
                }
            }
        }

        /**
         * Contract call that aggregates multiple [Aggregatable] calls into a single [Multicall3] contract call.
         *
         * **IMPORTANT**: From the call, only [Aggregatable.to], [Aggregatable.value] and [Aggregatable.data] parameters
         * are used. All others are ignored.
         *
         * The calls will be aggregated using as few calldata as possible, depending on the calls' properties. One of
         * the following aggregation functions will be used:
         * - [aggregate3Value], if any call is payable with non-zero value set,
         * - [aggregate3], if calls have mixed failure conditions (i.e. some allow failure, some don't),
         * - [tryAggregate], if all calls have the same failure condition, and are not payable or with zero value.
         *
         * Aggregate calls can be nested, i.e. an aggregate call can contain other aggregate calls.
         * */
        @JvmStatic
        fun <T> aggregate(vararg calls: Aggregatable<out T>): FunctionCall<AggregationResult<T>> {
            return aggregate(calls.toList())
        }

        /**
         * Contract call that aggregates multiple [Aggregatable] calls into a single [Multicall3] contract call.
         *
         * **IMPORTANT**: From the call, only [Aggregatable.to], [Aggregatable.value] and [Aggregatable.data] parameters
         * are used. All others are ignored.
         *
         * The calls will be aggregated using as few calldata as possible, depending on the calls' properties. One of
         * the following aggregation functions will be used:
         * - [aggregate3Value], if any call is payable with non-zero value set,
         * - [aggregate3], if calls have mixed failure conditions (i.e. some allow failure, some don't),
         * - [tryAggregate], if all calls have the same failure condition, and are not payable or with zero value.
         *
         * Aggregate calls can be nested, i.e. an aggregate call can contain other aggregate calls.
         * */
        @JvmStatic
        fun <T> aggregate(calls: List<Aggregatable<out T>>): FunctionCall<AggregationResult<T>> {
            if (calls.isEmpty()) {
                throw IllegalArgumentException("No calls to aggregate")
            }

            val provider = calls.first().provider
            return getInstance(provider).aggregateCalls(calls)
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
            listOf(AbiType.Array(Call3)),
            listOf(AbiType.Array(Result)),
        )

        @JvmField
        val FUNCTION_BLOCK_AND_AGGREGATE: AbiFunction = AbiFunction(
            "blockAndAggregate",
            listOf(AbiType.Array(Call)),
            listOf(
                AbiType.UInt(256),
                AbiType.FixedBytes(32),
                AbiType.Array(Result),
            ),
        )

        @JvmField
        val FUNCTION_AGGREGATE3_VALUE: AbiFunction = AbiFunction(
            "aggregate3Value",
            listOf(AbiType.Array(Call3Value)),
            listOf(AbiType.Array(Result)),
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
            listOf(AbiType.Array(Call)),
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
            listOf(AbiType.Bool, AbiType.Array(Call)),
            listOf(AbiType.Array(Result)),
        )

        @JvmField
        val FUNCTION_TRY_BLOCK_AND_AGGREGATE: AbiFunction =
            AbiFunction(
                "tryBlockAndAggregate",
                listOf(AbiType.Bool, AbiType.Array(Call)),
                listOf(
                    AbiType.UInt(256),
                    AbiType.FixedBytes(32),
                    AbiType.Array(Result),
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

/**
 * Encode the [Multicall3.Aggregatable] calls into as few calldata as possible. All the functions return the same
 * type: list of [Multicall3.Result]'s.
 * */
private inline fun <T> List<Multicall3.Aggregatable<*>>.withDataAndValue(consumer: (Bytes, BigInteger?) -> T): T {
    if (this.isEmpty()) {
        return consumer(Bytes.EMPTY, null)
    }

    var anyPayable = false
    var mixedFailureConditions = false
    for (i in this.indices) {
        val call = this[i]
        if (call.value != null && call.value != BigInteger.ZERO) {
            anyPayable = true
            break
        }

        if (i > 0 && call.allowFailure != this[0].allowFailure) {
            mixedFailureConditions = true
            break
        }
    }

    // try to pack the calls using as few calldata as possible
    return when {
        anyPayable -> {
            var totalValue = BigInteger.ZERO
            val arr = List(this.size) {
                val req = this[it]
                val value = req.value ?: BigInteger.ZERO
                totalValue += value

                Multicall3.Call3Value(req.to!!, req.allowFailure, value, req.data ?: Bytes.EMPTY)
            }

            consumer(Multicall3.FUNCTION_AGGREGATE3_VALUE.encodeCall(listOf(arr)), totalValue)
        }

        mixedFailureConditions -> {
            val arr = List(this.size) {
                val req = this[it]
                Multicall3.Call3(req.to!!, req.allowFailure, req.data ?: Bytes.EMPTY)
            }

            consumer(Multicall3.FUNCTION_AGGREGATE3.encodeCall(listOf(arr)), null)
        }

        else -> {
            val allowFailure = this[0].allowFailure
            val arr = List(this.size) {
                val req = this[it]
                Multicall3.Call(req.to!!, req.data ?: Bytes.EMPTY)
            }

            consumer(Multicall3.FUNCTION_TRY_AGGREGATE.encodeCall(listOf(!allowFailure, arr)), null)
        }
    }
}

/**
 * Aggregate all calls into a [Multicall3] call. Only [CallRequest.to], [CallRequest.value], [CallRequest.data]
 * fields are used from the calls.
 * */
fun <T> Iterable<Multicall3.Aggregatable<T>>.aggregate(): FunctionCall<AggregationResult<T>> {
    val collection = this as? List<Multicall3.Aggregatable<T>> ?: toList()
    return Multicall3.aggregate(collection)
}
