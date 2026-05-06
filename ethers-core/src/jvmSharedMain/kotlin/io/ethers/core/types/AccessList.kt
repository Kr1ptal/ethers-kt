package io.ethers.core.types

import io.ethers.core.asAddress
import io.ethers.core.asHash
import io.ethers.core.asHexLong
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Ideally, this would be an inline class, but java interop is a pain. If you need to add any functions to operate on
// AccessList, add an extension function to List<AccessList.Item> instead, inside this object, and annotate it with
// @JvmStatic, so it's accessible from java.
object AccessList {

    @Serializable(with = AccessListItemSerializer::class)
    data class Item(val address: Address, val storageKeys: List<Hash>) : RlpEncodable {
        override fun rlpEncode(rlp: RlpEncoder) {
            val listBodySize = RlpEncoder.sizeOf(address) + RlpEncoder.sizeOfList(storageKeys)

            rlp.encodeList(listBodySize) {
                encode(address)
                encodeList(storageKeys)
            }
        }

        override fun rlpSize(): Int = with(RlpEncoder) {
            return sizeOfList(sizeOf(address) + sizeOfList(storageKeys))
        }

        companion object : RlpDecodable<Item> {
            @JvmStatic
            override fun rlpDecode(rlp: RlpDecoder): Item? {
                return rlp.decodeListOrNull {
                    val address = rlp.decodeOrNull(Address) ?: return null
                    val storageKeys = rlp.decodeAsListOrNull(Hash) ?: return null

                    Item(address, storageKeys)
                }
            }
        }
    }
}

@Serializable(with = CreateAccessListSerializer::class)
data class CreateAccessList(
    val accessList: List<AccessList.Item>,
    val gasUsed: Long,
    val error: String?,
)

object AccessListItemSerializer : KSerializer<AccessList.Item> {
    override val descriptor = buildClassSerialDescriptor("AccessList.Item")

    override fun serialize(encoder: Encoder, value: AccessList.Item) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("address", value.address.toString())
                put(
                    "storageKeys",
                    buildJsonArray {
                        for (i in value.storageKeys.indices) {
                            add(kotlinx.serialization.json.JsonPrimitive(value.storageKeys[i].toString()))
                        }
                    },
                )
            },
        )
    }

    override fun deserialize(decoder: Decoder): AccessList.Item {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val address = obj["address"]!!.jsonPrimitive.asAddress()
        val storageKeys = obj["storageKeys"]?.jsonArray?.map { it.jsonPrimitive.asHash() } ?: emptyList()

        return AccessList.Item(address, storageKeys)
    }
}

object CreateAccessListSerializer : KSerializer<CreateAccessList> {
    override val descriptor = buildClassSerialDescriptor("CreateAccessList")

    override fun serialize(encoder: Encoder, value: CreateAccessList) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): CreateAccessList {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val accessList = obj["accessList"]?.jsonArray?.map { element ->
            jsonDecoder.json.decodeFromJsonElement(AccessListItemSerializer, element)
        } ?: emptyList()
        val gasUsed = obj["gasUsed"]!!.jsonPrimitive.asHexLong()
        val error: String? = obj["error"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }

        return CreateAccessList(accessList, gasUsed, error)
    }
}
