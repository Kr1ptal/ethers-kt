package io.ethers.abi.eip712

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.core.FastHex
import io.ethers.core.Jackson
import io.ethers.core.forEachObjectField
import io.ethers.core.readAddress
import io.ethers.core.readBytes
import io.ethers.core.readOrNull
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import java.math.BigInteger

@JsonSerialize(using = EIP712DomainSerializer::class)
@JsonDeserialize(using = EIP712DomainDeserializer::class)
data class EIP712Domain(
    val name: String? = null,
    val version: String? = null,
    val chainId: BigInteger? = null,
    val verifyingContract: Address? = null,
    val salt: Bytes? = null,
) : ContractStruct {
    override val tuple: List<Any> = buildList {
        name?.let { add(it) }
        version?.let { add(it) }
        chainId?.let { add(it) }
        verifyingContract?.let { add(it) }
        salt?.let { add(it) }
    }

    val separator by lazy(LazyThreadSafetyMode.NONE) {
        EIP712Codec.hashStruct(this)
    }

    override val abiType = AbiType.Struct(
        EIP712Domain::class.java,
        { data ->
            var index = 0
            EIP712Domain(
                name = if (this.name != null) data[index++] as String else null,
                version = if (this.version != null) data[index++] as String else null,
                chainId = if (this.chainId != null) data[index++] as BigInteger else null,
                verifyingContract = if (this.verifyingContract != null) data[index++] as Address else null,
                salt = if (this.salt != null) data[index++] as Bytes else null,
            )
        },
        buildList {
            name?.let { add(AbiType.Struct.Field("name", AbiType.String)) }
            version?.let { add(AbiType.Struct.Field("version", AbiType.String)) }
            chainId?.let { add(AbiType.Struct.Field("chainId", AbiType.UInt(256))) }
            verifyingContract?.let { add(AbiType.Struct.Field("verifyingContract", AbiType.Address)) }
            salt?.let { add(AbiType.Struct.Field("salt", AbiType.FixedBytes(32))) }
        },
    )
}

private class EIP712DomainSerializer : JsonSerializer<EIP712Domain>() {
    override fun serialize(value: EIP712Domain, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        value.name?.let { gen.writeStringField("name", it) }
        value.version?.let { gen.writeStringField("version", it) }
        value.chainId?.let { gen.writeStringField("chainId", it.toString()) }
        value.verifyingContract?.let { gen.writeStringField("verifyingContract", it.toString()) }
        value.salt?.let { gen.writeStringField("salt", FastHex.encodeWithPrefix(it.asByteArray())) }
        gen.writeEndObject()
    }
}

private class EIP712DomainDeserializer : JsonDeserializer<EIP712Domain>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EIP712Domain {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var name: String? = null
        var version: String? = null
        var chainId: BigInteger? = null
        var verifyingContract: Address? = null
        var salt: Bytes? = null

        p.forEachObjectField { field ->
            when (field) {
                "name" -> name = p.readOrNull { text }
                "version" -> version = p.readOrNull { text }
                "chainId" -> chainId = p.readOrNull { BigInteger(p.text) }
                "verifyingContract" -> verifyingContract = p.readOrNull { readAddress() }
                "salt" -> salt = p.readOrNull { readBytes() }
                else -> p.skipChildren()
            }
        }

        return EIP712Domain(
            name = name,
            version = version,
            chainId = chainId,
            verifyingContract = verifyingContract,
            salt = salt,
        )
    }
}

fun main() {
    val raw = """{"types":{"BulkOrder":[{"name":"tree","type":"OrderComponents[2]"}],"OrderComponents":[{"name":"offerer","type":"address"},{"name":"zone","type":"address"},{"name":"offer","type":"OfferItem[]"},{"name":"consideration","type":"ConsiderationItem[]"},{"name":"orderType","type":"uint8"},{"name":"startTime","type":"uint256"},{"name":"endTime","type":"uint256"},{"name":"zoneHash","type":"bytes32"},{"name":"salt","type":"uint256"},{"name":"conduitKey","type":"bytes32"},{"name":"counter","type":"uint256"}],"OfferItem":[{"name":"itemType","type":"uint8"},{"name":"token","type":"address"},{"name":"identifierOrCriteria","type":"uint256"},{"name":"startAmount","type":"uint256"},{"name":"endAmount","type":"uint256"}],"ConsiderationItem":[{"name":"itemType","type":"uint8"},{"name":"token","type":"address"},{"name":"identifierOrCriteria","type":"uint256"},{"name":"startAmount","type":"uint256"},{"name":"endAmount","type":"uint256"},{"name":"recipient","type":"address"}]},"primaryType":"BulkOrder","domain":{"name":"Seaport","version":"1.6","chainId":2741,"verifyingContract":"0x0000000000000068f116a894984e2db1123eb395"},"message":{"tree":[{"offerer":"0x562c422aeef8ba1331b0018665af51b1cb71e343","zone":"0x000056f7000000ece9003ca63978907a00ffd100","offer":[{"itemType":3,"token":"0x458422e93bf89a109afc4fac00aacf2f18fcf541","identifierOrCriteria":"504","startAmount":"1","endAmount":"1"}],"consideration":[{"itemType":0,"token":"0x0000000000000000000000000000000000000000","identifierOrCriteria":"0","startAmount":"263200000000000","endAmount":"263200000000000","recipient":"0x562c422aeef8ba1331b0018665af51b1cb71e343"},{"itemType":0,"token":"0x0000000000000000000000000000000000000000","identifierOrCriteria":"0","startAmount":"2800000000000","endAmount":"2800000000000","recipient":"0x0000a26b00c1f0df003000390027140000faa719"},{"itemType":0,"token":"0x0000000000000000000000000000000000000000","identifierOrCriteria":"0","startAmount":"14000000000000","endAmount":"14000000000000","recipient":"0xfb1302f5d6c5f107a0715b8ce7303d1e3c647807"}],"orderType":2,"startTime":"1758662541","endTime":"1761254541","zoneHash":"0x0000000000000000000000000000000000000000000000000000000000000000","salt":"27855337018906766782546881864045825683096516384821792734235564196342280731178","conduitKey":"0x61159fefdfada89302ed55f8b9e89e2d67d8258712b3a3f89aa88525877f1d5e","counter":"0"},{"offerer":"0x562c422aeef8ba1331b0018665af51b1cb71e343","zone":"0x000056f7000000ece9003ca63978907a00ffd100","offer":[{"itemType":3,"token":"0x458422e93bf89a109afc4fac00aacf2f18fcf541","identifierOrCriteria":"514","startAmount":"1","endAmount":"1"}],"consideration":[{"itemType":0,"token":"0x0000000000000000000000000000000000000000","identifierOrCriteria":"0","startAmount":"902400000000000","endAmount":"902400000000000","recipient":"0x562c422aeef8ba1331b0018665af51b1cb71e343"},{"itemType":0,"token":"0x0000000000000000000000000000000000000000","identifierOrCriteria":"0","startAmount":"9600000000000","endAmount":"9600000000000","recipient":"0x0000a26b00c1f0df003000390027140000faa719"},{"itemType":0,"token":"0x0000000000000000000000000000000000000000","identifierOrCriteria":"0","startAmount":"48000000000000","endAmount":"48000000000000","recipient":"0xfb1302f5d6c5f107a0715b8ce7303d1e3c647807"}],"orderType":2,"startTime":"1758662541","endTime":"1761254541","zoneHash":"0x0000000000000000000000000000000000000000000000000000000000000000","salt":"27855337018906766782546881864045825683096516384821792734240704567173025251123","conduitKey":"0x61159fefdfada89302ed55f8b9e89e2d67d8258712b3a3f89aa88525877f1d5e","counter":"0"}]}}"""

    val data = Jackson.MAPPER.readValue(raw, EIP712TypedData::class.java)

    println(data)
    println(data.signatureHash().toHexString())
}
