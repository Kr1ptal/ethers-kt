package io.ethers.abi.eip712

import io.ethers.abi.AbiType
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
            name = "TestDApp",
            version = "1.0",
            chainId = BigInteger.valueOf(1),
        )

        // Should not throw and should return a 32-byte hash
        val hash = Hash(domain.separator)
        hash shouldBe Hash("0xc2f8787176b8ac6bf7215b4adcc1e069bf4ab82d9ab1df05a57a91d425935b6e")
    }
})
