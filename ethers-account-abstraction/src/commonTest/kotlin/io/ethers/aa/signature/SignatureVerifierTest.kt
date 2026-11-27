package io.ethers.aa.signature

import io.ethers.aa.FakeJsonRpcClient
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.crypto.Hashing
import io.ethers.providers.Provider
import io.ethers.providers.RpcError
import io.ethers.signers.PrivateKeySigner
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SignatureVerifierTest : FunSpec({
    val owner = PrivateKeySigner("0x" + "07".repeat(32))
    val account = Address("0x1111111111111111111111111111111111111111")
    val factory = Address("0x2222222222222222222222222222222222222222")
    val factoryData = Bytes("0xabcdef")
    val hash = Hash(Hashing.keccak256("hello".encodeToByteArray()))
    val eoaSignature = Bytes(owner.signHash(hash.asByteArray()).toByteArray())
    val accountSignature = Bytes("0x" + "aa".repeat(70))

    fun verifier(node: FakeJsonRpcClient) = SignatureVerifier(Provider(node, 1))

    fun callData(node: FakeJsonRpcClient): String {
        val call = node.requests("eth_call").single().params[0].jsonObject
        call["to"].shouldBeNull()
        return (call["data"] ?: call["input"])!!.jsonPrimitive.content
    }

    test("EOA signature is verified locally") {
        val node = FakeJsonRpcClient().respond("eth_getCode", "\"0x\"")
        verifier(node).verifyHash(owner.address, hash, eoaSignature).send().unwrap() shouldBe true
        node.requests("eth_call").shouldBeEmpty()
    }

    test("signature of an EIP-7702 delegated EOA is left to its delegate") {
        val node = FakeJsonRpcClient()
            .respond("eth_getCode", "\"0xef0100${"11".repeat(20)}\"")
            .respond("eth_call", "\"0x00\"")
        verifier(node).verifyHash(owner.address, hash, eoaSignature).send().unwrap() shouldBe false
        node.requests("eth_call").size shouldBe 1
    }

    test("EIP-191 message signature") {
        val node = FakeJsonRpcClient().respond("eth_getCode", "\"0x\"")
        val message = "hi".encodeToByteArray()
        val signature = Bytes(owner.signMessage(message).toByteArray())
        verifier(node).verifyMessage(owner.address, message, signature).send().unwrap() shouldBe true
    }

    test("smart account signature is verified by the deployless ERC-6492 validator") {
        val node = FakeJsonRpcClient().respond("eth_call", "\"0x01\"")
        verifier(node).verifyHash(account, hash, accountSignature).send().unwrap() shouldBe true

        val data = callData(node)
        data shouldStartWith ERC6492_VALIDATOR_BYTECODE.toString()
        data shouldEndWith "aa".repeat(70) + "0".repeat(52)
    }

    test("invalid signature or reverting validation is not valid") {
        verifier(FakeJsonRpcClient().respond("eth_call", "\"0x00\"")).verifyHash(account, hash, accountSignature).send().unwrap() shouldBe false
        verifier(FakeJsonRpcClient().respond("eth_call", "\"0x\"")).verifyHash(account, hash, accountSignature).send().unwrap() shouldBe false
        verifier(FakeJsonRpcClient().fail("eth_call", RpcError(3, "execution reverted")))
            .verifyHash(account, hash, accountSignature).send().unwrap() shouldBe false
    }

    test("other RPC errors are reported") {
        val error = verifier(FakeJsonRpcClient().fail("eth_call", RpcError(-32005, "rate limited")))
            .verifyHash(account, hash, accountSignature).send().unwrapError()
        error.code shouldBe -32005
    }

    test("EOA signature of another signer falls through to the validator") {
        val node = FakeJsonRpcClient().respond("eth_call", "\"0x00\"")
        verifier(node).verifyHash(account, hash, eoaSignature).send().unwrap() shouldBe false
        node.requests("eth_call").size shouldBe 1
    }

    test("signature of an undeployed account is wrapped with its factory") {
        val node = FakeJsonRpcClient().respond("eth_call", "\"0x01\"")
        verifier(node).verifyHash(account, hash, accountSignature, factory, factoryData).send().unwrap() shouldBe true
        callData(node) shouldEndWith "6492".repeat(16)
    }

    test("no signature is valid for the zero address") {
        val node = FakeJsonRpcClient()
        verifier(node).verifyHash(Address.ZERO, hash, eoaSignature).send().unwrap() shouldBe false
        node.requests.shouldBeEmpty()
    }

    context("ERC-6492 wrapping") {
        test("round trip") {
            val wrapped = Erc6492Signature.wrap(accountSignature, factory, factoryData)
            Erc6492Signature.isWrapped(wrapped) shouldBe true
            wrapped.toString() shouldEndWith "6492".repeat(16)
            Erc6492Signature.parse(wrapped) shouldBe Erc6492Signature(factory, factoryData, accountSignature)
        }

        test("wrapping is idempotent") {
            val wrapped = Erc6492Signature.wrap(accountSignature, factory, factoryData)
            Erc6492Signature.wrap(wrapped, factory, factoryData) shouldBe wrapped
        }

        test("plain or malformed signatures do not parse") {
            Erc6492Signature.isWrapped(accountSignature) shouldBe false
            Erc6492Signature.parse(accountSignature).shouldBeNull()
            Erc6492Signature.parse(Bytes("0x" + "6492".repeat(16))).shouldBeNull()
        }
    }
})
