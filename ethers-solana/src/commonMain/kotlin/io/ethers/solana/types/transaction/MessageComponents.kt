@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.U8ListSerializer
import io.ethers.solana.types.U8Serializer
import io.ethers.solana.utils.U64_MAX
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The three counts that split a message's accounts into signers and readonly accounts.
 *
 * Unknown JSON fields are not retained, unlike the RPC response types: this mirrors three bytes of the
 * message binary format, which cannot gain a field without changing that format.
 */
@Serializable
data class MessageHeader(
    @SerialName("numRequiredSignatures") @Serializable(with = U8Serializer::class) val requiredSignatures: Int,
    @SerialName("numReadonlySignedAccounts") @Serializable(with = U8Serializer::class) val readonlySignedAccounts: Int,
    @SerialName("numReadonlyUnsignedAccounts") @Serializable(with = U8Serializer::class) val readonlyUnsignedAccounts: Int,
)

/**
 * An instruction inside a compiled message, naming its program and accounts by index into the
 * message's account list rather than by address.
 *
 * Implemented by [MessageInstruction] for messages this library compiled and by SolanaRPCInstruction
 * for a node's response, which carries execution details on top. Code that reads either takes this.
 */
interface CompiledInstruction {
    /** Index into the message's account list naming the program this instruction invokes. */
    val programIdIndex: Int

    /** Indices into the message's account list, inline accounts first and loaded ones after. */
    val accounts: List<Int>

    val data: SolanaBytes
}

/**
 * A [CompiledInstruction] in a message this library compiled. [accounts] is not copied; pass an
 * immutable list.
 */
data class MessageInstruction(override val programIdIndex: Int, override val accounts: List<Int>, override val data: SolanaBytes) : CompiledInstruction {
    /** Takes ownership of [data] rather than copying it, so do not mutate the array afterwards. */
    constructor(programIdIndex: Int, accounts: List<Int>, data: ByteArray) :
        this(programIdIndex, accounts, SolanaBytes.fromBytes(data))
}

/** Everything [compileMessage] derives from a request: a whole message, ready for a version to encode. */
internal class CompiledMessageFields(
    val header: MessageHeader,
    val accounts: List<SolanaAddress>,
    val recentBlockhash: SolanaBlockhash,
    val instructions: List<MessageInstruction>,
    val lookups: List<CompiledAddressLookupTable>,
)
