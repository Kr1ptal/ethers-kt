package io.ethers.abi.eip712

import io.ethers.abi.AbiType
import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class EIP712DomainTest : FunSpec({
    test("tuple contains only non-null fields in correct order") {
        val domain = EIP712Domain(
            name = "TestDApp",
            version = "1.0",
            chainId = BigInteger.valueOf(1),
            verifyingContract = Address.ZERO,
            salt = Bytes("0000000000000000000000000000000000000000000000000000000000000001"),
        )

        domain.tuple shouldBe listOf(
            "TestDApp",
            "1.0",
            BigInteger.valueOf(1),
            Address.ZERO,
            Bytes("0000000000000000000000000000000000000000000000000000000000000001"),
        )
    }

    test("tuple contains only non-null fields when some are null") {
        val domain = EIP712Domain(
            name = "TestDApp",
            chainId = BigInteger.valueOf(1),
            salt = Bytes("0000000000000000000000000000000000000000000000000000000000000001"),
        )

        domain.tuple shouldBe listOf(
            "TestDApp",
            BigInteger.valueOf(1),
            Bytes("0000000000000000000000000000000000000000000000000000000000000001"),
        )
    }

    test("abiType contains only non-null fields") {
        val domain = EIP712Domain(
            name = "TestDApp",
            chainId = BigInteger.valueOf(1),
        )

        val abiType = domain.abiType
        abiType.fields shouldContainExactlyInAnyOrder listOf(
            AbiType.Struct.Field("name", AbiType.String),
            AbiType.Struct.Field("chainId", AbiType.UInt(256)),
        )
    }

    test("abiType contains all fields when all are present") {
        val domain = EIP712Domain(
            name = "TestDApp",
            version = "1.0",
            chainId = BigInteger.valueOf(1),
            verifyingContract = Address.ZERO,
            salt = Bytes("0000000000000000000000000000000000000000000000000000000000000001"),
        )

        val abiType = domain.abiType
        abiType.fields shouldContainExactlyInAnyOrder listOf(
            AbiType.Struct.Field("name", AbiType.String),
            AbiType.Struct.Field("version", AbiType.String),
            AbiType.Struct.Field("chainId", AbiType.UInt(256)),
            AbiType.Struct.Field("verifyingContract", AbiType.Address),
            AbiType.Struct.Field("salt", AbiType.FixedBytes(32)),
        )
    }

    test("empty domain has empty abiType fields") {
        val domain = EIP712Domain()

        domain.abiType.fields shouldBe emptyList()
        domain.tuple shouldBe emptyList()
    }

    test("has correct root type") {
        val domain = EIP712Domain(
            name = "TestDApp",
            version = "1.0",
            chainId = BigInteger.valueOf(1),
        )

        domain.abiType.eip712RootType shouldBe "EIP712Domain(string name,string version,uint256 chainId)"
    }

    test("has correct separator hash") {
        val domain = EIP712Domain(
            "Seaport",
            "1.1",
            BigInteger.ONE,
            Address("0x00000000006c3852cbEf3e08E8dF289169EdE581"),
        )

        // Should not throw and should return a 32-byte hash
        val hash = Hash(domain.separator)
        hash shouldBe Hash("0xb50c8913581289bd2e066aeef89fceb9615d490d673131fd1a7047436706834e")
    }

    context("JSON serialization") {
        val mapper = Jackson.MAPPER

        test("roundtrip serialization of EIP712Domain") {
            val domain = EIP712Domain(
                name = "TestDApp",
                version = "1.0.0",
                chainId = BigInteger.valueOf(137),
                verifyingContract = Address("0x3333333333333333333333333333333333333333"),
                salt = null,
            )

            val json = mapper.writeValueAsString(domain)
            val deserialized = mapper.readValue(json, EIP712Domain::class.java)

            deserialized shouldBe domain
        }

        test("serializes null fields correctly") {
            val domain = EIP712Domain(name = "TestOnly")

            val json = mapper.writeValueAsString(domain)
            val deserialized = mapper.readValue(json, EIP712Domain::class.java)

            deserialized.name shouldBe "TestOnly"
            deserialized.version shouldBe null
            deserialized.chainId shouldBe null
            deserialized.verifyingContract shouldBe null
            deserialized.salt shouldBe null
        }

        test("serializes all fields when present") {
            val domain = EIP712Domain(
                name = "FullDApp",
                version = "2.1.0",
                chainId = BigInteger.valueOf(1337),
                verifyingContract = Address("0x4444444444444444444444444444444444444444"),
                salt = Bytes("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
            )

            val json = mapper.writeValueAsString(domain)
            val jsonNode = mapper.readTree(json)

            // Verify JSON structure
            jsonNode.has("name") shouldBe true
            jsonNode.has("version") shouldBe true
            jsonNode.has("chainId") shouldBe true
            jsonNode.has("verifyingContract") shouldBe true
            jsonNode.has("salt") shouldBe true

            // Verify values are properly formatted
            jsonNode["name"].asText() shouldBe "FullDApp"
            jsonNode["version"].asText() shouldBe "2.1.0"
            jsonNode["chainId"].asText() shouldBe "1337"
            jsonNode["verifyingContract"].asText() shouldBe "0x4444444444444444444444444444444444444444"
            jsonNode["salt"].asText() shouldBe "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

            // Verify roundtrip
            val deserialized = mapper.readValue(json, EIP712Domain::class.java)
            deserialized shouldBe domain
        }
    }
})
