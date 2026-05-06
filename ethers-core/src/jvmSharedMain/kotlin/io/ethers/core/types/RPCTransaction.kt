package io.ethers.core.types

import io.ethers.core.asAddress
import io.ethers.core.asHash
import io.ethers.core.asHexBigInteger
import io.ethers.core.asHexByteArray
import io.ethers.core.asHexInt
import io.ethers.core.asHexLong
import io.ethers.core.json.JsonElement
import io.ethers.core.types.transaction.ChainId
import io.ethers.core.types.transaction.TransactionRecovered
import io.ethers.core.types.transaction.TxType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger

@Serializable(with = RPCTransactionSerializer::class)
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
    override val authorizationList: List<Authorization>?,
    override val chainId: Long,
    override val type: TxType,
    val v: Long,
    val r: BigInteger,
    val s: BigInteger,
    val yParity: Long,
    override val blobVersionedHashes: List<Hash>?,
    override val blobFeeCap: BigInteger?,
    val otherFields: Map<String, JsonElement> = emptyMap(),
) : TransactionRecovered {
    /**
     * Return true if the transaction has a signature, false otherwise.
     * */
    val hasSignature: Boolean
        get() = v != -1L && r != BigInteger.ZERO && s != BigInteger.ZERO
}

object RPCTransactionSerializer : KSerializer<RPCTransaction> {
    override val descriptor = buildClassSerialDescriptor("RPCTransaction")

    override fun serialize(encoder: Encoder, value: RPCTransaction) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): RPCTransaction {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        var blockHash: Hash? = null
        var blockNumber = -1L
        var transactionIndex = -1
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
        var authorizationList: List<Authorization>? = null
        var chainId: Long = ChainId.NONE
        var v = -1L
        var r: BigInteger? = null
        var s: BigInteger? = null
        var yParity = -1L
        var blobVersionedHashes: List<Hash>? = null
        var blobFeeCap: BigInteger? = null
        var otherFields: MutableMap<String, JsonElement>? = null

        for ((key, element) in obj.entries) {
            when (key) {
                "blockHash" -> blockHash = if (element is JsonNull) null else element.jsonPrimitive.asHash()
                "blockNumber" -> if (element !is JsonNull) blockNumber = element.jsonPrimitive.asHexLong()
                "transactionIndex" -> if (element !is JsonNull) transactionIndex = element.jsonPrimitive.asHexInt()
                "hash" -> hash = element.jsonPrimitive.asHash()
                "from" -> from = element.jsonPrimitive.asAddress()
                "to" -> to = if (element is JsonNull) null else element.jsonPrimitive.asAddress()
                "value" -> value = element.jsonPrimitive.asHexBigInteger()
                "nonce" -> nonce = element.jsonPrimitive.asHexLong()
                "gas" -> gas = element.jsonPrimitive.asHexLong()
                "gasPrice" -> gasPrice = element.jsonPrimitive.asHexBigInteger()
                "maxFeePerGas" -> gasFeeCap = element.jsonPrimitive.asHexBigInteger()
                "maxPriorityFeePerGas" -> gasTipCap = element.jsonPrimitive.asHexBigInteger()
                "input" -> {
                    val bytes = element.jsonPrimitive.asHexByteArray()
                    data = if (bytes.isEmpty()) null else Bytes(bytes)
                }
                "type" -> type = element.jsonPrimitive.asHexLong()
                "accessList" -> accessList = element.jsonArray.map {
                    jsonDecoder.json.decodeFromJsonElement(AccessListItemSerializer, it)
                }
                "authorizationList" -> authorizationList = element.jsonArray.map {
                    jsonDecoder.json.decodeFromJsonElement(AuthorizationSerializer, it)
                }
                "chainId" -> if (element !is JsonNull) chainId = element.jsonPrimitive.asHexLong()
                "v" -> v = element.jsonPrimitive.asHexLong()
                "r" -> r = element.jsonPrimitive.asHexBigInteger()
                "s" -> s = element.jsonPrimitive.asHexBigInteger()
                "yParity" -> yParity = element.jsonPrimitive.asHexLong()
                "blobVersionedHashes" -> blobVersionedHashes = element.jsonArray.map { it.jsonPrimitive.asHash() }
                "maxFeePerBlobGas" -> blobFeeCap = element.jsonPrimitive.asHexBigInteger()
                else -> {
                    if (otherFields == null) otherFields = HashMap()
                    otherFields[key] = JsonElement(element.toString())
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
            authorizationList,
            chainId,
            TxType.fromType(type.toInt()),
            v,
            r ?: BigInteger.ZERO,
            s ?: BigInteger.ZERO,
            yParity,
            blobVersionedHashes,
            blobFeeCap,
            otherFields ?: emptyMap(),
        )
    }
}
