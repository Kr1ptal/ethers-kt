package io.ethers.abigen.cases

import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.StructFactory
import io.ethers.abi.call.FunctionCall
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.ArgDescriptor
import io.ethers.abigen.ClassDescriptor
import io.ethers.abigen.FunctionDescriptor
import io.ethers.abigen.getAbiFunctionField
import io.ethers.abigen.getDeclaredFunctions
import io.ethers.abigen.getDeclaredStructs
import io.ethers.abigen.nestedClass
import io.ethers.abigen.parametrizedBy
import io.ethers.abigen.typedNestedClass
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSubclassOf

class BeefyV1Test : FunSpec({
    context("abigen validation: BeefyV1") {
        val clazz = AbigenCompiler.getContract("BeefyV1")

        test("class name") {
            clazz.simpleName shouldBe "BeefyV1"
        }

        test("functions") {
            clazz.getDeclaredFunctions() shouldContainAll listOf(
                FunctionDescriptor(
                    "VerifyConsensus",
                    listOf(
                        ArgDescriptor("host", Address::class),
                        ArgDescriptor("trustedState", clazz.nestedClass("BeefyConsensusState")),
                        ArgDescriptor("proof", clazz.nestedClass("BeefyConsensusProof")),
                    ),
                    FunctionCall::class.parametrizedBy(clazz.nestedClass("VerifyConsensusResult")),
                ),
            )
        }

        test("structs") {
            val declared = clazz.getDeclaredStructs()

            declared.descriptors shouldContainAll listOf(
                ClassDescriptor(
                    "AuthoritySetCommitment",
                    listOf(
                        ArgDescriptor("id", BigInteger::class),
                        ArgDescriptor("len", BigInteger::class),
                        ArgDescriptor("root", Bytes::class),
                    ),
                ),
                ClassDescriptor(
                    "IntermediateState",
                    listOf(
                        ArgDescriptor("height", clazz.nestedClass("StateMachineHeight")),
                        ArgDescriptor("commitment", clazz.nestedClass("StateCommitment")),
                    ),
                ),
                ClassDescriptor(
                    "BeefyConsensusState",
                    listOf(
                        ArgDescriptor("latestHeight", BigInteger::class),
                        ArgDescriptor("latestTimestamp", BigInteger::class),
                        ArgDescriptor("frozenHeight", BigInteger::class),
                        ArgDescriptor("latestHeadsRoot", Bytes::class),
                        ArgDescriptor("beefyActivationBlock", BigInteger::class),
                        ArgDescriptor("currentAuthoritySet", clazz.nestedClass("AuthoritySetCommitment")),
                        ArgDescriptor("nextAuthoritySet", clazz.nestedClass("AuthoritySetCommitment")),
                    ),
                ),
            )

            declared.classes.all {
                it.companionObject != null && it.companionObject!!.isSubclassOf(StructFactory::class)
            } shouldBe true
        }

        test("AbiFunction field") {
            val authoritySetCommitment = AbiType.Struct(
                clazz.typedNestedClass("AuthoritySetCommitment"),
                AbiType.Struct.Field("id", AbiType.UInt(256)),
                AbiType.Struct.Field("len", AbiType.UInt(256)),
                AbiType.Struct.Field("root", AbiType.FixedBytes(32)),
            )

            val beefyConsensusState = AbiType.Struct(
                clazz.typedNestedClass("BeefyConsensusState"),
                AbiType.Struct.Field("latestHeight", AbiType.UInt(256)),
                AbiType.Struct.Field("latestTimestamp", AbiType.UInt(256)),
                AbiType.Struct.Field("frozenHeight", AbiType.UInt(256)),
                AbiType.Struct.Field("latestHeadsRoot", AbiType.FixedBytes(32)),
                AbiType.Struct.Field("beefyActivationBlock", AbiType.UInt(256)),
                AbiType.Struct.Field("currentAuthoritySet", authoritySetCommitment),
                AbiType.Struct.Field("nextAuthoritySet", authoritySetCommitment),
            )

            val payload = AbiType.Struct(
                clazz.typedNestedClass("Payload"),
                AbiType.Struct.Field("id", AbiType.FixedBytes(2)),
                AbiType.Struct.Field("data", AbiType.Bytes),
            )

            val commitment = AbiType.Struct(
                clazz.typedNestedClass("Commitment"),
                AbiType.Struct.Field("payload", AbiType.Array(payload)),
                AbiType.Struct.Field("blockNumber", AbiType.UInt(256)),
                AbiType.Struct.Field("validatorSetId", AbiType.UInt(256)),
            )

            val signature = AbiType.Struct(
                clazz.typedNestedClass("Signature"),
                AbiType.Struct.Field("signature", AbiType.Bytes),
                AbiType.Struct.Field("authorityIndex", AbiType.UInt(256)),
            )

            val signedCommitment = AbiType.Struct(
                clazz.typedNestedClass("SignedCommitment"),
                AbiType.Struct.Field("commitment", commitment),
                AbiType.Struct.Field("signatures", AbiType.Array(signature)),
            )

            val proofNode = AbiType.Struct(
                clazz.typedNestedClass("ProofNode"),
                AbiType.Struct.Field("k_index", AbiType.UInt(256)),
                AbiType.Struct.Field("node", AbiType.FixedBytes(32)),
            )

            val beefyMmrLeaf = AbiType.Struct(
                clazz.typedNestedClass("BeefyMmrLeaf"),
                AbiType.Struct.Field("version", AbiType.UInt(256)),
                AbiType.Struct.Field("parentNumber", AbiType.UInt(256)),
                AbiType.Struct.Field("parentHash", AbiType.FixedBytes(32)),
                AbiType.Struct.Field("nextAuthoritySet", authoritySetCommitment),
                AbiType.Struct.Field("extra", AbiType.FixedBytes(32)),
                AbiType.Struct.Field("kIndex", AbiType.UInt(256)),
            )

            val beefyConsensusProof = AbiType.Struct(
                clazz.typedNestedClass("BeefyConsensusProof"),
                AbiType.Struct.Field("signedCommitment", signedCommitment),
                AbiType.Struct.Field("latestMmrLeaf", beefyMmrLeaf),
                AbiType.Struct.Field("mmrProof", AbiType.Array(AbiType.FixedBytes(32))),
                AbiType.Struct.Field("authoritiesProof", AbiType.Array(AbiType.Array(proofNode))),
                AbiType.Struct.Field("header", AbiType.Bytes),
                AbiType.Struct.Field("headsIndex", AbiType.UInt(256)),
                AbiType.Struct.Field("extrinsicProof", AbiType.Array(AbiType.Bytes)),
                AbiType.Struct.Field("timestampExtrinsic", AbiType.Bytes),
            )

            val stateCommitment = AbiType.Struct(
                clazz.typedNestedClass("StateCommitment"),
                AbiType.Struct.Field("timestamp", AbiType.UInt(256)),
                AbiType.Struct.Field("commitment", AbiType.FixedBytes(32)),
            )

            val stateMachineHeight = AbiType.Struct(
                clazz.typedNestedClass("StateMachineHeight"),
                AbiType.Struct.Field("stateMachineId", AbiType.UInt(256)),
                AbiType.Struct.Field("height", AbiType.UInt(256)),
            )

            val intermediateState = AbiType.Struct(
                clazz.typedNestedClass("IntermediateState"),
                AbiType.Struct.Field("height", stateMachineHeight),
                AbiType.Struct.Field("commitment", stateCommitment),
            )

            clazz.getAbiFunctionField("FUNCTION_VERIFY_CONSENSUS") shouldBe AbiFunction(
                name = "VerifyConsensus",
                inputs = listOf(
                    AbiType.Address,
                    beefyConsensusState,
                    beefyConsensusProof,
                ),
                outputs = listOf(
                    beefyConsensusState,
                    intermediateState,
                ),
            )
        }
    }
})
