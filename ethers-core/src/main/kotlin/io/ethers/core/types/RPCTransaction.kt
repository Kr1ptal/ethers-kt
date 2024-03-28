package io.ethers.core.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.forEachObjectField
import io.ethers.core.ifNotNull
import io.ethers.core.readAddress
import io.ethers.core.readBytesEmptyAsNull
import io.ethers.core.readHash
import io.ethers.core.readHexBigInteger
import io.ethers.core.readHexInt
import io.ethers.core.readHexLong
import io.ethers.core.readListOf
import io.ethers.core.readOrNull
import io.ethers.core.types.transaction.ChainId
import io.ethers.core.types.transaction.TransactionRecovered
import io.ethers.core.types.transaction.TxType
import java.math.BigInteger

@JsonDeserialize(using = RPCTransactionDeserializer::class)
data class RPCTransaction(
    val blockHash: Hash?,
    val blockNumber: Long,
    val transactionIndex: Int,
    override val hash: Hash,
    override val from: Address,
    override val to: Address?,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasPrice: BigInteger,
    override val gasFeeCap: BigInteger,
    override val gasTipCap: BigInteger,
    override val data: Bytes?,
    override val accessList: List<AccessList.Item>,
    override val chainId: Long,
    override val type: TxType,
    val v: Long,
    val r: BigInteger,
    val s: BigInteger,
    val yParity: Int,
    override val blobVersionedHashes: List<Hash>?,
    override val blobFeeCap: BigInteger?,
    val otherFields: Map<String, JsonNode> = emptyMap(),
) : TransactionRecovered

private class RPCTransactionDeserializer : JsonDeserializer<RPCTransaction>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): RPCTransaction {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var blockHash: Hash? = null
        var blockNumber: Long = -1L
        var transactionIndex: Int = -1
        lateinit var hash: Hash
        lateinit var from: Address
        var to: Address? = null
        lateinit var value: BigInteger
        var nonce = -1L
        var gas = -1L
        lateinit var gasPrice: BigInteger
        var gasFeeCap: BigInteger? = null
        var gasTipCap: BigInteger? = null
        var data: Bytes? = null
        var type = -1L
        var accessList: List<AccessList.Item> = emptyList()
        var chainId: Long = ChainId.NONE
        var v = -1L
        lateinit var r: BigInteger
        lateinit var s: BigInteger
        var yParity = -1
        var blobVersionedHashes: List<Hash>? = null
        var blobFeeCap: BigInteger? = null
        var otherFields: MutableMap<String, JsonNode>? = null

        p.forEachObjectField { field ->
            when (field) {
                "blockHash" -> blockHash = p.readOrNull { readHash() }
                "blockNumber" -> p.ifNotNull { blockNumber = p.readHexLong() }
                "transactionIndex" -> p.ifNotNull { transactionIndex = p.readHexInt() }
                "hash" -> hash = p.readHash()
                "from" -> from = p.readAddress()
                "to" -> to = p.readOrNull { readAddress() }
                "value" -> value = p.readHexBigInteger()
                "nonce" -> nonce = p.readHexLong()
                "gas" -> gas = p.readHexLong()
                "gasPrice" -> gasPrice = p.readHexBigInteger()
                "maxFeePerGas" -> gasFeeCap = p.readHexBigInteger()
                "maxPriorityFeePerGas" -> gasTipCap = p.readHexBigInteger()
                "input" -> data = p.readBytesEmptyAsNull()
                "type" -> type = p.readHexLong()
                "accessList" -> accessList = p.readListOf(AccessList.Item::class.java)
                "chainId" -> p.ifNotNull { chainId = p.readHexLong() }
                "v" -> v = p.readHexLong()
                "r" -> r = p.readHexBigInteger()
                "s" -> s = p.readHexBigInteger()
                "y" -> yParity = p.readHexInt()
                "blobVersionedHashes" -> blobVersionedHashes = p.readListOf(Hash::class.java)
                "maxFeePerBlobGas" -> blobFeeCap = p.readHexBigInteger()
                else -> {
                    if (otherFields == null) {
                        otherFields = HashMap()
                    }
                    otherFields!![p.currentName()] = p.readValueAs(JsonNode::class.java)
                }
            }
        }

        return RPCTransaction(
            blockHash,
            blockNumber,
            transactionIndex,
            hash,
            from,
            to,
            value,
            nonce,
            gas,
            gasPrice,
            gasFeeCap ?: gasPrice,
            gasTipCap ?: gasPrice,
            data,
            accessList,
            chainId,
            TxType.fromType(type.toInt()),
            v,
            r,
            s,
            yParity,
            blobVersionedHashes,
            blobFeeCap,
            otherFields ?: emptyMap(),
        )
    }
}
