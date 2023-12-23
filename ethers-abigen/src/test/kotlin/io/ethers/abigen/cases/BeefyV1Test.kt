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
            clazz.getAbiFunctionField("FUNCTION_VERIFY_CONSENSUS") shouldBe AbiFunction(
                name = "VerifyConsensus",
                inputs = listOf(
                    AbiType.Address,
                    AbiType.Tuple.struct(
                        clazz.typedNestedClass("BeefyConsensusState"),
                        AbiType.UInt(256),
                        AbiType.UInt(256),
                        AbiType.UInt(256),
                        AbiType.FixedBytes(32),
                        AbiType.UInt(256),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("AuthoritySetCommitment"),
                            AbiType.UInt(256),
                            AbiType.UInt(256),
                            AbiType.FixedBytes(32),
                        ),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("AuthoritySetCommitment"),
                            AbiType.UInt(256),
                            AbiType.UInt(256),
                            AbiType.FixedBytes(32),
                        ),
                    ),
                    AbiType.Tuple.struct(
                        clazz.typedNestedClass("BeefyConsensusProof"),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("SignedCommitment"),
                            AbiType.Tuple.struct(
                                clazz.typedNestedClass("Commitment"),
                                AbiType.Array(
                                    AbiType.Tuple.struct(
                                        clazz.typedNestedClass("Payload"),
                                        AbiType.FixedBytes(2),
                                        AbiType.Bytes,
                                    ),
                                ),
                                AbiType.UInt(256),
                                AbiType.UInt(256),
                            ),
                            AbiType.Array(
                                AbiType.Tuple.struct(
                                    clazz.typedNestedClass("Signature"),
                                    AbiType.Bytes,
                                    AbiType.UInt(256),
                                ),
                            ),
                        ),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("BeefyMmrLeaf"),
                            AbiType.UInt(256),
                            AbiType.UInt(256),
                            AbiType.FixedBytes(32),
                            AbiType.Tuple.struct(
                                clazz.typedNestedClass("AuthoritySetCommitment"),
                                AbiType.UInt(256),
                                AbiType.UInt(256),
                                AbiType.FixedBytes(32),
                            ),
                            AbiType.FixedBytes(32),
                            AbiType.UInt(256),
                        ),
                        AbiType.Array(AbiType.FixedBytes(32)),
                        AbiType.Array(
                            AbiType.Array(
                                AbiType.Tuple.struct(
                                    clazz.typedNestedClass("ProofNode"),
                                    AbiType.UInt(256),
                                    AbiType.FixedBytes(32),
                                ),
                            ),
                        ),
                        AbiType.Bytes, AbiType.UInt(256),
                        AbiType.Array(AbiType.Bytes),
                        AbiType.Bytes,
                    ),
                ),
                outputs = listOf(
                    AbiType.Tuple.struct(
                        clazz.typedNestedClass("BeefyConsensusState"),
                        AbiType.UInt(256),
                        AbiType.UInt(256),
                        AbiType.UInt(256),
                        AbiType.FixedBytes(32),
                        AbiType.UInt(256),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("AuthoritySetCommitment"),
                            AbiType.UInt(256),
                            AbiType.UInt(256),
                            AbiType.FixedBytes(32),
                        ),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("AuthoritySetCommitment"),
                            AbiType.UInt(256),
                            AbiType.UInt(256),
                            AbiType.FixedBytes(32),
                        ),
                    ),
                    AbiType.Tuple.struct(
                        clazz.typedNestedClass("IntermediateState"),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("StateMachineHeight"),
                            AbiType.UInt(256),
                            AbiType.UInt(256),
                        ),
                        AbiType.Tuple.struct(
                            clazz.typedNestedClass("StateCommitment"),
                            AbiType.UInt(256),
                            AbiType.FixedBytes(32),
                        ),
                    ),
                ),
            )
        }
    }
})
