package io.ethers.aa

import io.ethers.abi.AbiCodecException
import io.ethers.core.Kotlinx
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class UserOperationTest : FunSpec({
    val sender = Address("0x1111111111111111111111111111111111111111")
    val factory = Address("0x2222222222222222222222222222222222222222")
    val paymaster = Address("0x3333333333333333333333333333333333333333")

    fun op(
        paymasterVerificationGasLimit: BigInteger? = bigIntegerOf(40_000),
        paymasterPostOpGasLimit: BigInteger? = bigIntegerOf(50_000),
        paymasterSignature: Bytes? = null,
    ) = UserOperation(
        sender = sender,
        nonce = bigIntegerOf(7).shiftLeft(64).add(bigIntegerOf(3)),
        callData = Bytes("0xdeadbeef"),
        callGasLimit = bigIntegerOf(100_000),
        verificationGasLimit = bigIntegerOf(200_000),
        preVerificationGas = bigIntegerOf(50_000),
        maxFeePerGas = bigIntegerOf(30_000_000_000),
        maxPriorityFeePerGas = bigIntegerOf(1_000_000_000),
        signature = Bytes("0x" + "11".repeat(65)),
        factory = factory,
        factoryData = Bytes("0xcafe"),
        paymaster = paymaster,
        paymasterVerificationGasLimit = paymasterVerificationGasLimit,
        paymasterPostOpGasLimit = paymasterPostOpGasLimit,
        paymasterData = Bytes("0xabcdef"),
        paymasterSignature = paymasterSignature,
    )

    context("hash of mainnet operations matches the EntryPoint's") {
        withData(UserOperationVectors.ALL.associateBy { it.description }) { vector ->
            val userOp = UserOperation.fromJson(Kotlinx.DEFAULT.parseToJsonElement(vector.json))
            userOp.hash(vector.entryPoint, vector.chainId, vector.eip7702Delegate) shouldBe vector.hash
        }
    }

    context("JSON-RPC round trip of mainnet operations") {
        withData(UserOperationVectors.ALL.associateBy { it.description }) { vector ->
            val json = Kotlinx.DEFAULT.parseToJsonElement(vector.json).jsonObject
            UserOperation.fromJson(json).toJson(vector.entryPoint.version) shouldBe json
        }
    }

    test("nonce key and sequence") {
        val userOp = op()
        userOp.nonceKey shouldBe bigIntegerOf(7)
        userOp.nonceSequence shouldBe bigIntegerOf(3)
    }

    test("initCode joins factory and factoryData") {
        op().initCode shouldBe Bytes("0x2222222222222222222222222222222222222222cafe")
        op().copy(factory = null, factoryData = null).initCode shouldBe Bytes.EMPTY
    }

    context("packed round trip") {
        withData(EntryPointVersion.V0_7, EntryPointVersion.V0_8, EntryPointVersion.V0_9) { version ->
            val userOp = op()
            val packed = userOp.toPacked(version)
            packed.accountGasLimits shouldBe Bytes(
                "0x" + "00000000000000000000000000030d40" + "000000000000000000000000000186a0",
            )
            packed.gasFees shouldBe Bytes(
                "0x" + "0000000000000000000000003b9aca00" + "000000000000000000000006fc23ac00",
            )
            packed.paymasterAndData shouldBe Bytes(
                "0x3333333333333333333333333333333333333333" +
                    "00000000000000000000000000009c40" + "0000000000000000000000000000c350" + "abcdef",
            )
            UserOperation.fromPacked(packed, version) shouldBe userOp
        }
    }

    test("v0.6 operation round trips through its on-chain fields") {
        val userOp = op(paymasterVerificationGasLimit = null, paymasterPostOpGasLimit = null)
        userOp.paymasterAndData(EntryPointVersion.V0_6) shouldBe Bytes("0x3333333333333333333333333333333333333333abcdef")

        UserOperation.fromV06(
            sender = userOp.sender,
            nonce = userOp.nonce,
            initCode = userOp.initCode,
            callData = userOp.callData,
            callGasLimit = userOp.callGasLimit,
            verificationGasLimit = userOp.verificationGasLimit,
            preVerificationGas = userOp.preVerificationGas,
            maxFeePerGas = userOp.maxFeePerGas,
            maxPriorityFeePerGas = userOp.maxPriorityFeePerGas,
            paymasterAndData = userOp.paymasterAndData(EntryPointVersion.V0_6),
            signature = userOp.signature,
        ) shouldBe userOp.copy(paymasterData = Bytes("0xabcdef"))
    }

    test("v0.6 rejects fields it does not support") {
        shouldThrow<IllegalArgumentException> { op().paymasterAndData(EntryPointVersion.V0_6) }
        shouldThrow<IllegalArgumentException> { op().hash(EntryPoint.V0_6, 1) }
        shouldThrow<IllegalArgumentException> { op().toPacked(EntryPointVersion.V0_6) }
    }

    test("gas values that overflow 128 bits cannot be packed") {
        val tooLarge = bigIntegerOf(1).shiftLeft(128)
        shouldThrow<AbiCodecException> { op().copy(callGasLimit = tooLarge).toPacked(EntryPointVersion.V0_7) }
        shouldThrow<AbiCodecException> { op().copy(maxFeePerGas = tooLarge).hash(EntryPoint.V0_7, 1) }
        shouldThrow<AbiCodecException> { op(paymasterPostOpGasLimit = tooLarge).paymasterAndData(EntryPointVersion.V0_7) }
        // v0.6 does not pack gas values, so they may use all 256 bits
        op(null, null).copy(callGasLimit = tooLarge).hash(EntryPoint.V0_6, 1) shouldNotBe null
    }

    context("v0.9 paymaster signature") {
        val signature = Bytes("0x" + "ab".repeat(65))
        val signed = op(paymasterSignature = signature)

        test("is appended with its length and magic") {
            val paymasterAndData = signed.paymasterAndData(EntryPointVersion.V0_9).toString()
            paymasterAndData shouldEndWith "ab".repeat(65) + "0041" + "22e325a297439656"
        }

        test("round trips through the packed form") {
            UserOperation.fromPacked(signed.toPacked(EntryPointVersion.V0_9), EntryPointVersion.V0_9) shouldBe signed
        }

        test("is excluded from the hash, but its presence is not") {
            val otherSignature = signed.copy(paymasterSignature = Bytes("0x" + "cd".repeat(65)))
            signed.hash(EntryPoint.V0_9, 1) shouldBe otherSignature.hash(EntryPoint.V0_9, 1)
            signed.hash(EntryPoint.V0_9, 1) shouldNotBe op().hash(EntryPoint.V0_9, 1)
        }

        test("is not supported before v0.9") {
            shouldThrow<IllegalArgumentException> { signed.toPacked(EntryPointVersion.V0_8) }
            shouldThrow<IllegalArgumentException> { signed.toJson(EntryPointVersion.V0_8) }
        }

        test("is only parsed by v0.9") {
            val packed = signed.toPacked(EntryPointVersion.V0_9)
            val v08 = UserOperation.fromPacked(packed, EntryPointVersion.V0_8)
            v08.paymasterSignature shouldBe null
            v08.paymasterData shouldBe Bytes(packed.paymasterAndData.asByteArray().copyOfRange(52, packed.paymasterAndData.size))
        }

        test("with an invalid length is rejected") {
            val packed = signed.toPacked(EntryPointVersion.V0_9)
            val raw = packed.paymasterAndData.toByteArray()
            raw[raw.size - 10] = 0x7f
            shouldThrow<IllegalArgumentException> {
                UserOperation.fromPacked(packed.copy(paymasterAndData = Bytes(raw)), EntryPointVersion.V0_9)
            }
        }
    }

    context("EIP-7702") {
        val delegate = Address("0x4444444444444444444444444444444444444444")
        val eip7702 = op().copy(factory = UserOperation.EIP7702_FACTORY_MARKER, factoryData = Bytes.EMPTY)

        test("hash needs the delegate") {
            eip7702.isEip7702 shouldBe true
            shouldThrow<IllegalArgumentException> { eip7702.hash(EntryPoint.V0_8, 1) }
        }

        test("hash commits to the delegate, defaulting to the authorization's") {
            val authorization = Authorization(1, delegate, 0, 0, bigIntegerOf(1), bigIntegerOf(1))
            val withAuth = eip7702.copy(eip7702Auth = authorization)
            withAuth.hash(EntryPoint.V0_8, 1) shouldBe eip7702.hash(EntryPoint.V0_8, 1, delegate)
            eip7702.hash(EntryPoint.V0_8, 1, delegate) shouldNotBe eip7702.hash(EntryPoint.V0_8, 1, factory)
        }

        test("the marker is a regular factory before v0.8") {
            eip7702.copy(paymasterVerificationGasLimit = null).hash(EntryPoint.V0_7, 1) shouldNotBe null
        }

        test("a short zero-padded marker in packed initCode decodes as the marker") {
            val packed = eip7702.toPacked(EntryPointVersion.V0_8).copy(initCode = Bytes("0x7702"))
            val decoded = UserOperation.fromPacked(packed, EntryPointVersion.V0_8)
            decoded.factory shouldBe UserOperation.EIP7702_FACTORY_MARKER
            decoded.hash(EntryPoint.V0_8, 1, delegate) shouldBe eip7702.hash(EntryPoint.V0_8, 1, delegate)
        }

        test("authorization is sent as eip7702Auth") {
            val authorization = Authorization(1, delegate, 5, 1, bigIntegerOf(2), bigIntegerOf(3))
            val json = eip7702.copy(eip7702Auth = authorization).toJson(EntryPointVersion.V0_8)
            val auth = json["eip7702Auth"].shouldBeInstanceOf<JsonObject>()
            auth["address"] shouldBe JsonPrimitive(delegate.toString())
            auth["nonce"] shouldBe JsonPrimitive("0x5")
            auth["yParity"] shouldBe JsonPrimitive("0x1")
            // encoded like the authorization list of a transaction
            auth shouldBe Kotlinx.DEFAULT.encodeToJsonElement(Authorization.serializer(), authorization)
            UserOperation.fromJson(json).eip7702Auth shouldBe authorization

            shouldThrow<IllegalArgumentException> {
                eip7702.copy(eip7702Auth = authorization).toJson(EntryPointVersion.V0_7)
            }
        }
    }

    context("JSON-RPC layout") {
        test("v0.6 uses initCode and paymasterAndData") {
            val json = op(null, null).toJson(EntryPointVersion.V0_6)
            json.keys shouldBe setOf(
                "sender", "nonce", "initCode", "callData", "callGasLimit", "verificationGasLimit", "preVerificationGas",
                "maxFeePerGas", "maxPriorityFeePerGas", "paymasterAndData", "signature",
            )
            json["nonce"] shouldBe JsonPrimitive("0x70000000000000003")
            UserOperation.fromJson(json) shouldBe op(null, null)
        }

        test("v0.7 uses factory and paymaster fields") {
            val json = op().toJson(EntryPointVersion.V0_7)
            json.keys shouldBe setOf(
                "sender", "nonce", "factory", "factoryData", "callData", "callGasLimit", "verificationGasLimit",
                "preVerificationGas", "maxFeePerGas", "maxPriorityFeePerGas", "paymaster", "paymasterVerificationGasLimit",
                "paymasterPostOpGasLimit", "paymasterData", "signature",
            )
            UserOperation.fromJson(json) shouldBe op()
        }

        test("v0.7 leaves out factory and paymaster fields when unset") {
            val json = op().copy(factory = null, factoryData = null, paymaster = null, paymasterData = null).toJson(EntryPointVersion.V0_7)
            json.keys.any { it.startsWith("factory") || it.startsWith("paymaster") } shouldBe false
        }

        test("quantities may be JSON numbers or decimal strings") {
            val json = JsonObject(
                op().toJson(EntryPointVersion.V0_7) + mapOf(
                    "callGasLimit" to JsonPrimitive(100000),
                    "preVerificationGas" to JsonPrimitive("50000"),
                ),
            )
            UserOperation.fromJson(json) shouldBe op()
        }

        test("an EntryPoint version overrides layout detection") {
            val json = JsonObject(op().toJson(EntryPointVersion.V0_7) + mapOf("initCode" to JsonPrimitive("0x")))
            UserOperation.fromJson(json, EntryPointVersion.V0_7) shouldBe op()
            shouldThrow<IllegalArgumentException> { UserOperation.fromJson(JsonPrimitive("0x")) }
        }

        test("missing required field is rejected") {
            val json = JsonObject(op().toJson(EntryPointVersion.V0_7) - "callData")
            shouldThrow<IllegalArgumentException> { UserOperation.fromJson(json) }
        }
    }

    test("EntryPoint lookup") {
        EntryPoint.canonical(EntryPoint.V0_7.address) shouldBe EntryPoint.V0_7
        EntryPoint.canonical(Address.ZERO) shouldBe null
        EntryPoint.canonical(EntryPointVersion.V0_9) shouldBe EntryPoint.V0_9
        EntryPointVersion.fromLabel("0.8") shouldBe EntryPointVersion.V0_8
        EntryPointVersion.fromLabel("0.5") shouldBe null
    }
})
