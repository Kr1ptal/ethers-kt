package io.ethers.solana

import io.ethers.solana.instruction.AddressLookupTableProgram
import io.ethers.solana.instruction.AssociatedTokenProgram
import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Ed25519Program
import io.ethers.solana.instruction.MemoProgram
import io.ethers.solana.instruction.Secp256k1Program
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.instruction.Token2022Program
import io.ethers.solana.instruction.TokenProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.DataSlice
import io.ethers.solana.types.rpc.SolanaSendConfig
import io.ethers.solana.types.rpc.SolanaSimulationConfig
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.ethers.solana.utils.SolUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.lang.reflect.Executable

/**
 * The `@Throws` annotations added for Swift also reach the JVM signatures. These pin them to unchecked exceptions,
 * so Java callers are never made to handle a checked one.
 */
class JavaSignatureTest : FunSpec({
    val classes = listOf(
        AddressLookupTableProgram::class, AssociatedTokenProgram::class, ComputeBudgetProgram::class, Ed25519Program::class,
        Secp256k1Program::class, SystemProgram::class, TokenProgram::class, Token2022Program::class, KeypairSigner::class,
        KeypairSigner.Companion::class, SolanaSigner::class, SolanaAddress::class, SolanaAddress.Companion::class,
        SolanaSignature::class, SolanaBlockhash::class, SolanaBytes::class, SolanaBytes.Companion::class,
        AddressLookupTableAccount::class, SolanaTransaction::class, SolanaTransactionConfig::class,
        SolanaTransactionSigned::class, SolanaTransactionSigned.Builder::class, SolanaTransactionUnsigned::class,
        SolanaTxLegacy::class, SolanaTxV0::class, SolanaTxV1::class, SolanaTxType.Companion::class,
        SolanaTxType.Unsupported::class, MemoProgram::class, SolanaTransactionRequest::class, DataSlice::class,
        SolanaSendConfig::class, SolanaSimulationConfig::class, SolUnit::class,
    ).map { it.java }

    fun Class<*>.executables(): List<Executable> = declaredMethods.toList() + declaredConstructors

    test("annotated declarations declare their exceptions") {
        classes.flatMap { it.executables() }.filter { it.exceptionTypes.isNotEmpty() }.shouldNotBeEmpty()
    }

    test("an unsupported transaction type has no public constructor, so it comes only from fromVersion") {
        // the synthetic accessor the companion uses is not callable from Java source
        SolanaTxType.Unsupported::class.java.constructors.filterNot { it.isSynthetic }.shouldBeEmpty()
    }

    test("no public declaration declares a checked exception") {
        val checked = classes.flatMap { it.executables() }.flatMap { executable ->
            executable.exceptionTypes
                .filterNot { RuntimeException::class.java.isAssignableFrom(it) || Error::class.java.isAssignableFrom(it) }
                .map { "$executable throws ${it.name}" }
        }
        checked.shouldBeEmpty()
    }
})
