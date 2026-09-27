package io.ethers.solana.idl.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

/** Runtime types referenced by generated code, named rather than linked so the generator has no runtime dependency. */
internal object Runtime {
    private const val TYPES = "io.ethers.solana.types"
    private const val CODEC = "io.ethers.solana.codec"

    val SOLANA_ADDRESS = ClassName(TYPES, "SolanaAddress")
    val SOLANA_BYTES = ClassName(TYPES, "SolanaBytes")
    val ACCOUNT_META = ClassName(TYPES, "AccountMeta")
    val INSTRUCTION = ClassName("io.ethers.solana.instruction", "Instruction")

    val BORSH_READER = ClassName(CODEC, "BorshReader")
    val BORSH_WRITER = ClassName(CODEC, "BorshWriter")
    val SOLANA_CODEC = ClassName(CODEC, "SolanaCodec")
    val SOLANA_OPTION = ClassName(CODEC, "SolanaOption")
    val DISCRIMINATOR = ClassName(CODEC, "SolanaDiscriminator")
    val EVENT_FILTER = ClassName("io.ethers.solana.program", "SolanaEventFilter")
    val PROGRAM_FAILURE = ClassName("io.ethers.solana.program", "SolanaProgramFailure")
    val ACCOUNT_FACTORY = ClassName(CODEC, "SolanaAccountFactory")
    val INSTRUCTION_DESCRIPTOR = ClassName(CODEC, "SolanaInstructionDescriptor")
    val RETURNING_DESCRIPTOR = INSTRUCTION_DESCRIPTOR.nestedClass("Returning")
    val ACCOUNTS_READER = ClassName(CODEC, "SolanaAccountsReader")
    val ACCOUNT_FILTER_BASE = ClassName("io.ethers.solana.program", "SolanaAccountFilterBase")
    val MAYBE_SOLANA_ACCOUNT = ClassName("io.ethers.solana.program", "MaybeSolanaAccount")
    val SOLANA_ACCOUNT_ERROR = ClassName("io.ethers.solana.program", "SolanaAccountError")
    val FETCH = MemberName("io.ethers.solana.program", "fetch")
    val WATCH = MemberName("io.ethers.solana.program", "watch")
    val SOLANA_API = ClassName("io.ethers.solana.providers.middleware", "SolanaApi")
    val COMMITMENT = ClassName("io.ethers.solana.types.rpc", "Commitment")
    val RPC_REQUEST = ClassName("io.ethers.providers.types", "RpcRequest")
    val RPC_SUBSCRIBE = ClassName("io.ethers.providers.types", "RpcSubscribe")
    val RPC_ERROR = ClassName("io.ethers.providers", "RpcError")
    val EVENT = ClassName(CODEC, "SolanaEvent")
    val EVENT_FACTORY = ClassName(CODEC, "SolanaEventFactory")
    val EVENT_CONTEXT = ClassName(CODEC, "SolanaEventContext")
    val PROGRAM_ERROR = ClassName(CODEC, "SolanaProgramError")
    val PROGRAM_ERROR_DECODER = ClassName(CODEC, "SolanaProgramErrorDecoder")
    val DECODE_ERROR = ClassName(CODEC, "SolanaDecodeError")
    val UNRECOGNIZED = DECODE_ERROR.nestedClass("Unrecognized")

    val RESULT = ClassName("io.ethers.core", "Result")
    val FAILURE = MemberName("io.ethers.core", "failure")
    val BIG_INTEGER = ClassName("io.github.artificialpb.bignum", "BigInteger")

    val JVM_FIELD = ClassName("kotlin.jvm", "JvmField")
    val JVM_STATIC = ClassName("kotlin.jvm", "JvmStatic")
    val JVM_OVERLOADS = ClassName("kotlin.jvm", "JvmOverloads")
}

private val WORD_SEPARATOR = Regex("[^A-Za-z0-9]+")
private val CAMEL_HUMP = Regex("([a-z0-9])([A-Z])")

/** `snake_case`, `kebab-case`, `module::Path` or `camelCase` to `PascalCase`. */
internal fun String.toPascalCase(): String = split(WORD_SEPARATOR)
    .filter(String::isNotEmpty)
    .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

internal fun String.toCamelCase(): String = toPascalCase().replaceFirstChar(Char::lowercaseChar)

internal fun String.toUpperSnakeCase(): String = replace(CAMEL_HUMP, "$1_$2")
    .split(WORD_SEPARATOR)
    .filter(String::isNotEmpty)
    .joinToString("_")
    .uppercase()
