package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.forEachObjectField
import io.ethers.core.handleUnknownField
import io.ethers.core.readAddress
import io.ethers.core.readHexLong
import io.ethers.core.readListOf
import io.ethers.core.readListOfHashes
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder

// Ideally, this would be an inline class, but java interop is a pain. If you need to add any functions to operate on
// AccessList, add an extension function to List<AccessList.Item> instead, inside this object, and annotate it with
// @JvmStatic, so it's accessible from java.
object AccessList {

    @JsonSerialize(using = AccessListItemSerializer::class)
    @JsonDeserialize(using = AccessListItemDeserializer::class)
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
                return rlp.decodeList {
                    val address = rlp.decode(Address) ?: return null
                    val storageKeys = rlp.decodeAsList(Hash)

                    Item(address, storageKeys)
                }
            }
        }
    }
}

@JsonDeserialize(using = CreateAccessListDeserializer::class)
data class CreateAccessList(
    val accessList: List<AccessList.Item>,
    val gasUsed: Long,
    val error: String?,
)

private class AccessListItemSerializer : JsonSerializer<AccessList.Item>() {
    override fun serialize(value: AccessList.Item, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("address", value.address.toString())

        gen.writeArrayFieldStart("storageKeys")
        for (i in value.storageKeys.indices) {
            gen.writeString(value.storageKeys[i].toString())
        }
        gen.writeEndArray()

        gen.writeEndObject()
    }
}

private class AccessListItemDeserializer : JsonDeserializer<AccessList.Item>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AccessList.Item {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        lateinit var address: Address
        var storageKeys: List<Hash>? = null

        p.forEachObjectField { field ->
            when (field) {
                "address" -> address = p.readAddress()
                "storageKeys" -> storageKeys = p.readListOfHashes()
                else -> p.handleUnknownField()
            }
        }

        return AccessList.Item(
            address,
            storageKeys ?: emptyList(),
        )
    }
}

class CreateAccessListDeserializer : JsonDeserializer<CreateAccessList>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CreateAccessList {
        var accessList: List<AccessList.Item> = emptyList()
        var gasUsed = 0L
        var error: String? = null

        p.forEachObjectField { field ->
            when (field) {
                "accessList" -> accessList = p.readListOf(AccessList.Item::class.java)
                "gasUsed" -> gasUsed = p.readHexLong()
                "error" -> error = p.text
                else -> p.handleUnknownField()
            }
        }

        return CreateAccessList(accessList, gasUsed, error)
    }
}
